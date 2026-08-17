//! M3 gate: the real-file load pipeline — `Model::from_pes` / `Model::from_svg`
//! on the committed fixtures, then the shared emission `DrawList::from_model`.
//! This is the exact path the viewer app runs on "Open…" (headless: no window
//! needed).

#![allow(clippy::expect_used, clippy::unwrap_used)]

use std::path::PathBuf;

use emb_draw::{Command, DrawList};
use emb_model::model::Model;

fn fixture_pes() -> Vec<u8> {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../fixtures/simple.pes");
    std::fs::read(&path).unwrap_or_else(|e| panic!("read {}: {e}", path.display()))
}

fn fixture_svg() -> String {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../fixtures/simple.svg");
    std::fs::read_to_string(&path).unwrap_or_else(|e| panic!("read {}: {e}", path.display()))
}

#[test]
fn real_pes_becomes_a_draw_list() {
    let bytes = fixture_pes();
    let model = Model::from_pes(&bytes).expect("fixture must parse");
    assert!(!model.polylines.is_empty(), "fixture must carry polylines");

    let dl = DrawList::from_model(&model);
    assert!(matches!(dl.items[0].command, Command::Canvas { .. }));

    let polylines = dl
        .items
        .iter()
        .filter(|i| matches!(i.command, Command::Polyline { .. }))
        .count();
    assert_eq!(polylines, model.polylines.len());

    let bounds = dl.bounds();
    assert!(bounds.min_x.is_finite() && bounds.max_y.is_finite());
    assert!(bounds.max_x > bounds.min_x || bounds.max_y > bounds.min_y);
}

/// The SVG viewer path (gap #15): `Model::from_svg` on the committed
/// `simple.svg` (written by our own writer — the round-trip the reader must
/// pass), then the same DrawList emission as PES. This is exactly what the
/// viewer runs when opening a .svg.
#[test]
fn real_svg_becomes_a_draw_list() {
    let svg = fixture_svg();
    let model = Model::from_svg(&svg).expect("the svg fixture must parse");
    assert!(
        !model.polylines.is_empty(),
        "the svg fixture must carry polylines"
    );

    let dl = DrawList::from_model(&model);
    let polylines = dl
        .items
        .iter()
        .filter(|i| matches!(i.command, Command::Polyline { .. }))
        .count();
    assert_eq!(polylines, model.polylines.len());

    let bounds = dl.bounds();
    assert!(bounds.min_x.is_finite() && bounds.max_y.is_finite());
    // The writer emits a single stitch per M/L, so the round-trip keeps the
    // fixture's polyline count and its extent.
    assert!(bounds.max_x > bounds.min_x || bounds.max_y > bounds.min_y);
}
