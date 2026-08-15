#![forbid(unsafe_code)]
//! The single stitch model (paths, stitches, colours, transforms) + algorithms
//! (hatch, satin, trace, TSP, boolean shapes). The SSOT every frontend consumes
//! (docs/decisions/D001.md).
//!
//! Algorithms are ported from the Java suite (`PEmbroiderGraphics` and friends,
//! which are the MODEL, never a source). Float outputs are compared against
//! Java-generated fixtures with a 0.001 mm tolerance (docs/decisions/D002.md):
//! last-ulp libm differences are noise, the machine grid is 0.1 mm.

pub mod geom;
pub mod hatch;
pub mod model;
pub mod resample;
pub mod tsp;
