//! The stitch model: the SSOT every frontend consumes.
//!
//! Invariant (mirrors the Java `PEmbroiderGraphics` de-facto invariant):
//! `colors[i]` is the colour of `polylines[i]`, and the two are always the
//! same length.

use crate::geom::Point;
use crate::tsp;
use emb_data::Design;

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

    /// Build a `Model` from a format-level `Design`: group the flat stitch
    /// stream into polylines, one run of consecutive same-colour stitches per
    /// polyline, split on colour change and on jump (the Java SVG writer's
    /// rule, `PEmbroiderWriter.SVG.svgString`: a new path starts when the
    /// colour changes or the stitch jumps).
    ///
    /// The canvas is `width x height` mm taken from the design bounds; the
    /// polylines keep their absolute coordinates (a design can sit anywhere
    /// on its canvas).
    pub fn from_design(design: &Design) -> Self {
        let width = design.bounds[2] - design.bounds[0];
        let height = design.bounds[3] - design.bounds[1];
        let mut model = Model::new(width, height);
        let mut run: Vec<Point> = Vec::new();
        let mut run_color: u32 = 0;
        for (i, p) in design.stitches.iter().enumerate() {
            let color = design.colors[i];
            let jumps = design.jumps.get(i).copied().unwrap_or(false);
            if !run.is_empty() && (jumps || color != run_color) {
                model.push_polyline(std::mem::take(&mut run), run_color);
            }
            run_color = color;
            run.push(Point::new(p.x, p.y));
        }
        if !run.is_empty() {
            model.push_polyline(run, run_color);
        }
        model
    }

    /// Load a PES file from bytes: `emb_data::pes::read` + [`Self::from_design`].
    /// The single entry point every app uses to open a design.
    pub fn from_pes(bytes: &[u8]) -> Result<Self, emb_data::Error> {
        let design = emb_data::pes::read(bytes)?;
        Ok(Self::from_design(&design))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use emb_data::Design;

    fn design_with(stitches: Vec<(f32, f32)>, colors: Vec<u32>, jumps: Vec<bool>) -> Design {
        Design {
            bounds: [0.0, 0.0, 100.0, 100.0],
            stitches: stitches
                .into_iter()
                .map(|(x, y)| emb_data::Point { x, y })
                .collect(),
            colors,
            jumps,
            title: "test".into(),
        }
    }

    #[test]
    fn groups_consecutive_same_colour_stitches() {
        let d = design_with(
            vec![(0.0, 0.0), (1.0, 0.0), (2.0, 0.0), (3.0, 0.0), (4.0, 0.0)],
            vec![0x111111, 0x111111, 0x222222, 0x222222, 0x222222],
            vec![],
        );
        let m = Model::from_design(&d);
        assert_eq!(m.polylines.len(), 2);
        assert_eq!(m.colors, vec![0x111111, 0x222222]);
        assert_eq!(m.polylines[0].len(), 2);
        assert_eq!(m.polylines[1].len(), 3);
    }

    #[test]
    fn splits_on_jump() {
        let d = design_with(
            vec![(0.0, 0.0), (1.0, 0.0), (10.0, 0.0), (11.0, 0.0)],
            vec![0x111111; 4],
            vec![false, false, true, false],
        );
        let m = Model::from_design(&d);
        assert_eq!(m.polylines.len(), 2);
        assert_eq!(m.polylines[0].len(), 2);
        assert_eq!(m.polylines[1].len(), 2);
    }

    #[test]
    fn empty_design_is_empty_model() {
        let d = design_with(vec![], vec![], vec![]);
        let m = Model::from_design(&d);
        assert!(m.polylines.is_empty());
        assert_eq!((m.width, m.height), (100.0, 100.0));
    }
}
