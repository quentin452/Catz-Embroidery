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

/// `Math.rint` semantics (half-to-even), matching the Java suite's rounding.
fn rint(v: f64) -> i32 {
    v.round_ties_even() as i32
}

/// `Math.round` semantics (half toward +inf), used where the Java uses it.
fn java_round(v: f32) -> i32 {
    (v + 0.5).floor() as i32
}
