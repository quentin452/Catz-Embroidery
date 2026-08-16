//! Brother PES writer + reader.
//!
//! **Writer:** ported from `PEmbroiderWriter.PES` (v1 header, `VERSION == 1`,
//! the Java's default). Byte-identical output.
//!
//! **Reader:** NOT a port. The Java reader (`PEmbroiderReader.PES.read`) is
//! fundamentally misaligned — measured with `tools/java-fixtures/PesProbe.java`:
//! it reads the 4-byte PEC offset from the wrong place (the v1 header), the
//! resulting `skipBytes` is negative and skips nothing, and it decodes the
//! CEmbOne/CSewSeg text and the PEC icon graphics as a stitch stream. It cannot
//! read the files its own writer produces. The Rust reader parses the real PES v1
//! structure instead (docs/LESSONS.md records the measurement).

use crate::{Design, Error, Point, java_round, rint};

const PEC_ICON_WIDTH: usize = 48;
const PEC_ICON_HEIGHT: usize = 38;

const STITCH: i32 = 0;
const END: i32 = 4;
const COLOR_CHANGE: i32 = 5;

/// The standard 64-colour PEC palette (the Java's `find_color` table).
const STD_PALETTE: [u32; 64] = [
    0x1a0a94, 0x0f75ff, 0x00934c, 0xbabdfe, 0xec0000, 0xe4995a, 0xcc48ab, 0xfdc4fa, 0xdd84cd,
    0x6bd38a, 0xe4a945, 0xffbd42, 0xffe600, 0x6cd900, 0xc1a941, 0xb5ad97, 0xba9c5f, 0xfaf59e,
    0x808080, 0x000000, 0x001cdf, 0xdf00b8, 0x626262, 0x69260d, 0xff0060, 0xbf8200, 0xf39178,
    0xff6805, 0xf0f0f0, 0xc832cd, 0xb0bf9b, 0x65bfeb, 0xffba04, 0xfff06c, 0xfeca15, 0xf38101,
    0x37a923, 0x23465f, 0xa6a695, 0xcebfa6, 0x96aa02, 0xffe3c6, 0xff99d7, 0x007004, 0xedccfb,
    0xc089d8, 0xe7d9b4, 0xe90e86, 0xcf6829, 0x408615, 0xdb1797, 0xffa704, 0xb9ffff, 0x228927,
    0xb612cd, 0x00aa00, 0xfea9dc, 0xfed510, 0x0097df, 0xffff84, 0xcfe774, 0xffc864, 0xffc8c8,
    0xffc8c8,
];

/// The Java `find_color`: the nearest palette entry by squared distance, returned
/// 1-based (index + 1, the PEC palette encoding).
pub fn find_color(rgb: u32) -> u8 {
    let r = ((rgb >> 16) & 0xFF) as f32;
    let g = ((rgb >> 8) & 0xFF) as f32;
    let b = (rgb & 0xFF) as f32;
    let mut best = 195075.0f32;
    let mut best_i = 0usize;
    for (i, c) in STD_PALETTE.iter().enumerate() {
        let r0 = ((c >> 16) & 0xFF) as f32;
        let g0 = ((c >> 8) & 0xFF) as f32;
        let b0 = (c & 0xFF) as f32;
        let d = (r - r0).powi(2) + (g - g0).powi(2) + (b - b0).powi(2);
        if d < best {
            best = d;
            best_i = i;
        }
    }
    (best_i + 1) as u8
}

/// The palette RGB a PEC index (1-based) refers to.
pub fn palette_rgb(index: u8) -> u32 {
    STD_PALETTE[(index as usize - 1) % STD_PALETTE.len()]
}

// ---------------------------------------------------------------- writer

struct BinWriter {
    out: Vec<u8>,
}

impl BinWriter {
    fn new() -> Self {
        Self { out: Vec::new() }
    }
    fn u8(&mut self, v: i32) {
        self.out.push(v as u8);
    }
    fn u16le(&mut self, v: i32) {
        self.out.extend_from_slice(&(v as u16).to_le_bytes());
    }
    fn u16be(&mut self, v: i32) {
        self.out.extend_from_slice(&(v as u16).to_be_bytes());
    }
    fn u32le(&mut self, v: i32) {
        self.out.extend_from_slice(&(v as u32).to_le_bytes());
    }
    fn f32le(&mut self, v: f32) {
        self.out.extend_from_slice(&v.to_bits().to_le_bytes());
    }
    fn bytes(&mut self, b: &[u8]) {
        self.out.extend_from_slice(b);
    }
    /// The Java `writePesString16`: u16 length prefix, then the bytes.
    fn str16(&mut self, s: &str) {
        self.u16le(s.len() as i32);
        self.bytes(s.as_bytes());
    }
    /// Writes `len` zero bytes and returns their position, to be patched later
    /// (the Java `space_holder` + `writeSpaceHolder*LE` mechanics).
    fn placeholder(&mut self, len: usize) -> usize {
        let pos = self.out.len();
        self.out.resize(pos + len, 0);
        pos
    }
    fn patch(&mut self, pos: usize, len: usize, value: usize) {
        assert!(pos + len <= self.out.len());
        for i in 0..len {
            self.out[pos + i] = ((value >> (8 * i)) & 0xFF) as u8;
        }
    }
}

fn encode_long_form(value: i32) -> i32 {
    (value & 0x0FFF) | 0x8000
}

fn flag_trim(long_form: i32) -> i32 {
    long_form | 0x2000
}

/// The Java `_BinWriter.write_pec_header`: the 512-byte PEC header.
/// Returns the deduplicated palette (RGBs in encounter order).
fn write_pec_header(w: &mut BinWriter, design: &Design) -> Vec<u32> {
    w.bytes(format!("LA:{:<16}\r", design.title).as_bytes());
    for _ in 0..12 {
        w.u8(0x20);
    }
    w.u8(0xFF);
    w.u8(0x00);
    w.u8((PEC_ICON_WIDTH / 8) as i32);
    w.u8(PEC_ICON_HEIGHT as i32);

    let mut palette = Vec::new();
    for (i, c) in design.colors.iter().enumerate() {
        if i == 0 || design.colors[i] != design.colors[i - 1] {
            palette.push(*c);
        }
    }
    for _ in 0..12 {
        w.u8(0x20);
    }
    w.u8(palette.len() as i32 - 1);
    for c in &palette {
        w.u8(i32::from(find_color(*c)));
    }
    for _ in 0..(463 - palette.len()) {
        w.u8(0x20);
    }
    palette
}

/// The Java `_BinWriter.write_pec_block`: the stitch block. `width`/`height` are
/// the rounded bounds extents; the 2×u16BE are the (ignored) offset words.
fn write_pec_block(w: &mut BinWriter, design: &Design) -> Result<(), Error> {
    let width = rint(f64::from(design.bounds[2] - design.bounds[0]));
    let height = rint(f64::from(design.bounds[3] - design.bounds[1]));
    let block_start = w.out.len();
    w.u8(0x00);
    w.u8(0x00);
    let len_placeholder = w.placeholder(3);
    w.u8(0x31);
    w.u8(0xFF);
    w.u8(0xF0);
    w.u16le(width);
    w.u16le(height);
    w.u16le(0x1E0);
    w.u16le(0x1B0);
    w.u16be(0x9000 | -java_round(design.bounds[0]));
    w.u16be(0x9000 | -java_round(design.bounds[1]));
    pec_encode(w, design)?;
    let block_len = w.out.len() - block_start;
    w.patch(len_placeholder, 3, block_len);
    Ok(())
}

/// The Java `_BinWriter.write_pec_graphics`: the needle-plate icon, 38 rows of
/// 6 bytes. 30 rows are the `02 00 00 00 00 40` body (counted from the source).
fn write_pec_graphics(w: &mut BinWriter) {
    w.bytes(&[0; 6]);
    w.bytes(&[0xF0, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F]);
    w.bytes(&[0x08, 0x00, 0x00, 0x00, 0x00, 0x10]);
    w.bytes(&[0x04, 0x00, 0x00, 0x00, 0x00, 0x20]);
    for _ in 0..30 {
        w.bytes(&[0x02, 0x00, 0x00, 0x00, 0x00, 0x40]);
    }
    w.bytes(&[0x04, 0x00, 0x00, 0x00, 0x00, 0x20]);
    w.bytes(&[0x08, 0x00, 0x00, 0x00, 0x00, 0x10]);
    w.bytes(&[0xF0, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F]);
    w.bytes(&[0; 6]);
}

/// The Java `_BinWriter.pec_encode`: the stitch records. Short form for deltas in
/// (-64, 63), long form (12-bit + flags) otherwise; first stitch is always long
/// form followed by a redundant short (0,0) record (measured quirk, kept).
fn pec_encode(w: &mut BinWriter, design: &Design) -> Result<(), Error> {
    let mut color_two = true;
    let (mut xx, mut yy) = (0f64, 0f64);
    for i in 0..design.stitches.len() {
        if i > 0 && design.colors[i] != design.colors[i - 1] {
            w.u8(0xFE);
            w.u8(0xB0);
            w.u8(if color_two { 2 } else { 1 });
            color_two = !color_two;
        }
        let (x, y) = (design.stitches[i].x as f64, design.stitches[i].y as f64);
        let mut dx = rint(x - xx);
        let mut dy = rint(y - yy);
        xx += f64::from(dx);
        yy += f64::from(dy);
        if i == 0 {
            // The first stitch gets the trim flag; later long forms do not
            // (the Java's else-branch below calls encode_long_form only).
            w.u16be(flag_trim(encode_long_form(dx)));
            w.u16be(flag_trim(encode_long_form(dy)));
            w.u8(0x00);
            w.u8(0x00);
            dx = 0;
            dy = 0;
        }
        if dx < 63 && dx > -64 && dy < 63 && dy > -64 {
            w.u8(dx & 0x7F);
            w.u8(dy & 0x7F);
        } else {
            w.u16be(encode_long_form(dx));
            w.u16be(encode_long_form(dy));
        }
    }
    w.u8(0xFF);
    Ok(())
}

/// The Java `_BinWriter.write_pes_sewsegheader`: the CEmbOne section header.
fn write_pes_sewsegheader(w: &mut BinWriter, left: f32, top: f32, right: f32, bottom: f32) {
    let height = bottom - top;
    let width = right - left;
    let (hoop_height, hoop_width) = (1800i32, 1300i32);
    for _ in 0..8 {
        w.u16le(0);
    }
    let mut trans_x = 0f32;
    let mut trans_y = 0f32;
    trans_x += 350f32;
    trans_y += 100f32 + height;
    trans_x += (hoop_width / 2) as f32;
    trans_y += (hoop_height / 2) as f32;
    trans_x += -width / 2.0;
    trans_y += -height / 2.0;
    w.f32le(1.0);
    w.f32le(0.0);
    w.f32le(0.0);
    w.f32le(1.0);
    w.f32le(trans_x);
    w.f32le(trans_y);
    w.u16le(1);
    w.u16le(0);
    w.u16le(0);
    w.u16le(width as i32);
    w.u16le(height as i32);
    w.u32le(0);
    w.u32le(0);
}

/// The Java `_BinWriter.write_pes_embsewseg_segments`: the CSewSeg segment
/// records. Returns the section count (patched into the placeholder).
/// The Java's `adjust_x = (int)(left + cx)` collapses to `bounds[0]` and
/// `adjust_y` to `bounds[3]` — both derived here, not passed.
fn write_pes_embsewseg_segments(
    w: &mut BinWriter,
    design: &Design,
    adjust_x: i32,
    adjust_y: i32,
) -> usize {
    let mut section = 0usize;
    let mut colorlog: Vec<i32> = Vec::new();
    let mut color_code = i32::from(find_color(design.colors[0]));
    colorlog.push(section as i32);
    colorlog.push(color_code);

    let mut segment: Vec<i32> = Vec::new();
    let p0 = design.stitches[0];
    segment.push(0 - adjust_x);
    segment.push(0 - adjust_y);
    segment.push(p0.x as i32 - adjust_x);
    segment.push(p0.y as i32 - adjust_y);
    let mut flag = 1i32;
    w.u16le(flag);
    w.u16le(color_code);
    w.u16le((segment.len() / 2) as i32);
    for v in &segment {
        w.u16le(*v);
    }
    section += 1;
    segment.clear();

    let mut i = 0usize;
    while i < design.stitches.len() {
        let this_color = design.colors[i];
        let mode = if i > 0 && design.colors[i - 1] != this_color {
            COLOR_CHANGE
        } else {
            STITCH
        };
        if mode != END && flag != -1 {
            w.u16le(0x8003);
        }
        match mode {
            COLOR_CHANGE => {
                color_code = i32::from(find_color(design.colors[i]));
                colorlog.push(section as i32);
                colorlog.push(color_code);
                flag = 1;
            }
            _ => {
                while i < design.stitches.len() && design.colors[i] == this_color {
                    let p = design.stitches[i];
                    segment.push(p.x as i32 - adjust_x);
                    segment.push(p.y as i32 - adjust_y);
                    i += 1;
                }
                i -= 1;
                flag = 0;
            }
        }
        if !segment.is_empty() {
            w.u16le(flag);
            w.u16le(color_code);
            w.u16le((segment.len() / 2) as i32);
            for v in &segment {
                w.u16le(*v);
            }
            section += 1;
        } else {
            flag = -1;
        }
        segment.clear();
        i += 1;
    }
    w.u16le((colorlog.len() / 2) as i32);
    for v in &colorlog {
        w.u16le(*v);
    }
    w.u16le(0x0000);
    w.u16le(0x0000);
    section
}

/// The Java `_BinWriter.write_pes_blocks`: the CEmbOne/CSewSeg block. Returns
/// nothing (the Java returns the colorlog, unused by write_version_1).
fn write_pes_blocks(w: &mut BinWriter, design: &Design) {
    let b = design.bounds;
    let (cx, cy) = ((b[0] + b[2]) / 2.0, (b[1] + b[3]) / 2.0);
    let (left, top, right, bottom) = (b[0] - cx, b[1] - cy, b[2] - cx, b[3] - cy);
    w.str16("CEmbOne");
    write_pes_sewsegheader(w, left, top, right, bottom);
    let sections_placeholder = w.placeholder(2);
    w.u16le(0xFFFF);
    w.u16le(0x0000);
    w.str16("CSewSeg");
    let section = write_pes_embsewseg_segments(w, design, b[0] as i32, b[3] as i32);
    w.patch(sections_placeholder, 2, section);
}

/// The Java `_BinWriter.write_pec`: header + stitch block + the needle-plate
/// icons (two always, one more per colour change).
fn write_pec(w: &mut BinWriter, design: &Design) -> Result<(), Error> {
    let palette = write_pec_header(w, design);
    write_pec_block(w, design)?;
    write_pec_graphics(w);
    write_pec_graphics(w);
    for _ in 1..palette.len() {
        write_pec_graphics(w);
    }
    Ok(())
}

/// The Java `write_version_1` (VERSION == 1, TRUNCATED == false): the whole file.
pub fn write(design: &Design) -> Result<Vec<u8>, Error> {
    let mut w = BinWriter::new();
    w.bytes(b"#PES0001");

    let pec_placeholder = w.placeholder(4);
    if design.stitches.is_empty() {
        w.u16le(0x01);
        w.u16le(0x01);
        w.u16le(0);
        w.u16le(0x0000);
        w.u16le(0x0000);
    } else {
        w.u16le(0x01);
        w.u16le(0x01);
        w.u16le(1);
        w.u16le(0xFFFF);
        w.u16le(0x0000);
        write_pes_blocks(&mut w, design);
    }
    let pec_offset = w.out.len();
    w.patch(pec_placeholder, 4, pec_offset);
    write_pec(&mut w, design)?;
    Ok(w.finish())
}

impl BinWriter {
    fn finish(self) -> Vec<u8> {
        self.out
    }
}

// ---------------------------------------------------------------- reader

fn u32le(bytes: &[u8]) -> u32 {
    u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]])
}

fn u16le(bytes: &[u8]) -> u16 {
    u16::from_le_bytes([bytes[0], bytes[1]])
}

fn u16be(bytes: &[u8]) -> u16 {
    u16::from_be_bytes([bytes[0], bytes[1]])
}

fn sign_extend_7(v: u8) -> i32 {
    if v & 0x40 != 0 {
        (u32::from(v) | 0xFFFFFF80) as i32
    } else {
        i32::from(v)
    }
}

fn sign_extend_12(v: i32) -> i32 {
    if v & 0x800 != 0 { v | -4096 } else { v }
}

/// Reads a PES v1/v6 file: the magic, the PEC offset, the 512-byte PEC header
/// (title, palette), and the PEC stitch block (delta records, colour changes
/// via the palette, 0xFF end).
///
/// Record grammar (measured against pyembroidery on test.pes, docs/LESSONS.md):
/// each axis word is independently flagged — a byte with bit 7 set opens a
/// 12-bit signed value spanning 2 bytes, otherwise the byte is a 7-bit signed
/// delta on its own. A record is therefore 2, 3 or 4 bytes. A reader that
/// forces 4 bytes on every long-form record misparses files whose writer omits
/// the flag byte on the dy word (e.g. Ticetac's), inventing phantom points.
///
/// The x/y-offset words in the block header are the design's origin: they read
/// as a leading long-form record (`0x9000 | -left`, so `left = 0` decodes to
/// `(0, 0)` and `0x9480` to `+1152`). The stream accumulates from that origin,
/// which lands the design inside the declared width/height — those become the
/// design bounds `[0, 0, w, h]`. The design's `jumps` are left empty — the PEC
/// stream has no per-stitch jump flags.
pub fn read(bytes: &[u8]) -> Result<Design, Error> {
    if bytes.len() < 12 {
        return Err(Error::Truncated {
            wanted: 12,
            found: bytes.len(),
        });
    }
    let mut magic = [0u8; 8];
    magic.copy_from_slice(&bytes[0..8]);
    if magic != *b"#PES0001" && magic != *b"#PES0060" {
        return Err(Error::UnsupportedPesMagic { magic });
    }
    let pec_offset = u32le(&bytes[8..12]) as usize;
    if pec_offset > bytes.len() {
        return Err(Error::Truncated {
            wanted: pec_offset,
            found: bytes.len(),
        });
    }

    // PEC header layout (measured against the fixture at docs/FORMAT.md):
    //   +0  LA line (20)
    //   +20 12×0x20, +32 0xFF, +33 0x00, +34 icon dims (2), +36 12×0x20
    //   +48 palette count, +49 palette indices, then 0x20 fill — 512 bytes total.
    if pec_offset + 512 > bytes.len() {
        return Err(Error::Truncated {
            wanted: pec_offset + 512,
            found: bytes.len(),
        });
    }
    let title = String::from_utf8_lossy(&bytes[pec_offset + 3..pec_offset + 19])
        .trim_end()
        .to_string();
    let palette_len = usize::from(bytes[pec_offset + 48]) + 1;
    let palette_start = pec_offset + 49;
    if palette_start + palette_len > pec_offset + 512 {
        return Err(Error::Malformed {
            format: "PES",
            detail: "palette extends past the PEC header".into(),
        });
    }
    let mut palette = Vec::with_capacity(palette_len);
    for i in 0..palette_len {
        palette.push(palette_rgb(bytes[palette_start + i]));
    }

    // PEC stitch block:
    //   +0 0x00 0x00, +2 u24 length, +5 0x31 0xFF 0xF0, +8 w u16le, +10 h u16le,
    //   +12 0x1E0, +14 0x1B0, +16 x-offset u16be, +18 y-offset u16be,
    //   +20 delta records, ... 0xFF end.
    let block = pec_offset + 512;
    if block + 20 > bytes.len() {
        return Err(Error::Truncated {
            wanted: block + 20,
            found: bytes.len(),
        });
    }
    let declared_len = usize::from(bytes[block + 2])
        | (usize::from(bytes[block + 3]) << 8)
        | (usize::from(bytes[block + 4]) << 16);
    let block_end = block
        .checked_add(declared_len)
        .filter(|end| *end <= bytes.len())
        .ok_or(Error::Truncated {
            wanted: block + declared_len,
            found: bytes.len(),
        })?;
    let width = i32::from(u16le(&bytes[block + 8..block + 10]));
    let height = i32::from(u16le(&bytes[block + 10..block + 12]));

    // The offset words are the design's origin: `0x9000 | -left`, read as a
    // long-form record. The deltas accumulate from there, so the design lands
    // inside the declared width/height instead of at the raw stream extents.
    let origin_x = sign_extend_12(i32::from(u16be(&bytes[block + 16..block + 18])) & 0x0FFF);
    let origin_y = sign_extend_12(i32::from(u16be(&bytes[block + 18..block + 20])) & 0x0FFF);

    let mut stitches = Vec::new();
    let mut colors = Vec::new();
    let mut color_changes = 0usize;
    let mut current_color = palette[0];
    let (mut xx, mut yy) = (origin_x, origin_y);
    let mut pos = block + 20;
    let mut found_end = false;
    while pos < block_end {
        let b = bytes[pos];
        if b == 0xFF {
            found_end = true;
            break;
        }
        if b == 0xFE {
            if pos + 2 >= block_end {
                return Err(Error::Truncated {
                    wanted: pos + 3,
                    found: block_end,
                });
            }
            if bytes[pos + 1] != 0xB0 {
                return Err(Error::Malformed {
                    format: "PES",
                    detail: format!("expected 0xB0 after 0xFE at offset {pos}"),
                });
            }
            color_changes += 1;
            current_color = *palette.get(color_changes).ok_or_else(|| Error::Malformed {
                format: "PES",
                detail: format!(
                    "colour change {color_changes} beyond the {}-entry palette",
                    palette.len()
                ),
            })?;
            pos += 3;
            continue;
        }
        // dx axis: bit 7 opens a 12-bit long form (2 bytes), else a 7-bit
        // short (1 byte).
        let (dx, dy, advance) = if b & 0x80 != 0 {
            if pos + 2 > block_end {
                // The declared length cuts the record short with no end marker:
                // the missing-marker ruling (the length bounds the stream).
                break;
            }
            let v1 = i32::from(u16be(&bytes[pos..pos + 2]));
            // dy axis: independently flagged, so the record is 3 or 4 bytes.
            let b2 = bytes[pos + 2];
            if b2 & 0x80 != 0 {
                if pos + 4 > block_end {
                    break;
                }
                let v2 = i32::from(u16be(&bytes[pos + 2..pos + 4]));
                (sign_extend_12(v1 & 0x0FFF), sign_extend_12(v2 & 0x0FFF), 4)
            } else {
                if pos + 3 > block_end {
                    break;
                }
                (sign_extend_12(v1 & 0x0FFF), sign_extend_7(b2), 3)
            }
        } else {
            if pos + 2 > block_end {
                break;
            }
            (sign_extend_7(b), sign_extend_7(bytes[pos + 1]), 2)
        };
        xx += dx;
        yy += dy;
        stitches.push(Point {
            x: xx as f32,
            y: yy as f32,
        });
        colors.push(current_color);
        pos += advance;
    }
    if !found_end {
        // The declared block length bounds the stream: a block whose records
        // end exactly at `block_end` (or are cut by it) without a 0xFF end
        // marker is accepted — the same ruling as the DST writer's missing
        // END record (docs/LESSONS.md).
        log::warn!(
            "PES: stitch block at offset {block} has no 0xFF end marker but \
             the declared length is respected — accepting (missing-marker file)"
        );
    }

    Ok(Design {
        bounds: [0.0, 0.0, width as f32, height as f32],
        stitches,
        colors,
        jumps: Vec::new(),
        title,
    })
}
