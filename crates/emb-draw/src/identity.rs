//! The visual identity: how a design looks on screen. One source — renderers
//! cannot drift on line width, colour or spacing (docs/LESSONS.md).

/// An sRGB colour, unpacked from the model's packed `0x00RRGGBB` so no
/// renderer has to guess the packing.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Rgb {
    pub r: u8,
    pub g: u8,
    pub b: u8,
}

impl Rgb {
    pub const fn from_packed(packed: u32) -> Self {
        Self {
            r: ((packed >> 16) & 0xFF) as u8,
            g: ((packed >> 8) & 0xFF) as u8,
            b: (packed & 0xFF) as u8,
        }
    }

    pub const fn new(r: u8, g: u8, b: u8) -> Self {
        Self { r, g, b }
    }
}

/// The shared look of a rendered design: what the canvas is, what sits behind
/// it, and how thick a stitch line is.
#[derive(Debug, Clone, PartialEq)]
pub struct VisualIdentity {
    /// The canvas the design is stitched on (its hoop).
    pub canvas: Rgb,
    /// What surrounds the canvas.
    pub backdrop: Rgb,
    /// Stroke width of a stitch run, in mm (a thread is roughly 0.3-0.5 mm).
    pub stitch_width_mm: f32,
}

impl Default for VisualIdentity {
    fn default() -> Self {
        Self {
            // The Java editor's look: a white canvas (255) on a mid-grey
            // backdrop (background(100), PEmbroiderEditor draw()).
            canvas: Rgb::new(255, 255, 255),
            backdrop: Rgb::new(100, 100, 100),
            stitch_width_mm: 0.4,
        }
    }
}
