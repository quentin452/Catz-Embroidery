#![forbid(unsafe_code)]
//! The single stitch model (paths, stitches, colours, transforms) + algorithms
//! (hatch, satin, trace, TSP, boolean shapes). The SSOT every frontend consumes
//! (docs/decisions/D001.md).
//!
//! Algorithms are ported from the Java suite (`PEmbroiderGraphics` and friends,
//! which are the MODEL, never a source). Float outputs are compared against
//! Java-generated fixtures with a 0.001 mm tolerance (docs/decisions/D002.md):
//! last-ulp libm differences are noise, the machine grid is 0.1 mm.

pub mod convert;
pub mod geom;
pub mod hatch;
pub mod hatch_raster;
pub mod model;
pub mod raster;
pub mod resample;
pub mod stroke;
pub mod trace;
pub mod tsp;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("resample randomize != 0 is not ported (no consumer demands it; ROADMAP)")]
    ResampleRandomizeNotPorted,
    #[error(
        "PERLIN hatch is not ported (D003/D005): the Java walks app.noise through a Java2D raster; refuses until a consumer demands it"
    )]
    PerlinNotPorted,
    #[error("raster pixel count {found} does not match {width}x{height}")]
    RasterSizeMismatch {
        width: usize,
        height: usize,
        found: usize,
    },
    #[error("contour tracing failed: {0}")]
    Trace(String),
}
