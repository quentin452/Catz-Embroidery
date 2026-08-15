//! SVG writer, ported from `PEmbroiderWriter.SVG` (the Java suite is the model).
//!
//! Measured deviation: the Java formats the stitch coordinates with
//! `String.format("%.3f", ...)` WITHOUT a locale — on a French-locale machine the
//! output uses commas and is unparsable. The Rust writer always writes dots
//! (fixtures are generated with `-Duser.language=en`; see docs/LESSONS.md).

use crate::Design;

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
