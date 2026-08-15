//! M2 gates: the emb-model algorithms reproduce the Java-generated fixtures
//! within the 0.001 mm tolerance ruled in docs/decisions/D002.md. The fixtures
//! are produced headlessly by `tools/java-fixtures/GenModelFixtures.java`.
//!
//! `expect`/`unwrap` here: this whole file is a test — a failure IS the report
//! (clippy.toml's allow-expect-in-tests does not cover integration tests).

#![allow(clippy::expect_used, clippy::unwrap_used)]

use std::path::PathBuf;

use emb_model::geom::Point;
use emb_model::raster::Raster;
use emb_model::{hatch, hatch_raster, model::Model, resample, trace, tsp};

/// The D002 tolerance: 0.001 mm, 100x finer than the 0.1 mm machine grid.
const TOLERANCE_MM: f32 = 0.001;

fn fixture_polylines(name: &str) -> Vec<Vec<Point>> {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../fixtures/model")
        .join(name);
    let text = std::fs::read_to_string(&path).unwrap_or_else(|e| panic!("read {name}: {e}"));
    parse_polylines(&text)
}

fn parse_polylines(text: &str) -> Vec<Vec<Point>> {
    let mut lines = text.lines();
    let first = lines.next().expect("first header line");
    if let Some(rest) = first.strip_prefix("L ") {
        let n: usize = rest.parse().expect("polyline count");
        let mut out = Vec::with_capacity(n);
        for _ in 0..n {
            out.push(parse_polyline(&mut lines));
        }
        out
    } else {
        // Single polyline fixture ("P n" first).
        let n: usize = first
            .strip_prefix("P ")
            .expect("point header")
            .parse()
            .expect("point count");
        vec![parse_points(&mut lines, n)]
    }
}

fn parse_polyline(lines: &mut std::str::Lines) -> Vec<Point> {
    let header = lines.next().expect("P header");
    let n: usize = header
        .strip_prefix("P ")
        .expect("point header")
        .parse()
        .expect("point count");
    parse_points(lines, n)
}

fn parse_points(lines: &mut std::str::Lines, n: usize) -> Vec<Point> {
    let mut poly = Vec::with_capacity(n);
    for _ in 0..n {
        let line = lines.next().expect("point line");
        let mut it = line.split_whitespace();
        let x: f32 = it.next().expect("x").parse().expect("x float");
        let y: f32 = it.next().expect("y").parse().expect("y float");
        poly.push(Point::new(x, y));
    }
    poly
}

fn assert_polylines_close(actual: &[Vec<Point>], expected: &[Vec<Point>], tolerance: f32) {
    assert_eq!(actual.len(), expected.len(), "polyline count");
    for (a, e) in actual.iter().zip(expected) {
        assert_eq!(a.len(), e.len(), "point count");
        for (pa, pe) in a.iter().zip(e) {
            assert!(
                (pa.x - pe.x).abs() < tolerance && (pa.y - pe.y).abs() < tolerance,
                "point {pa:?} vs expected {pe:?} (tolerance {tolerance} mm)"
            );
        }
    }
}

/// The concave L-shape the Java generator hatches.
fn l_shape() -> Vec<Point> {
    let pts = [
        (10.0, 10.0),
        (90.0, 10.0),
        (90.0, 60.0),
        (50.0, 60.0),
        (50.0, 90.0),
        (10.0, 90.0),
    ];
    pts.iter().map(|&(x, y)| Point::new(x, y)).collect()
}

/// The zigzag polyline the Java generator resamples.
fn zigzag() -> Vec<Point> {
    let pts = [
        (0.0, 0.0),
        (3.0, 0.0),
        (3.5, 1.0),
        (15.0, 1.0),
        (15.0, 5.0),
        (60.0, 5.0),
        (60.0, 6.0),
        (120.0, 6.0),
    ];
    pts.iter().map(|&(x, y)| Point::new(x, y)).collect()
}

/// The six scattered segments the Java generator TSPs.
fn tsp_segments() -> Vec<Vec<Point>> {
    let segs = [
        [(10.0, 10.0), (30.0, 10.0)],
        [(25.0, 40.0), (45.0, 35.0)],
        [(5.0, 60.0), (8.0, 70.0)],
        [(70.0, 20.0), (60.0, 30.0)],
        [(80.0, 80.0), (90.0, 75.0)],
        [(50.0, 55.0), (55.0, 60.0)],
    ];
    segs.iter()
        .map(|s| s.iter().map(|&(x, y)| Point::new(x, y)).collect::<Vec<_>>())
        .collect()
}

#[test]
fn hatch_parallel_45_degrees() {
    let expected = fixture_polylines("hatch45.txt");
    let got = hatch::hatch_parallel(&l_shape(), std::f32::consts::PI / 4.0, 4.0);
    assert_polylines_close(&got, &expected, TOLERANCE_MM);
}

#[test]
fn hatch_parallel_arbitrary_angle() {
    let expected = fixture_polylines("hatch03.txt");
    let got = hatch::hatch_parallel(&l_shape(), 0.3, 5.0);
    assert_polylines_close(&got, &expected, TOLERANCE_MM);
}

#[test]
fn resample_merges_and_splits() {
    let expected = fixture_polylines("resample.txt");
    let got = resample::resample(&zigzag(), 4.0, 10.0, 0.0, 0.0).expect("deterministic resample");
    assert_polylines_close(&[got], &expected, TOLERANCE_MM);
}

#[test]
fn resample_refuses_the_unported_randomize_path() {
    assert!(resample::resample(&zigzag(), 4.0, 10.0, 0.5, 0.0).is_err());
}

#[test]
fn tsp_reorders_with_trials_3() {
    let expected = fixture_polylines("tsp.txt");
    let got = tsp::solve(&tsp_segments(), 3, 999);
    assert_polylines_close(&got, &expected, TOLERANCE_MM);
}

#[test]
fn model_optimize_orders_one_colour_block() {
    // All six segments share a colour: optimize groups them into one TSP block,
    // which must match the standalone solve fixture.
    let mut m = Model::new(100.0, 100.0);
    for seg in tsp_segments() {
        m.push_polyline(seg, 0xFF0000);
    }
    m.optimize_with(3, 999);
    let expected = fixture_polylines("tsp.txt");
    assert_polylines_close(&m.polylines, &expected, TOLERANCE_MM);
    assert_eq!(m.colors.len(), m.polylines.len());
}

/// The 40x40 binary circle the Java generator traces and hatches (white inside,
/// radius 15 at (19.5, 19.5)).
fn circle_mask() -> Raster {
    let (w, h) = (40usize, 40usize);
    let mut px = Vec::with_capacity(w * h);
    for y in 0..h {
        for x in 0..w {
            let dx = x as f32 - 19.5;
            let dy = y as f32 - 19.5;
            px.push(dx * dx + dy * dy <= 15.0 * 15.0);
        }
    }
    Raster::new(w, h, px).expect("40x40 pixels")
}

#[test]
fn find_contours_traces_the_circle() {
    let expected = fixture_polylines("findcontours.txt");
    let got = trace::find_contours(&circle_mask()).expect("contours");
    assert_polylines_close(&got, &expected, TOLERANCE_MM);
}

#[test]
fn approx_poly_dp_simplifies_the_contour() {
    let expected = fixture_polylines("approxpolydp.txt");
    let contours = trace::find_contours(&circle_mask()).expect("contours");
    let got = trace::approx_poly_dp(&contours[0], 1.0);
    assert_polylines_close(&[got], &expected, TOLERANCE_MM);
}

#[test]
fn hatch_parallel_raster_matches() {
    let expected = fixture_polylines("hatchraster.txt");
    let got =
        hatch_raster::hatch_parallel_raster(&circle_mask(), std::f32::consts::PI / 4.0, 4.0, 1.0);
    assert_polylines_close(&got, &expected, TOLERANCE_MM);
}

#[test]
fn cross_mode_matches_the_converter_path() {
    let expected = fixture_polylines("cross.txt");
    let got = hatch_raster::hatch_cross_raster(
        &circle_mask(),
        std::f32::consts::PI / 4.0,
        4.0,
        10.0,
        0.5,
    )
    .expect("cross hatch");
    assert_polylines_close(&got, &expected, TOLERANCE_MM);
}

#[test]
fn isolines_match_the_concentric_path() {
    let expected = fixture_polylines("isolines.txt");
    let got = hatch::isolines(&circle_mask(), 4.0).expect("isolines");
    assert_polylines_close(&got, &expected, TOLERANCE_MM);
}
