//! Tajima DST writer, ported from `PEmbroiderWriter.DST` (the Java suite is the
//! model; the measured differences live in docs/LESSONS.md).
//!
//! One documented deviation: the Java encodes the END record but never writes it
//! (`PEmbroiderWriter.java:206-207` closes the stream right after the encode);
//! the Rust writer writes it. The fixture test encodes the deviation explicitly.

use crate::{Design, Error, rint};

const HEADER_SIZE: usize = 512;
const COMMAND_SIZE: usize = 3;

const STITCH: u8 = 0;
const JUMP: u8 = 1;
const END: u8 = 4;
const COLOR_CHANGE: u8 = 5;

fn bit(b: u8) -> u8 {
    1u8 << b
}

/// The Java `DST.encodeRecord`: a balanced signed delta encoding, y axis flipped.
/// Returns the 3-byte record, or an error if the delta cannot be represented
/// (the Java prints "Write exceeded possible distance" and writes garbage).
fn encode_record(dx: i32, dy: i32, flags: u8) -> Result<[u8; 3], Error> {
    let (mut x, mut y) = (dx, -dy);
    let (mut b0, mut b1, mut b2) = (0u8, 0u8, 0u8);
    match flags {
        COLOR_CHANGE => return Ok([0, 0, 0b1100_0011]),
        END => return Ok([0, 0, 0b1111_0011]),
        _ => {}
    }
    if flags == JUMP {
        b2 += bit(7);
    }
    b2 += bit(0);
    b2 += bit(1);
    if x > 40 {
        b2 += bit(2);
        x -= 81;
    }
    if x < -40 {
        b2 += bit(3);
        x += 81;
    }
    if x > 13 {
        b1 += bit(2);
        x -= 27;
    }
    if x < -13 {
        b1 += bit(3);
        x += 27;
    }
    if x > 4 {
        b0 += bit(2);
        x -= 9;
    }
    if x < -4 {
        b0 += bit(3);
        x += 9;
    }
    if x > 1 {
        b1 += bit(0);
        x -= 3;
    }
    if x < -1 {
        b1 += bit(1);
        x += 3;
    }
    if x > 0 {
        b0 += bit(0);
        x -= 1;
    }
    if x < 0 {
        b0 += bit(1);
        x += 1;
    }
    if y > 40 {
        b2 += bit(5);
        y -= 81;
    }
    if y < -40 {
        b2 += bit(4);
        y += 81;
    }
    if y > 13 {
        b1 += bit(5);
        y -= 27;
    }
    if y < -13 {
        b1 += bit(4);
        y += 27;
    }
    if y > 4 {
        b0 += bit(5);
        y -= 9;
    }
    if y < -4 {
        b0 += bit(4);
        y += 9;
    }
    if y > 1 {
        b1 += bit(7);
        y -= 3;
    }
    if y < -1 {
        b1 += bit(6);
        y += 3;
    }
    if y > 0 {
        b0 += bit(7);
        y -= 1;
    }
    if y < 0 {
        b0 += bit(6);
        y += 1;
    }
    if x != 0 || y != 0 {
        return Err(Error::DeltaOverflow {
            format: "DST",
            dx,
            dy,
        });
    }
    Ok([b0, b1, b2])
}

/// The Java `DST.write`: a 512-byte header, then 3-byte delta records.
pub fn write(design: &Design) -> Result<Vec<u8>, Error> {
    let stitches = &design.stitches;
    let colors = &design.colors;
    let jumps = &design.jumps;

    let mut color_blocks = 1usize;
    for i in 1..colors.len() {
        if colors[i] != colors[i - 1] {
            color_blocks += 1;
        }
    }

    let mut out = Vec::with_capacity(HEADER_SIZE + (stitches.len() + 4) * COMMAND_SIZE);
    out.extend_from_slice(format!("LA:{:<16}\r", design.title).as_bytes());
    out.extend_from_slice(format!("ST:{:>7}\r", stitches.len()).as_bytes());
    out.extend_from_slice(format!("CO:{:>3}\r", color_blocks - 1).as_bytes());
    let b = design.bounds;
    out.extend_from_slice(format!("+X:{:>5}\r", b[2].abs() as i32).as_bytes());
    out.extend_from_slice(format!("-X:{:>5}\r", b[0].abs() as i32).as_bytes());
    out.extend_from_slice(format!("+Y:{:>5}\r", b[3].abs() as i32).as_bytes());
    out.extend_from_slice(format!("-Y:{:>5}\r", b[1].abs() as i32).as_bytes());

    let (ax, ay) = match stitches.last() {
        Some(p) => (p.x as i32, -(p.y as i32)),
        None => (0, 0),
    };
    let signed = |v: i32| {
        if v >= 0 {
            format!("+{v:>5}")
        } else {
            format!("-{:>5}", v.abs())
        }
    };
    out.extend_from_slice(format!("AX:{}\r", signed(ax)).as_bytes());
    out.extend_from_slice(format!("AY:{}\r", signed(ay)).as_bytes());
    out.extend_from_slice(b"MX:+    0\rMY:+    0\rPD:******\r");
    out.push(0x1A);
    out.resize(HEADER_SIZE, b' ');

    let write_record = |dx: i32, dy: i32, flags: u8, out: &mut Vec<u8>| -> Result<(), Error> {
        let record = encode_record(dx, dy, flags)?;
        out.extend_from_slice(&record);
        Ok(())
    };
    let (mut xx, mut yy) = (0f64, 0f64);
    for i in 0..stitches.len() {
        if i > 0 && colors[i] != colors[i - 1] {
            write_record(0, 0, COLOR_CHANGE, &mut out)?;
        }
        let mut data = STITCH;
        let (x, y) = (stitches[i].x as f64, stitches[i].y as f64);
        let mut dx = rint(x - xx);
        let mut dy = rint(y - yy);
        xx += f64::from(dx);
        yy += f64::from(dy);

        if dx.abs() >= 100 || dy.abs() >= 100 {
            data = JUMP;
            let steps = (dx / 100).abs().max((dy / 100).abs()) + 1;
            let inc = 1.0f32 / steps as f32;
            let (ddx, ddy) = (
                rint(f64::from(dx as f32 * inc)),
                rint(f64::from(dy as f32 * inc)),
            );
            let (mut accx, mut accy) = (0i32, 0i32);
            for _ in 0..steps - 1 {
                write_record(ddx, ddy, data, &mut out)?;
                accx += ddx;
                accy += ddy;
            }
            dx -= accx;
            dy -= accy;
        }

        if i != 0 && jumps.get(i).copied().unwrap_or(false) {
            data = JUMP;
        }

        write_record(dx, dy, data, &mut out)?;
    }
    // Deviation from the Java model (PEmbroiderWriter.java:206-207): the END
    // record is written, not just encoded. See docs/LESSONS.md.
    write_record(0, 0, END, &mut out)?;
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encode_basic_delta() {
        assert_eq!(encode_record(10, 10, STITCH).unwrap(), [0x55, 0x00, 0x03]);
        assert_eq!(encode_record(0, 0, JUMP).unwrap(), [0x00, 0x00, 0x83]);
        assert_eq!(
            encode_record(0, 0, COLOR_CHANGE).unwrap(),
            [0x00, 0x00, 0xC3]
        );
        assert_eq!(encode_record(0, 0, END).unwrap(), [0x00, 0x00, 0xF3]);
    }

    #[test]
    fn overflow_is_an_error() {
        assert!(encode_record(122, 0, STITCH).is_err());
    }
}
