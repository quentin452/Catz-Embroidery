#![forbid(unsafe_code)]
//! The shared preview draw-list vocabulary (commands + visual identity): the
//! contract between the apps and the renderer. One source, renderers can't
//! drift (docs/decisions/D001.md, docs/LESSONS.md).
//!
//! An app emits a [`DrawList`] from the model; every renderer — the live
//! viewport, the overview texture cache — rasterises the same list. A command
//! carries its bounds, computed once at emission, so viewport culling is
//! O(visible) per frame instead of O(design).

pub mod identity;
pub mod list;
pub mod viewport;

pub use identity::{Rgb, VisualIdentity};
pub use list::{Bounds, Command, DrawItem, DrawList};
pub use viewport::Viewport;
