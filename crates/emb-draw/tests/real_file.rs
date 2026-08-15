//! M3 gate: the real-file load pipeline — `Model::from_pes` on the committed
//! fixture, then the shared emission `DrawList::from_model`. This is the exact
//! path the viewer app runs on "Open…" (headless: no window needed).

#![allow(clippy::expect_used, clippy::unwrap_used)]

use std::path::PathBuf;

use emb_draw::{Command, DrawList};
use emb_model::model::Model;

fn fixture_pes() -> Vec<u8> {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../fixtures/simple.pes");
    std::fs::read(&path).unwrap_or_else(|e| panic!("read {}: {e}", path.display()))
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
