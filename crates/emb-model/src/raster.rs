//! A binary raster mask: what the trace and the raster hatches read.
//!
//! Pixels are `true` inside the mask. The Java suite's equivalent is a
//! thresholded PImage (pixels > 0x7F red channel); `get` out of bounds returns
//! false — the vendored `PImage.get` returns 0 for out-of-range coordinates
//! (measured, docs/LESSONS.md).

use crate::Error;

#[derive(Debug, Clone, PartialEq)]
pub struct Raster {
    pub width: usize,
    pub height: usize,
    pixels: Vec<bool>,
}

impl Raster {
    pub fn new(width: usize, height: usize, pixels: Vec<bool>) -> Result<Self, Error> {
        if pixels.len() != width * height {
            return Err(Error::RasterSizeMismatch {
                width,
                height,
                found: pixels.len(),
            });
        }
        Ok(Self {
            width,
            height,
            pixels,
        })
    }

    /// The Java `PImage.get(x, y)` on a thresholded mask: true inside the
    /// mask, false outside the image bounds.
    pub fn get(&self, x: i32, y: i32) -> bool {
        if x < 0 || y < 0 || x >= self.width as i32 || y >= self.height as i32 {
            return false;
        }
        self.pixels[y as usize * self.width + x as usize]
    }

    pub fn pixels(&self) -> &[bool] {
        &self.pixels
    }
}
