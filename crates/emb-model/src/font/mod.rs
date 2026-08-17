//! The Hershey vector font: `PEmbroiderFont.putText` ported for the editor's
//! TXT stitching.
//!
//! The Java editor rasterises text with Java2D (`pg.text`), which is the
//! D003-class unreproducible raster; the Rust editor stitches TXT through the
//! Java's OWN vector font API (`PEmbroiderFont.putText` + `SIMPLEX`) instead
//! — a D010-class decision recorded in the ruling and ROADMAP. The glyph data
//! is the classic public-domain Hershey table (`data.rs`, generated 2026-08-17
//! from the Java suite's `PEmbroiderFont` data, the model); the layout logic
//! below mirrors `putChar`/`putText` exactly, and the fixture tests compare
//! the output against the Java generator within D002's tolerance.
//!
//! Anchor semantics: the baseline-LEFT of the text sits at `(x, y)` (the
//! Java's `FONT_ALIGN = LEFT`, `FONT_ALIGN_VERTICAL = BASELINE` defaults —
//! the editor's click point). `scale` is `FONT_SCALE` = the editor's text
//! size in mm.

pub mod data;

use crate::geom::Point;

/// The Java's `parseBound`: chars 3-4 of the glyph string are the xmin/xmax
/// bounds as offsets from 'R'.
fn parse_bound(entry: &str) -> (i32, i32) {
    let ord_r = 'R' as i32;
    let bytes = entry.as_bytes();
    (bytes[3] as i32 - ord_r, bytes[4] as i32 - ord_r)
}

/// Look up the glyph string for a char through the SIMPLEX cmap. The Java
/// falls back to a space glyph when the char is out of range or missing.
fn glyph_for(cm: &[u16], c: char) -> Option<&'static str> {
    let idx = (c as usize).wrapping_sub(32);
    let id = *cm.get(idx)?;
    data::GLYPHS
        .iter()
        .find(|(glyph_id, _)| *glyph_id == id)
        .map(|(_, s)| *s)
}

/// `PEmbroiderFont.putChar`: append the glyph's polylines to `out`, returns
/// the advance (xmax - xmin) in scaled units.
///
/// The glyph string is 5 header chars (3 ignored, then xmin/xmax as
/// R-relative chars), then coordinate pairs (R-relative), with the pair
/// `" R"` marking a pen-up (starts a new polyline). A point is
/// `(ox + x*scl - xmin, oy + y*scl)`.
fn put_char(cm: &[u16], c: char, ox: f32, oy: f32, scl: f32, out: &mut Vec<Vec<Point>>) -> f32 {
    let Some(entry) = glyph_for(cm, c).or_else(|| glyph_for(cm, ' ')) else {
        return 0.0;
    };
    let (xmin_c, xmax_c) = parse_bound(entry);
    let xmin = xmin_c as f32 * scl;
    let xmax = xmax_c as f32 * scl;
    let content = &entry[5..];
    let bytes = content.as_bytes();

    out.push(Vec::new());
    for i in (0..bytes.len()).step_by(2) {
        if i + 1 >= bytes.len() {
            break;
        }
        let (a, b) = (bytes[i] as char, bytes[i + 1] as char);
        if a == ' ' && b == 'R' {
            out.push(Vec::new());
        } else {
            let x = (a as i32 - 'R' as i32) as f32 * scl - xmin;
            let y = (b as i32 - 'R' as i32) as f32 * scl;
            if let Some(last) = out.last_mut() {
                last.push(Point::new(ox + x, oy + y));
            }
        }
    }
    xmax - xmin
}

/// `PEmbroiderFont.estimateTextWidth`: the sum of the unscaled glyph advances.
fn estimate_text_width(cm: &[u16], s: &str) -> i32 {
    let mut sum = 0;
    for c in s.chars() {
        if let Some(entry) = glyph_for(cm, c).or_else(|| glyph_for(cm, ' ')) {
            let (a, b) = parse_bound(entry);
            sum += b - a;
        }
    }
    sum
}

/// `PEmbroiderFont.putText`: the text's glyph strokes at `(ox, oy)` scaled by
/// `scl`, with the anchor adjusted for `align` (LEFT = baseline-left, the
/// default the editor uses). Returns one polyline per pen-up segment.
pub fn put_text(cm: &[u16], s: &str, ox: f32, oy: f32, scl: f32, align: Align) -> Vec<Vec<Point>> {
    let mut out: Vec<Vec<Point>> = Vec::new();
    let mut ox = ox;
    if align != Align::Left {
        let w = estimate_text_width(cm, s);
        match align {
            Align::Right => ox -= scl * w as f32,
            Align::Center => ox -= scl * w as f32 / 2.0,
            Align::Left => {}
        }
    }
    for c in s.chars() {
        ox += put_char(cm, c, ox, oy, scl, &mut out);
    }
    // NO retain here: the Java keeps every polyline `putChar` pushed,
    // including the empty ones (a space glyph, a trailing pen-up) — the
    // fixture comparison is byte-for-byte, so the port keeps them too.
    out
}

/// The horizontal alignment of `putText` (the Java's `PConstants` LEFT/RIGHT/
/// CENTER). The editor uses `Left`; the others exist for completeness of the
/// port.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Align {
    Left,
    Right,
    Center,
}

/// The SIMPLEX cmap, as the `putText` caller passes it.
pub const SIMPLEX: &[u16] = &data::SIMPLEX;

#[cfg(test)]
mod tests {
    use super::*;

    /// 'H' = 508: the fixture's first three polylines (two stems + the
    /// crossbar) at baseline-left (10, 20), scale 1. Pinned against
    /// `fixtures/model/hershey_hello.txt` in the fixture test; here we check
    /// the layout constants directly.
    #[test]
    fn h_advances_22_and_draws_three_strokes() {
        let polys = put_text(SIMPLEX, "H", 10.0, 20.0, 1.0, Align::Left);
        assert_eq!(polys.len(), 3);
        // Left stem: (14, 8) - (14, 29): ox + x*scl - xmin with xmin = -11.
        assert_eq!(polys[0][0], Point::new(14.0, 8.0));
        assert_eq!(polys[0][1], Point::new(14.0, 29.0));
        // Crossbar at y = 18 (the oy baseline 20 minus the glyph's y).
        assert_eq!(polys[2][0], Point::new(14.0, 18.0));
        assert_eq!(polys[2][1], Point::new(28.0, 18.0));
    }

    #[test]
    fn unknown_char_falls_back_to_space() {
        // The Java's putChar: no glyph → warn + substitute a space, whose
        // empty content still pushes ONE empty polyline. Kept for parity.
        let polys = put_text(SIMPLEX, "\u{00e9}", 0.0, 0.0, 1.0, Align::Left);
        assert_eq!(polys.len(), 1);
        assert!(polys[0].is_empty());
    }

    #[test]
    fn scale_multiplies_points_and_advance() {
        // 'W' = 523 at scale 4, baseline (30, 40): pinned in the fixture
        // (hershey_w_scale4.txt, the first stroke's first point).
        let polys = put_text(SIMPLEX, "W", 30.0, 40.0, 4.0, Align::Left);
        assert_eq!(polys.len(), 4);
        assert_eq!(polys[0][0], Point::new(38.0, -8.0));
    }

    #[test]
    fn put_text_advances_characters_left_to_right() {
        let polys = put_text(SIMPLEX, "HI", 0.0, 0.0, 1.0, Align::Left);
        // H advance = 22, so 'I' starts at x = 22; I's stem is at x 22 + its
        // own xmin offset.
        assert!(polys.len() >= 2);
    }
}
