#![forbid(unsafe_code)]
//! Parsers/writers for machine formats (PES read+write, DST and SVG write) — typed,
//! bounds-checked, structured errors. No model dependency (docs/decisions/D001.md).

pub mod dst;
pub mod pes;
pub mod svg;

/// A point in design space, in millimetres (the Java suite's `PVector`).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Point {
    pub x: f32,
    pub y: f32,
}

/// One design as the formats see it: absolute stitch positions, one colour per
/// stitch, optional per-stitch jump flags, design bounds, title.
#[derive(Debug, Clone, PartialEq)]
pub struct Design {
    /// `[left, top, right, bottom]` in mm (the Java `float[] bounds`).
    pub bounds: [f32; 4],
    pub stitches: Vec<Point>,
    /// Packed `0x00RRGGBB` per stitch.
    pub colors: Vec<u32>,
    /// Per-stitch jump flag; may be shorter (treated as all-false).
    pub jumps: Vec<bool>,
    pub title: String,
}

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("io: {0}")]
    Io(#[from] std::io::Error),
    #[error("truncated data: wanted {wanted} bytes, found {found}")]
    Truncated { wanted: usize, found: usize },
    #[error("malformed {format}: {detail}")]
    Malformed {
        format: &'static str,
        detail: String,
    },
    #[error("unencodable delta in {format}: dx={dx}, dy={dy}")]
    DeltaOverflow {
        format: &'static str,
        dx: i32,
        dy: i32,
    },
    #[error("unsupported PES magic: {magic:?}")]
    UnsupportedPesMagic { magic: [u8; 8] },
}

/// Write a design to a file, choosing the writer by extension. PES/DST/SVG
/// are the M1 writers; anything else is refused (the Java's "Unsupported
/// format" path — the Rust scope is D001's format list). The single save
/// path the editor and the converter share.
pub fn write_design(path: &std::path::Path, design: &Design) -> Result<(), String> {
    let ext = path
        .extension()
        .map(|e| e.to_string_lossy().to_lowercase())
        .unwrap_or_default();
    let bytes: Vec<u8> = match ext.as_str() {
        "pes" => pes::write(design).map_err(|e| e.to_string())?,
        "dst" => dst::write(design).map_err(|e| e.to_string())?,
        "svg" => svg::write(design).into_bytes(),
        _ => return Err(format!("unsupported extension .{ext}")),
    };
    std::fs::write(path, bytes).map_err(|e| e.to_string())
}

/// The Java's title rule: the file stem, truncated to 8 characters
/// (`PEmbroiderWriter.write`: `TITLE.substring(0, min(8, len))`).
pub fn file_title(path: &std::path::Path) -> String {
    let stem = path
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_default();
    stem.chars().take(8).collect()
}

/// `Math.rint` semantics (half-to-even), matching the Java suite's rounding.
fn rint(v: f64) -> i32 {
    v.round_ties_even() as i32
}

/// `Math.round` semantics (half toward +inf), used where the Java uses it.
fn java_round(v: f32) -> i32 {
    (v + 0.5).floor() as i32
}

#[cfg(test)]
mod tests {
    use super::*;

    fn empty_design() -> Design {
        Design {
            bounds: [0.0; 4],
            stitches: vec![],
            colors: vec![],
            jumps: vec![],
            title: "t".into(),
        }
    }

    #[test]
    fn file_title_truncates_to_eight() {
        let p = std::path::Path::new("verylongname.pes");
        assert_eq!(file_title(p), "verylong");
        let p = std::path::Path::new("ab.pes");
        assert_eq!(file_title(p), "ab");
    }

    #[test]
    fn write_design_refuses_unknown_extensions() {
        assert!(write_design(std::path::Path::new("x.gcode"), &empty_design()).is_err());
    }
}
