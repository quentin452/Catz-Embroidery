//! SVG writer + reader, ported from `PEmbroiderWriter.SVG` (the Java suite is
//! the model). The reader (the viewer's SVG entry point, gap #15) is the
//! counterpart of [`write`]: it parses the format this writer emits and is
//! round-trip-tested against it.
//!
//! Measured deviation: the Java formats the stitch coordinates with
//! `String.format("%.3f", ...)` WITHOUT a locale — on a French-locale machine the
//! output uses commas and is unparsable. The Rust writer always writes dots
//! (fixtures are generated with `-Duser.language=en`; see docs/LESSONS.md).

use crate::{Design, Error, Point};

/// `Float.toString` semantics: shortest round-tripping representation, with a
/// trailing `.0` when the value is integral (Java prints `100.0`, Rust prints
/// `100`).
fn java_f32(v: f32) -> String {
    let s = format!("{v}");
    if s.contains(['.', 'e', 'E']) {
        s
    } else {
        s + ".0"
    }
}

/// The Java `SVG.svgString`, one string per design.
pub fn write(design: &Design) -> String {
    let b = design.bounds;
    let w = b[2] - b[0];
    let h = b[3] - b[1];
    let mut svg = format!(
        "<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" width=\"{}\" height=\"{}\" \
         viewBox=\"{} {} {} {}\">",
        java_f32(w),
        java_f32(h),
        java_f32(b[0]),
        java_f32(b[1]),
        java_f32(w),
        java_f32(h)
    );
    if design.stitches.is_empty() {
        return svg + "</svg>";
    }
    for (i, p) in design.stitches.iter().enumerate() {
        let is_jump = design.jumps.get(i).copied().unwrap_or(false);
        if i == 0 || design.colors[i] != design.colors[i - 1] || is_jump {
            let c = design.colors[i];
            let r = (c >> 16) & 0xFF;
            let g = (c >> 8) & 0xFF;
            let bl = c & 0xFF;
            if i != 0 {
                svg.push_str("\"/>");
            }
            svg.push_str(&format!(
                "<path fill=\"none\" stroke=\"#{r:02x}{g:02x}{bl:02x}\" d=\"M"
            ));
        } else {
            svg.push_str(" L");
        }
        svg.push_str(&format!("{:.3},{:.3}", p.x, p.y));
    }
    svg + "\"/></svg>"
}

/// Parse an SVG string back into a [`Design`] — the reader counterpart of
/// [`write`] (the viewer's SVG entry point). It accepts the format this
/// writer emits: `<svg viewBox="x y w h">` with `<path d="M x,y L x,y ...">`
/// elements carrying a `stroke="#rrggbb"`. It is deliberately NOT a general
/// SVG/XML parser (no dependency): a path's `d` is a flat list of `M` (move,
/// starts a new subpath = a jump) and `L` (line-to) commands, one stitch each.
/// The bounds come from the viewBox; a missing viewBox is an error.
///
/// Unknown attributes/tags are ignored, so a hand-authored or third-party SVG
/// that keeps to `<path d="...">` also reads. This is the format-as-the-writer-
/// emits-it level (D001), validated by the round-trip test.
pub fn read(svg: &str) -> Result<Design, Error> {
    let bounds = parse_viewbox(svg)?;
    let mut stitches: Vec<Point> = Vec::new();
    let mut colors: Vec<u32> = Vec::new();
    let mut jumps: Vec<bool> = Vec::new();

    for path in paths(svg) {
        let color = stroke_color(path)?;
        let Some(d) = attribute(path, "d") else {
            return Err(Error::Malformed {
                format: "SVG",
                detail: "a <path> has no d attribute".into(),
            });
        };
        parse_d(d, &mut stitches, &mut colors, &mut jumps, color)?;
    }

    Ok(Design {
        bounds,
        stitches,
        colors,
        jumps,
        title: String::new(),
    })
}

/// `[left, top, right, bottom]` from the `<svg viewBox="x y w h">`.
fn parse_viewbox(svg: &str) -> Result<[f32; 4], Error> {
    let Some(vb) = attribute(svg, "viewBox") else {
        return Err(Error::Malformed {
            format: "SVG",
            detail: "no viewBox on the <svg> element".into(),
        });
    };
    let nums: Vec<f32> = vb
        .split(|c: char| c.is_ascii_whitespace() || c == ',')
        .filter(|s| !s.is_empty())
        .map(|s| s.parse::<f32>())
        .collect::<Result<_, _>>()
        .map_err(|_| Error::Malformed {
            format: "SVG",
            detail: format!("viewBox is not four numbers: {vb}"),
        })?;
    if nums.len() != 4 {
        return Err(Error::Malformed {
            format: "SVG",
            detail: format!("viewBox must have four numbers, got {}", nums.len()),
        });
    }
    Ok([nums[0], nums[1], nums[0] + nums[2], nums[1] + nums[3]])
}

/// The `<path>` elements as raw substring slices (self-closing `<path .../>`).
fn paths(svg: &str) -> Vec<&str> {
    let mut out = Vec::new();
    let mut rest = svg;
    while let Some(start) = rest.find("<path") {
        let rest_after = &rest[start + "<path".len()..];
        // Find the end of this path element: its own closing `/>` (the writer
        // emits each path self-closing, and a `<path .../>` never contains
        // `/>` before the end).
        let Some(end_rel) = rest_after.find("/>") else {
            break;
        };
        let element = &rest_after[..end_rel];
        out.push(element);
        rest = &rest_after[end_rel + 2..];
    }
    out
}

/// The value of `name="..."` (or `name='...'`) anywhere in `s`, or None.
fn attribute<'a>(s: &'a str, name: &str) -> Option<&'a str> {
    let needle = format!("{name}=");
    let idx = s.find(&needle)?;
    let after = &s[idx + needle.len()..];
    let quote = after.chars().next()?;
    if quote != '"' && quote != '\'' {
        return None;
    }
    let rest = &after[quote.len_utf8()..];
    let end = rest.find(quote)?;
    Some(&rest[..end])
}

/// The `stroke="#rrggbb"` as a packed 0xRRGGBB (default black when absent).
fn stroke_color(path: &str) -> Result<u32, Error> {
    let Some(stroke) = attribute(path, "stroke") else {
        return Ok(0x000000);
    };
    let hex = stroke.trim_start_matches('#');
    u32::from_str_radix(hex, 16).map_err(|_| Error::Malformed {
        format: "SVG",
        detail: format!("stroke is not #rrggbb: {stroke}"),
    })
}

/// Walk a `d` string: `M x,y` opens a subpath (jump), `L x,y` appends a
/// stitch. The writer emits commands glued to the first number
/// (`M12.000,30.000`), and a general SVG may use spaces or a bare-pair
/// shorthand — so the path is scanned character by character, pulling out the
/// command letters `M`/`L` (any other letter resets to `L`, the SVG default
/// for a following coordinate) and the coordinate pairs. Every coordinate
/// pair becomes one stitch with one jump flag: a `M` (or the first stitch) is
/// a jump, everything else is not.
fn parse_d(
    d: &str,
    stitches: &mut Vec<Point>,
    colors: &mut Vec<u32>,
    jumps: &mut Vec<bool>,
    color: u32,
) -> Result<(), Error> {
    let chars: Vec<char> = d.chars().collect();
    let mut i = 0usize;
    let mut is_jump = true;
    while i < chars.len() {
        match chars[i] {
            'M' | 'm' => {
                is_jump = true;
                i += 1;
            }
            'L' | 'l' => {
                is_jump = false;
                i += 1;
            }
            c if c.is_ascii_alphabetic() => {
                // Any other command letter (C, Q, Z, ...) is out of the writer's
                // vocabulary; treat it as a line for the following coordinate,
                // so a simple hand-authored path still reads.
                is_jump = false;
                i += 1;
            }
            c if c.is_ascii_digit() || c == '-' || c == '+' || c == '.' => {
                let (x, next) = parse_number(&chars, i)?;
                i = next;
                // Skip to the next number (comma/space separators).
                while i < chars.len() && !(chars[i].is_ascii_digit() || chars[i] == '-') {
                    i += 1;
                }
                let (y, next) = parse_number(&chars, i)?;
                i = next;
                stitches.push(Point { x, y });
                colors.push(color);
                jumps.push(is_jump);
                is_jump = false;
            }
            _ => i += 1, // separator / whitespace
        }
    }
    Ok(())
}

/// Parse one float starting at `i`; returns the value and the index after it.
fn parse_number(chars: &[char], i: usize) -> Result<(f32, usize), Error> {
    let mut j = i;
    let mut saw_dot = false;
    while j < chars.len() {
        let c = chars[j];
        if c.is_ascii_digit() {
            j += 1;
        } else if c == '.' && !saw_dot {
            saw_dot = true;
            j += 1;
        } else if (c == '-' || c == '+') && j == i {
            j += 1;
        } else {
            break;
        }
    }
    if j == i {
        return Err(Error::Malformed {
            format: "SVG",
            detail: "expected a number in the path".into(),
        });
    }
    let s: String = chars[i..j].iter().collect();
    let v = s.parse::<f32>().map_err(|_| Error::Malformed {
        format: "SVG",
        detail: format!("non-numeric coordinate '{s}'"),
    })?;
    Ok((v, j))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn design() -> Design {
        Design {
            bounds: [10.0, 20.0, 110.0, 90.0],
            stitches: vec![
                Point { x: 12.0, y: 30.0 },
                Point { x: 50.0, y: 60.0 },
                Point { x: 90.0, y: 40.0 },
            ],
            colors: vec![0xFF0000, 0xFF0000, 0x0000FF],
            jumps: vec![true, false, true],
            title: "test".into(),
        }
    }

    #[test]
    fn round_trips_its_writer() {
        let svg = write(&design());
        let back = read(&svg).expect("parse what the writer emitted");
        assert_eq!(back.bounds, design().bounds);
        assert_eq!(back.stitches, design().stitches);
        assert_eq!(back.colors, design().colors);
        // Each subpath boundary is a jump (the writer's `M` per colour/jump).
        assert_eq!(back.jumps, vec![true, false, true]);
    }

    #[test]
    fn reads_a_hand_written_path() {
        let svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"50\" \
                   viewBox=\"0 0 100 50\"><path fill=\"none\" stroke=\"#00ff00\" \
                   d=\"M 5,10 L 20,30 L 40,15\"/></svg>";
        let d = read(svg).expect("parse hand-written svg");
        assert_eq!(d.bounds, [0.0, 0.0, 100.0, 50.0]);
        assert_eq!(d.stitches.len(), 3);
        assert_eq!(d.colors, vec![0x00FF00; 3]);
        assert_eq!(d.jumps, vec![true, false, false]);
    }

    #[test]
    fn rejects_a_missing_viewbox() {
        let svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>";
        assert!(read(svg).is_err());
    }

    #[test]
    fn empty_design_writes_and_reads() {
        let d = Design {
            bounds: [0.0, 0.0, 0.0, 0.0],
            stitches: vec![],
            colors: vec![],
            jumps: vec![],
            title: "empty".into(),
        };
        let svg = write(&d);
        let back = read(&svg).expect("empty svg reads");
        assert!(back.stitches.is_empty());
    }
}
