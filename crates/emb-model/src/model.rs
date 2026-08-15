//! The stitch model: the SSOT every frontend consumes.
//!
//! Invariant (mirrors the Java `PEmbroiderGraphics` de-facto invariant):
//! `colors[i]` is the colour of `polylines[i]`, and the two are always the
//! same length.

use crate::geom::Point;
use crate::tsp;

/// One design: a list of polylines with one colour each, on a width x height
/// canvas (mm).
#[derive(Debug, Clone, PartialEq)]
pub struct Model {
    pub polylines: Vec<Vec<Point>>,
    pub colors: Vec<u32>,
    pub width: f32,
    pub height: f32,
}

impl Model {
    pub fn new(width: f32, height: f32) -> Self {
        Self {
            polylines: Vec::new(),
            colors: Vec::new(),
            width,
            height,
        }
    }

    /// `PEmbroiderGraphics.pushPolyline` without the matrix stack or the
    /// multicolor colour-expansion modes (no consumer uses them yet; they are
    /// queued in docs/ROADMAP.md).
    pub fn push_polyline(&mut self, poly: Vec<Point>, color: u32) {
        self.polylines.push(poly);
        self.colors.push(color);
    }

    /// `PEmbroiderGraphics.optimize()`: reorder each colour block with the TSP
    /// solver (5 trials, 999 iterations — the Java's defaults).
    pub fn optimize(&mut self) {
        self.optimize_with(5, 999);
    }

    /// `PEmbroiderGraphics.optimize(trials, maxIter)`: the TSP reorders each
    /// run of equal colours independently.
    pub fn optimize_with(&mut self, trials: usize, max_iter: usize) {
        if self.polylines.len() <= 2 {
            return;
        }
        let mut idx0 = 0;
        let mut i = 1;
        while i <= self.polylines.len() {
            if i == self.polylines.len() || self.colors[i] != self.colors[i - 1] {
                let block = self.polylines[idx0..i].to_vec();
                let block_colors = self.colors[idx0..i].to_vec();
                let solved = if block.len() > 2 {
                    tsp::solve(&block, trials, max_iter)
                } else {
                    block
                };
                self.polylines.splice(idx0..i, solved);
                self.colors.splice(idx0..i, block_colors);
                idx0 = i;
            }
            i += 1;
        }
    }
}
