//! The draw-list commands: the atomic things a renderer can draw, emitted
//! from the model by [`DrawList::from_model`].

use emb_model::geom::Point;
use emb_model::model::Model;

use crate::identity::{Rgb, VisualIdentity};

/// Axis-aligned bounds in design space (mm), precomputed at emission so a
/// renderer can cull in O(1) per command.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Bounds {
    pub min_x: f32,
    pub min_y: f32,
    pub max_x: f32,
    pub max_y: f32,
}

impl Bounds {
    pub fn of_points(points: &[Point]) -> Self {
        let mut b = Self {
            min_x: f32::INFINITY,
            min_y: f32::INFINITY,
            max_x: f32::NEG_INFINITY,
            max_y: f32::NEG_INFINITY,
        };
        for p in points {
            b.min_x = b.min_x.min(p.x);
            b.min_y = b.min_y.min(p.y);
            b.max_x = b.max_x.max(p.x);
            b.max_y = b.max_y.max(p.y);
        }
        b
    }

    pub fn intersects(self, other: Bounds) -> bool {
        self.min_x <= other.max_x
            && self.max_x >= other.min_x
            && self.min_y <= other.max_y
            && self.max_y >= other.min_y
    }
}

/// One thing a renderer draws. Commands carry their bounds (derived once at
/// emission) so culling never scans the points.
#[derive(Debug, Clone, PartialEq)]
pub enum Command {
    /// The canvas: a `width` x `height` mm rectangle filled with the
    /// identity's canvas colour.
    Canvas {
        width: f32,
        height: f32,
        bounds: Bounds,
    },
    /// A run of stitches: a polyline stroked with its colour at the identity's
    /// stitch width.
    Polyline {
        points: Vec<Point>,
        color: Rgb,
        bounds: Bounds,
    },
}

/// A command plus the bounds the renderer culls with.
#[derive(Debug, Clone, PartialEq)]
pub struct DrawItem {
    pub command: Command,
    pub bounds: Bounds,
}

/// The draw list: the whole contract between the apps and the renderer.
#[derive(Debug, Clone, PartialEq)]
pub struct DrawList {
    pub identity: VisualIdentity,
    pub items: Vec<DrawItem>,
}

impl DrawList {
    /// The one emission from the model: a canvas command followed by one
    /// polyline command per model polyline, bounds derived once. Every app
    /// calls this, every renderer rasterises the same source.
    pub fn from_model(model: &Model) -> Self {
        let canvas_bounds = Bounds {
            min_x: 0.0,
            min_y: 0.0,
            max_x: model.width,
            max_y: model.height,
        };
        let mut items = vec![DrawItem {
            bounds: canvas_bounds,
            command: Command::Canvas {
                width: model.width,
                height: model.height,
                bounds: canvas_bounds,
            },
        }];
        for (points, color) in model.polylines.iter().zip(&model.colors) {
            let bounds = Bounds::of_points(points);
            items.push(DrawItem {
                bounds,
                command: Command::Polyline {
                    points: points.clone(),
                    color: Rgb::from_packed(*color),
                    bounds,
                },
            });
        }
        DrawList {
            identity: VisualIdentity::default(),
            items,
        }
    }

    /// The union of every command's bounds: the canvas rect, or the empty
    /// rect when the design has no commands.
    pub fn bounds(&self) -> Bounds {
        let mut b = Bounds {
            min_x: f32::INFINITY,
            min_y: f32::INFINITY,
            max_x: f32::NEG_INFINITY,
            max_y: f32::NEG_INFINITY,
        };
        for item in &self.items {
            b.min_x = b.min_x.min(item.bounds.min_x);
            b.min_y = b.min_y.min(item.bounds.min_y);
            b.max_x = b.max_x.max(item.bounds.max_x);
            b.max_y = b.max_y.max(item.bounds.max_y);
        }
        if b.min_x.is_infinite() {
            Bounds {
                min_x: 0.0,
                min_y: 0.0,
                max_x: 0.0,
                max_y: 0.0,
            }
        } else {
            b
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn model() -> Model {
        let mut m = Model::new(100.0, 80.0);
        m.push_polyline(vec![Point::new(1.0, 2.0), Point::new(3.0, 4.0)], 0x112233);
        m.push_polyline(vec![Point::new(5.0, 6.0), Point::new(7.0, 8.0)], 0xFF0000);
        m
    }

    #[test]
    fn emits_canvas_first() {
        let dl = DrawList::from_model(&model());
        assert!(matches!(
            dl.items[0].command,
            Command::Canvas {
                width: 100.0,
                height: 80.0,
                ..
            }
        ));
        assert_eq!(dl.items.len(), 3);
    }

    #[test]
    fn polyline_bounds_are_derived_once() {
        let dl = DrawList::from_model(&model());
        match &dl.items[1].command {
            Command::Polyline {
                points,
                color,
                bounds,
            } => {
                assert_eq!(points.len(), 2);
                assert_eq!(*color, Rgb::new(0x11, 0x22, 0x33));
                assert_eq!(dl.items[1].bounds, *bounds);
            }
            other => panic!("expected polyline, got {other:?}"),
        }
    }

    #[test]
    fn culling_uses_the_contract_bounds() {
        let dl = DrawList::from_model(&model());
        let visible = Bounds {
            min_x: 2.0,
            min_y: 2.0,
            max_x: 4.0,
            max_y: 4.0,
        };
        assert!(dl.items[1].bounds.intersects(visible));
        assert!(!dl.items[2].bounds.intersects(visible));
    }

    #[test]
    fn empty_model_emits_just_the_canvas() {
        let dl = DrawList::from_model(&Model::new(10.0, 10.0));
        assert_eq!(dl.items.len(), 1);
    }
}
