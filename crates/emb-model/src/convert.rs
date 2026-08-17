//! The converter pipeline: a raster image becomes a stitched [`Model`] — the
//! port of the Java converter's `processImageWithProgress` driving the
//! `PEmbroiderGraphics.image()` gate (PEmbroiderGraphics.java:4849). The app
//! decodes and resizes the image (the Java's `img.resize(1000, 1000)`);
//! everything from the binarization on lives here so the pipeline is testable
//! headlessly.
//!
//! Deviations, ruled in D005 (each replaces an `app.random` the Java rolls,
//! which a fixture could never pin down):
//! - the MultiColor palette is a fixed deterministic palette, cycled per
//!   pushed polyline (the Java generates maxColors random colors per run);
//! - the Realistic per-segment color stream is read back by the Java's own
//!   writer as a per-polyline flat list (a latent mismatch,
//!   PEmbroiderWriter.java:2432 reads `colors.get(i)` per polyline); the port
//!   assigns each polyline the extracted palette's midpoint color instead;
//! - the Realistic resample skips the Java's first-segment random offset
//!   (`app.random`, PEmbroiderGraphics.java:3201) — `resample` refuses
//!   `randomize_offset != 0` (the same ruling as RESAMPLE_NOISE).
//!
//! PERLIN refuses with an error (D003, D005): the mode walks `app.noise`
//! through a Java2D-rasterized mask; its Java output is unreproducible
//! anyway.

use crate::Error;
use crate::geom::Point;
use crate::hatch;
use crate::hatch_raster;
use crate::model::Model;
use crate::raster::Raster;
use crate::resample::{self, StitchSettings};
use crate::stroke::stroke_poly_normal;
use crate::trace;

const HALF_PI: f32 = std::f32::consts::FRAC_PI_2;

/// The Java's `HATCH_ANGLE` default: QUARTER_PI.
const HATCH_ANGLE: f32 = std::f32::consts::FRAC_PI_4;

/// The Java's `PARALLEL_RESAMPLING_OFFSET_FACTOR`.
const RESAMPLING_OFFSET_FACTOR: f32 = 0.5;

/// The Java's `PImage.filter(THRESHOLD)` bound: `max(r,g,b) < 127` → off
/// (`(int)(0.5*255)`, PImage.java:1175), then `findContours` reads
/// `red > 0x7F` (PEmbroiderTrace.java:372) — together `on` iff
/// `max(r,g,b) >= 127`.
const THRESHOLD: u8 = 127;

/// The deterministic MultiColor palette (D005): the Java's `app.random`
/// colors, replaced by a fixed set — the same ruling the editor applied to
/// its new-layer colors.
const PALETTE: [u32; 16] = [
    0xE6194B, 0x3CB44B, 0xFFE119, 0x4363D8, 0xF58231, 0x911EB4, 0x46F0F0, 0xF032E6, 0xBCF60C,
    0xFABEBE, 0x008080, 0xE6BEFF, 0x9A6324, 0xFFFAC8, 0x800000, 0xAAFFC3,
];

/// The converter's hatch modes: the Java's `HATCH_MODE` constants
/// (PEmbroiderGraphics.java:55-59). PERLIN is a named exception (D003/D005)
/// and refuses with an error — the only mode whose Java output is
/// random-seeded.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HatchMode {
    Cross,
    Parallel,
    Concentric,
    Spiral,
    Perlin,
}

/// The converter's color modes: the Java's `ColorType` enum
/// (Main.java:59-63).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ColorMode {
    MultiColor,
    BlackAndWhite,
    Realistic,
}

/// The converter's knobs — the Java's ControlP5 fields (Main.java:40-58),
/// with the same defaults.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ConvertParams {
    pub hatch_mode: HatchMode,
    pub color_mode: ColorMode,
    /// The FillB toggle: false = outline-only (the default), true = outline
    /// + hatch fill.
    pub fill: bool,
    /// Stitch spacing in px: the Java's `stitchSpacing`, applied to both
    /// `hatchSpacing` and `strokeSpacing`.
    pub spacing: f32,
    /// Stroke weight in px: the Java's `strokeWeight` (clamped to >= 1).
    pub stroke_weight: f32,
    /// Palette size: the Java's `maxColors` (clamped to >= 1).
    pub max_colors: usize,
    /// Invert the binarization (Rust-only knob, 2026-08-17 — the Java has
    /// none): ON means dark pixels (`max(r,g,b) < 127`) instead of bright.
    /// For dark-subject-on-bright-background photos, where the parity mask
    /// converts the background blob. Recorded in ROADMAP.
    pub invert: bool,
}

impl Default for ConvertParams {
    fn default() -> Self {
        Self {
            hatch_mode: HatchMode::Cross,
            color_mode: ColorMode::MultiColor,
            fill: false,
            spacing: 10.0,
            stroke_weight: 25.0,
            max_colors: 10,
            invert: false,
        }
    }
}

/// The Java's `calcAxisAngleForParallel`: HALF_PI - ang.
fn calc_axis_angle(ang: f32) -> f32 {
    HALF_PI - ang
}

/// `PImage.filter(THRESHOLD)` + `findContours`' `red > 0x7F`, fused: a pixel
/// is in the mask iff its brightest channel is at or above the threshold.
/// `invert` (the Rust-only knob) flips the comparison to `<`, so the mask
/// becomes the dark pixels.
fn binarize(pixels: &[u8], width: usize, height: usize, invert: bool) -> Result<Raster, Error> {
    let on: Vec<bool> = pixels
        .chunks_exact(4)
        .map(|px| {
            let bright = px[0].max(px[1]).max(px[2]) >= THRESHOLD;
            if invert { !bright } else { bright }
        })
        .collect();
    Raster::new(width, height, on)
}

/// `PEmbroiderGraphics.extractColorsFromImage`: the exact-RGB histogram's
/// most frequent colors, capped at `max_colors` (alpha ignored, as the
/// Java's `& 0x00FFFFFF`). Ties keep first-seen order — the Java's
/// HashMap-bucket order is equally arbitrary (cosmetic, D005).
fn extract_colors(pixels: &[u8], max_colors: usize) -> Vec<u32> {
    use std::collections::HashMap;
    let mut freq: HashMap<u32, u32> = HashMap::new();
    for px in pixels.chunks_exact(4) {
        let rgb = ((px[0] as u32) << 16) | ((px[1] as u32) << 8) | px[2] as u32;
        *freq.entry(rgb).or_insert(0) += 1;
    }
    let mut entries: Vec<(u32, u32)> = freq.into_iter().collect();
    entries.sort_by(|a, b| b.1.cmp(&a.1));
    entries.truncate(max_colors);
    entries.into_iter().map(|(c, _)| c).collect()
}

/// The per-polyline color for push index `j`, mirroring the Java's
/// pushPolyline color modes (D005 for the two deviations):
/// - BlackAndWhite: the stroke/fill color (black);
/// - MultiColor: the palette cycled per pushed polyline;
/// - Realistic: the extracted palette's midpoint color of this polyline's
///   segment range (the Java colors per segment but its writer reads one
///   color per polyline — the port keeps the gradient at polyline
///   granularity).
fn color_for(j: usize, poly_len: usize, mode: ColorMode, extracted: &[u32]) -> u32 {
    match mode {
        ColorMode::BlackAndWhite => 0x000000,
        ColorMode::MultiColor => PALETTE[j % PALETTE.len()],
        ColorMode::Realistic => {
            if extracted.is_empty() {
                0x000000
            } else {
                let total = poly_len.saturating_sub(1).max(1);
                extracted[((total / 2) * extracted.len()) / total]
            }
        }
    }
}

/// The Java's pushPolyline: in Realistic mode every pushed polyline is
/// resampled to the stitch length (MIN 4 / MAX 10) before it lands in the
/// model. The resample cannot fail here — its only error is the refused
/// `randomize != 0`, and the Java's first-segment random offset is dropped
/// by D005, so the deterministic core runs.
fn push_resampled(model: &mut Model, poly: Vec<Point>, color: u32, mode: ColorMode) {
    if mode == ColorMode::Realistic {
        let settings = StitchSettings::default();
        let resampled = resample::resample(
            &poly,
            settings.min_stitch_length,
            settings.stitch_length,
            0.0,
            0.0,
        )
        .unwrap_or(poly);
        model.push_polyline(resampled, color);
    } else {
        model.push_polyline(poly, color);
    }
}

/// The whole converter pipeline: binarize → contours → (fill → stroke) →
/// [`Model`] on a `width` x `height` canvas (the pipeline's work space — the
/// app resizes to 1000x1000, the Java's `img.resize(1000, 1000)`).
///
/// Order mirrors the Java's `image()` with `FIRST_STROKE_THEN_FILL = false`
/// (its default, Main.java never sets it): the fill polylines first, then
/// the PERPENDICULAR stroke of the contours on top. The `(860, 70)` canvas
/// offset is not ported — the writer centres the bounds at export, and the
/// offset cancels out (docs/decisions/D005.md).
///
/// This variant reports a 0..1 fraction through `progress`: binarize,
/// contours, fill, then per-contour through the stroke (the long pole at
/// high stroke_weight). The converter app runs it on a background thread and
/// feeds a progress bar (the Java's `processImageWithProgress`);
/// [`convert_image`] is the no-op-progress form.
pub fn convert_image_with_progress(
    pixels: &[u8],
    width: usize,
    height: usize,
    params: &ConvertParams,
    progress: &mut dyn FnMut(f32),
) -> Result<Model, Error> {
    if pixels.len() != width * height * 4 {
        return Err(Error::RasterSizeMismatch {
            width,
            height,
            found: pixels.len(),
        });
    }
    let mask = binarize(pixels, width, height, params.invert)?;
    progress(0.15);
    // The Java clamps the setters: STROKE_SPACING/HATCH_SPACING >= 0.1,
    // STROKE_WEIGHT >= 1 (PEmbroiderGraphics.java:306, 489, 499).
    let spacing = params.spacing.max(0.1);
    let stroke_weight = params.stroke_weight.max(1.0);

    // `image()`: findContours → drop < 3 points → approxPolyDP(1).
    let mut contours = trace::find_contours(&mask)?;
    contours.retain(|c| c.len() >= 3);
    let contours: Vec<Vec<Point>> = contours
        .iter()
        .map(|c| trace::approx_poly_dp(c, 1.0))
        .collect();
    progress(0.3);

    let extracted = if params.color_mode == ColorMode::Realistic {
        extract_colors(pixels, params.max_colors)
    } else {
        Vec::new()
    };

    let mut model = Model::new(width as f32, height as f32);
    let mut j = 0usize;

    if params.fill {
        // `hatchRaster`: the effective angle after calcAxisAngleForParallel,
        // the stitch length divided by getCurrentScale(ang + HALF_PI) — the
        // converter's matrix stack is a translate only, so the scale is 1
        // and the length is STITCH_LENGTH (10).
        let ang = calc_axis_angle(HATCH_ANGLE);
        let len = StitchSettings::default().stitch_length;
        let fill_polys: Vec<Vec<Point>> = match params.hatch_mode {
            HatchMode::Parallel => {
                let base = hatch_raster::hatch_parallel_raster(&mask, ang, spacing, 1.0);
                hatch_raster::resample_cross_intersection(
                    &base,
                    ang,
                    spacing,
                    len,
                    RESAMPLING_OFFSET_FACTOR,
                    0.0,
                )?
            }
            HatchMode::Cross => hatch_raster::hatch_cross_raster(
                &mask,
                ang,
                spacing,
                len,
                RESAMPLING_OFFSET_FACTOR,
            )?,
            HatchMode::Concentric | HatchMode::Spiral => hatch::isolines(&mask, spacing)?,
            HatchMode::Perlin => return Err(Error::PerlinNotPorted),
        };
        for poly in fill_polys {
            let color = color_for(j, poly.len(), params.color_mode, &extracted);
            push_resampled(&mut model, poly, color, params.color_mode);
            j += 1;
        }
    }
    progress(0.45);

    // `_stroke(polys, close=true)` (the converter strokes, so the fill-only
    // CONCENTRIC branch of `image()` never fires). Thin strokes (weight <= 1)
    // are pushed as the closed polyline; thicker ones become the
    // PERPENDICULAR bars (D004). The stroke is the long pole — per-contour
    // progress, so the bar moves even when the first contour is huge.
    let total = contours.len().max(1);
    for (i, poly) in contours.iter().enumerate() {
        if stroke_weight <= 1.0 {
            let mut thin = poly.clone();
            if let Some(&first) = thin.first() {
                thin.push(first);
            }
            let color = color_for(j, thin.len(), params.color_mode, &extracted);
            push_resampled(&mut model, thin, color, params.color_mode);
            j += 1;
        } else {
            let bars = stroke_poly_normal(poly, stroke_weight / 2.0, spacing, true, true);
            for bar in bars {
                let color = color_for(j, bar.len(), params.color_mode, &extracted);
                push_resampled(&mut model, bar, color, params.color_mode);
                j += 1;
            }
        }
        progress(0.45 + 0.55 * (i as f32 + 1.0) / total as f32);
    }
    progress(1.0);

    Ok(model)
}

/// The whole converter pipeline with no progress reporting — the tests and
/// any caller that doesn't need the bar.
pub fn convert_image(
    pixels: &[u8],
    width: usize,
    height: usize,
    params: &ConvertParams,
) -> Result<Model, Error> {
    convert_image_with_progress(pixels, width, height, params, &mut |_| {})
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::geom::Point;

    fn solid(w: usize, h: usize, (r, g, b): (u8, u8, u8)) -> Vec<u8> {
        let mut px = Vec::with_capacity(w * h * 4);
        for _ in 0..w * h {
            px.extend_from_slice(&[r, g, b, 255]);
        }
        px
    }

    /// Black background with a white square [x0..x1) x [y0..y1).
    fn square_image(size: usize, x0: usize, y0: usize, x1: usize, y1: usize) -> Vec<u8> {
        let mut px = solid(size, size, (0, 0, 0));
        for y in y0..y1 {
            for x in x0..x1 {
                let i = (y * size + x) * 4;
                px[i] = 255;
                px[i + 1] = 255;
                px[i + 2] = 255;
            }
        }
        px
    }

    /// Black background with a white filled circle (inclusive bounds).
    fn circle_image(size: usize, cx: f32, cy: f32, radius: f32) -> Vec<u8> {
        let mut px = solid(size, size, (0, 0, 0));
        for y in 0..size {
            for x in 0..size {
                let dx = x as f32 + 0.5 - cx;
                let dy = y as f32 + 0.5 - cy;
                if dx * dx + dy * dy <= radius * radius {
                    let i = (y * size + x) * 4;
                    px[i] = 255;
                    px[i + 1] = 255;
                    px[i + 2] = 255;
                }
            }
        }
        px
    }

    fn in_bounds(polys: &[Vec<Point>], size: usize) -> bool {
        polys.iter().flatten().all(|p| {
            p.x >= -1.0 && p.x <= size as f32 + 1.0 && p.y >= -1.0 && p.y <= size as f32 + 1.0
        })
    }

    #[test]
    fn binarizes_bright_regions() {
        let px = square_image(100, 40, 30, 70, 60);
        let mask = binarize(&px, 100, 100, false).unwrap();
        assert!(mask.get(50, 45));
        assert!(!mask.get(10, 10));
        assert!(!mask.get(-1, 0));
    }

    #[test]
    fn invert_flips_the_mask() {
        // White square on black: the parity mask is the square, the inverted
        // mask its complement.
        let px = square_image(200, 40, 40, 160, 160);
        let normal = binarize(&px, 200, 200, false).unwrap();
        let inverted = binarize(&px, 200, 200, true).unwrap();
        assert!(
            normal.get(50, 50) && !inverted.get(50, 50),
            "inside the square: bright in parity, dark inverted"
        );
        assert!(
            !normal.get(5, 5) && inverted.get(5, 5),
            "background: dark in parity, bright inverted"
        );
        // Parity: the default stays the Java's bright-mask behavior.
        assert!(!ConvertParams::default().invert);
    }

    #[test]
    fn invert_changes_the_pipeline_output() {
        // Bright background touching three borders, a dark column at the left
        // edge and a dark interior disk: the parity mask traces the big
        // background loop AND the disk; the inverted mask traces the dark
        // column and the disk — the two models differ.
        let mut px = solid(200, 200, (255, 255, 255));
        for y in 0..200 {
            for x in 0..20 {
                let i = (y * 200 + x) * 4;
                px[i] = 0;
                px[i + 1] = 0;
                px[i + 2] = 0;
            }
        }
        for y in 0..200 {
            for x in 0..200 {
                let dx = x as f32 + 0.5 - 100.0;
                let dy = y as f32 + 0.5 - 100.0;
                if dx * dx + dy * dy <= 50.0 * 50.0 {
                    let i = (y * 200 + x) * 4;
                    px[i] = 0;
                    px[i + 1] = 0;
                    px[i + 2] = 0;
                }
            }
        }
        let normal = convert_image(
            &px,
            200,
            200,
            &ConvertParams {
                color_mode: ColorMode::BlackAndWhite,
                ..Default::default()
            },
        )
        .unwrap();
        let inverted = convert_image(
            &px,
            200,
            200,
            &ConvertParams {
                color_mode: ColorMode::BlackAndWhite,
                invert: true,
                ..Default::default()
            },
        )
        .unwrap();
        assert!(!normal.polylines.is_empty());
        assert!(!inverted.polylines.is_empty());
        assert_ne!(normal, inverted, "invert must change the traced geometry");
    }

    #[test]
    fn refuses_bad_pixel_counts() {
        let err = convert_image(&[0; 3], 1, 1, &ConvertParams::default()).unwrap_err();
        assert!(matches!(err, Error::RasterSizeMismatch { .. }));
    }

    #[test]
    fn perlin_refuses_with_the_named_exception() {
        let px = circle_image(100, 50.0, 50.0, 30.0);
        let params = ConvertParams {
            hatch_mode: HatchMode::Perlin,
            fill: true,
            ..Default::default()
        };
        assert!(matches!(
            convert_image(&px, 100, 100, &params),
            Err(Error::PerlinNotPorted)
        ));
    }

    #[test]
    fn outline_only_strokes_the_contours() {
        let px = circle_image(200, 100.0, 100.0, 60.0);
        let params = ConvertParams {
            color_mode: ColorMode::BlackAndWhite,
            fill: false,
            spacing: 10.0,
            stroke_weight: 25.0,
            ..Default::default()
        };
        let model = convert_image(&px, 200, 200, &params).unwrap();
        assert!(!model.polylines.is_empty());
        assert!(model.colors.iter().all(|&c| c == 0x000000));
        assert!(in_bounds(&model.polylines, 200));
        // Every bar point sits within half the stroke weight of a contour
        // SEGMENT (the D004 oracle is `spacing/2` wide: weight 25 ->
        // half 12.5).
        let mask = binarize(&px, 200, 200, false).unwrap();
        let mut contours = trace::find_contours(&mask).unwrap();
        contours.retain(|c| c.len() >= 3);
        let contours: Vec<Vec<Point>> = contours
            .iter()
            .map(|c| trace::approx_poly_dp(c, 1.0))
            .collect();
        for poly in &model.polylines {
            for p in poly {
                let d = contours.iter().fold(f32::INFINITY, |best, c| {
                    c.windows(2).fold(best, |b, seg| {
                        b.min(trace::point_distance_to_segment(*p, seg[0], seg[1]))
                    })
                });
                assert!(d <= 13.0, "bar point {p:?} at {d} from the contour");
            }
        }
    }

    #[test]
    fn parallel_fill_stays_inside_the_image() {
        let px = square_image(200, 50, 50, 150, 150);
        let params = ConvertParams {
            hatch_mode: HatchMode::Parallel,
            color_mode: ColorMode::BlackAndWhite,
            fill: true,
            spacing: 10.0,
            ..Default::default()
        };
        let model = convert_image(&px, 200, 200, &params).unwrap();
        assert!(!model.polylines.is_empty());
        assert!(in_bounds(&model.polylines, 200));
    }

    #[test]
    fn cross_fill_is_non_empty_and_deterministic() {
        let px = circle_image(200, 100.0, 100.0, 60.0);
        let params = ConvertParams {
            hatch_mode: HatchMode::Cross,
            color_mode: ColorMode::BlackAndWhite,
            fill: true,
            spacing: 10.0,
            ..Default::default()
        };
        let a = convert_image(&px, 200, 200, &params).unwrap();
        let b = convert_image(&px, 200, 200, &params).unwrap();
        assert!(!a.polylines.is_empty());
        assert_eq!(a, b, "the pipeline must be deterministic");
    }

    #[test]
    fn concentric_and_spiral_share_the_isolines_path() {
        // The Java's hatchRaster dispatches both to isolines(im, d); the port
        // keeps them identical (D005 notes it).
        let px = circle_image(200, 100.0, 100.0, 60.0);
        let conc = ConvertParams {
            hatch_mode: HatchMode::Concentric,
            color_mode: ColorMode::BlackAndWhite,
            fill: true,
            spacing: 10.0,
            ..Default::default()
        };
        let spir = ConvertParams {
            hatch_mode: HatchMode::Spiral,
            ..conc
        };
        let a = convert_image(&px, 200, 200, &conc).unwrap();
        let b = convert_image(&px, 200, 200, &spir).unwrap();
        assert!(!a.polylines.is_empty());
        assert_eq!(a, b);
        assert!(in_bounds(&a.polylines, 200));
    }

    #[test]
    fn multicolor_cycles_the_deterministic_palette() {
        let px = circle_image(200, 100.0, 100.0, 60.0);
        let params = ConvertParams {
            color_mode: ColorMode::MultiColor,
            fill: true,
            spacing: 10.0,
            ..Default::default()
        };
        let a = convert_image(&px, 200, 200, &params).unwrap();
        let b = convert_image(&px, 200, 200, &params).unwrap();
        assert_eq!(a, b, "no app.random: the palette is deterministic");
        assert!(!a.colors.is_empty());
        assert!(a.colors.iter().all(|c| PALETTE.contains(c)));
    }

    #[test]
    fn realistic_colors_come_from_the_dominant_colors() {
        // A red square and a blue square on black: the histogram yields
        // black, red and blue — every polyline color must be one of them.
        let mut px = solid(200, 200, (0, 0, 0));
        for y in 60..110 {
            for x in 60..110 {
                let i = (y * 200 + x) * 4;
                px[i] = 220;
                px[i + 1] = 20;
                px[i + 2] = 20;
            }
        }
        for y in 100..150 {
            for x in 100..150 {
                let i = (y * 200 + x) * 4;
                px[i] = 20;
                px[i + 1] = 20;
                px[i + 2] = 230;
            }
        }
        let params = ConvertParams {
            color_mode: ColorMode::Realistic,
            fill: true,
            spacing: 10.0,
            ..Default::default()
        };
        let model = convert_image(&px, 200, 200, &params).unwrap();
        assert!(!model.polylines.is_empty());
        let dominant = extract_colors(&px, 10);
        assert!(dominant.contains(&0x000000));
        assert!(dominant.contains(&0xDC1414));
        assert!(dominant.contains(&0x1414E6));
        assert!(
            model.colors.iter().all(|c| dominant.contains(c)),
            "colors must come from the extracted palette"
        );
    }

    #[test]
    fn realistic_resamples_to_the_stitch_length() {
        // The isolines of a big circle are long closed loops; realistic mode
        // resamples them (the Java's pushPolyline when
        // colorizeEmbroideryFromImage), so no consecutive points are farther
        // apart than the 10 px stitch length (plus float slack).
        let px = circle_image(400, 200.0, 200.0, 150.0);
        let params = ConvertParams {
            hatch_mode: HatchMode::Concentric,
            color_mode: ColorMode::Realistic,
            fill: true,
            spacing: 15.0,
            ..Default::default()
        };
        let model = convert_image(&px, 400, 400, &params).unwrap();
        assert!(!model.polylines.is_empty());
        for poly in &model.polylines {
            for w in poly.windows(2) {
                assert!(w[0].dist(w[1]) <= 10.01);
            }
        }
    }

    #[test]
    fn black_image_produces_an_empty_model() {
        let px = solid(100, 100, (0, 0, 0));
        let params = ConvertParams {
            fill: true,
            ..Default::default()
        };
        let model = convert_image(&px, 100, 100, &params).unwrap();
        assert!(model.polylines.is_empty());
    }

    #[test]
    fn defaults_match_the_java_converter() {
        let d = ConvertParams::default();
        assert_eq!(d.hatch_mode, HatchMode::Cross);
        assert_eq!(d.color_mode, ColorMode::MultiColor);
        assert!(!d.fill);
        assert_eq!(d.spacing, 10.0);
        assert_eq!(d.stroke_weight, 25.0);
        assert_eq!(d.max_colors, 10);
    }
}
