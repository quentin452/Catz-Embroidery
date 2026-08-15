//! M1 gates: the writers byte-match the Java-generated fixtures (with the
//! documented deviations), and the PES reader round-trips its writer.
//!
//! The fixtures are produced by `tools/java-fixtures/GenFixtures.java` (the Java
//! suite is the model) and committed under `fixtures/`.

use std::path::PathBuf;

use emb_data::{Design, Point, dst, pes, svg};

fn fixture(name: &str) -> Vec<u8> {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../fixtures")
        .join(name);
    std::fs::read(&path).unwrap_or_else(|e| panic!("read fixture {name}: {e}"))
}

/// The exact design `GenFixtures.java` writes: a red square + a red jump + a
/// blue triangle, 100×100 mm, title "Simple Test".
fn test_design() -> Design {
    let red = 0xFF0000u32;
    let blue = 0x0000FFu32;
    let pts: &[(f32, f32)] = &[
        (10.0, 10.0),
        (90.0, 10.0),
        (90.0, 90.0),
        (10.0, 90.0),
        (10.0, 10.0),
        (10.0, 10.0),
        (30.0, 10.0),
        (30.0, 10.0),
        (60.0, 50.0),
        (20.0, 50.0),
        (30.0, 10.0),
    ];
    Design {
        bounds: [0.0, 0.0, 100.0, 100.0],
        stitches: pts.iter().map(|&(x, y)| Point { x, y }).collect(),
        colors: vec![red; 7].into_iter().chain(vec![blue; 4]).collect(),
        jumps: vec![
            false, false, false, false, false, true, false, false, false, false, false,
        ],
        title: "Simple Test".into(),
    }
}

#[test]
fn dst_matches_java_fixture_except_the_known_end_record() {
    let java = fixture("simple.dst");
    let got = dst::write(&test_design()).unwrap();
    // Deviation, documented in docs/LESSONS.md: the Java writer encodes the END
    // record but never writes it (PEmbroiderWriter.java:206-207 closes the
    // stream right after). The Rust writer writes it — the fixture plus the END
    // record is the expected output.
    let mut expected = java;
    expected.extend_from_slice(&[0x00, 0x00, 0xF3]);
    assert_eq!(got, expected);
}

#[test]
fn svg_matches_java_fixture() {
    let java = String::from_utf8(fixture("simple.svg")).unwrap();
    let got = svg::write(&test_design());
    assert_eq!(got, java);
}

#[test]
fn pes_matches_java_fixture() {
    let java = fixture("simple.pes");
    let got = pes::write(&test_design()).unwrap();
    assert_eq!(got, java);
}

/// What the PES writer's first-stitch quirk yields on a read-back: the long-form
/// first stitch plus a redundant short (0,0) record plus the two spare 0x00
/// bytes the Java writes (read back as a second short (0,0)). The first stitch
/// therefore reads back as three identical points. docs/LESSONS.md.
fn read_back_positions() -> Vec<(f32, f32)> {
    vec![
        (10.0, 10.0),
        (10.0, 10.0),
        (10.0, 10.0),
        (90.0, 10.0),
        (90.0, 90.0),
        (10.0, 90.0),
        (10.0, 10.0),
        (10.0, 10.0),
        (30.0, 10.0),
        (30.0, 10.0),
        (60.0, 50.0),
        (20.0, 50.0),
        (30.0, 10.0),
    ]
}

#[test]
fn pes_reads_the_java_fixture() {
    let read = pes::read(&fixture("simple.pes")).unwrap();
    assert_eq!(read.title, "Simple Test");
    assert_eq!(read.bounds, [0.0, 0.0, 100.0, 100.0]);
    let positions: Vec<(f32, f32)> = read.stitches.iter().map(|p| (p.x, p.y)).collect();
    assert_eq!(positions, read_back_positions());
    assert_read_back_colors(&read);
}

/// The read-back design's colours per point (red ×9, blue ×4 — the extra points
/// are the first-stitch quirk, so the indices do not match the source design).
fn assert_read_back_colors(read: &emb_data::Design) {
    let expected: Vec<u32> = vec![0xFF0000; 9]
        .into_iter()
        .chain(vec![0x0000FF; 4])
        .collect();
    assert_eq!(read.colors.len(), expected.len());
    // The writer quantises through the PEC palette, so compare palette indices.
    for (got, want) in read.colors.iter().zip(&expected) {
        assert_eq!(pes::find_color(*got), pes::find_color(*want));
    }
}

#[test]
fn pes_roundtrips_its_writer() {
    let written = pes::write(&test_design()).unwrap();
    let read = pes::read(&written).unwrap();
    let positions: Vec<(f32, f32)> = read.stitches.iter().map(|p| (p.x, p.y)).collect();
    assert_eq!(positions, read_back_positions());
    assert_read_back_colors(&read);
}
