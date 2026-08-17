//! The app-side image handling: decode → resize to the pipeline's work size
//! (the Java's `img.resize(1000, 1000)`, Main.java:284). The Java2D
//! multi-step bilinear downscale becomes the `image` crate's single-pass
//! Triangle resampler — a raster pre-step deviation, noted in D005; the
//! comparison level for the raster path is functional acceptance, not
//! fixture tolerance.

use image::DynamicImage;

/// The pipeline's work size: every source is stretched to exactly this, like
/// the Java converter.
pub const WORK_SIZE: u32 = 1000;

/// Load an image file (jpg/png/jpeg/bmp/gif — what the Java accepts) and
/// resize it to the pipeline's work size.
pub fn load_image(path: &std::path::Path) -> Result<Vec<u8>, String> {
    let img = image::open(path).map_err(|e| format!("cannot decode {}: {e}", path.display()))?;
    Ok(to_work_rgba(&img))
}

/// The Java's `.pes` input path (Main.java:233-243): read a design, then
/// `PEmbroiderReader.createImageFromPolylines` rasterizes it back into a
/// canvas the pipeline consumes like any other source. The runs split on
/// colour change (the Java reader's colour-change grouping); the Java's own
/// `PES.read` adds raw 12-bit deltas WITHOUT accumulating them (a model bug —
/// the "polyline" would stay a few mm long) — ours reads absolute mm through
/// `emb_data::pes::read` (M1, pyembroidery-tested), so the raster holds the
/// real design.
///
/// Deviation from the Java's raster: the background is BLACK, not the Java's
/// `p.color(255)` white. The pipeline's bright threshold (`max(rgb) >= 127`)
/// can never separate thread-colour pixels from a white canvas — the whole
/// mask turns on as one blob — so a white raster defeats its own consumer
/// (the Java only "worked" because it drew near-black thread-INDEX values,
/// extracting the lines as negative-space holes). Black background extracts
/// the thread colours as the foreground; dark-thread designs take the Invert
/// toggle, like any dark image. Recorded in ROADMAP.
pub fn load_design_pixels(path: &std::path::Path) -> Result<Vec<u8>, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("cannot read {}: {e}", path.display()))?;
    let design =
        emb_data::pes::read(&bytes).map_err(|e| format!("cannot parse {}: {e}", path.display()))?;

    // Per-colour runs of points (the Java's colour-change split).
    let mut runs: Vec<(u32, Vec<(f32, f32)>)> = Vec::new();
    for (i, p) in design.stitches.iter().enumerate() {
        let color = design.colors[i];
        match runs.last_mut() {
            Some((c, pts)) if *c == color => pts.push((p.x, p.y)),
            _ => runs.push((color, vec![(p.x, p.y)])),
        }
    }
    if runs.is_empty() {
        return Err(format!("{} has no stitches", path.display()));
    }

    // `PEmbroiderReader.normalizePolylines`: uniform scale to fit the
    // canvas, translated to the origin.
    let (mut min_x, mut min_y, mut max_x, mut max_y) = (
        f32::INFINITY,
        f32::INFINITY,
        f32::NEG_INFINITY,
        f32::NEG_INFINITY,
    );
    for (_, pts) in &runs {
        for &(x, y) in pts {
            min_x = min_x.min(x);
            min_y = min_y.min(y);
            max_x = max_x.max(x);
            max_y = max_y.max(y);
        }
    }
    let span_x = max_x - min_x;
    let span_y = max_y - min_y;
    if span_x <= 0.0 && span_y <= 0.0 {
        return Err(format!("{} is a single point", path.display()));
    }
    let scale_x = if span_x > 0.0 {
        WORK_SIZE as f32 / span_x
    } else {
        f32::INFINITY
    };
    let scale_y = if span_y > 0.0 {
        WORK_SIZE as f32 / span_y
    } else {
        f32::INFINITY
    };
    let scale = scale_x.min(scale_y);

    // Black canvas; Bresenham segments in each run's colour
    // (`createImageFromPolylines` + `drawLineOnImage`, the Java's pixel
    // truncation toward zero included).
    let mut px = vec![0u8; (WORK_SIZE * WORK_SIZE * 4) as usize];
    for (color, pts) in &runs {
        for w in pts.windows(2) {
            let (x1, y1) = to_pixel(w[0], min_x, min_y, scale);
            let (x2, y2) = to_pixel(w[1], min_x, min_y, scale);
            draw_line(&mut px, x1, y1, x2, y2, *color);
        }
    }
    Ok(px)
}

/// `PApplet.map(p, 0, canvas, 0, canvas)` truncated to the pixel grid.
fn to_pixel((x, y): (f32, f32), min_x: f32, min_y: f32, scale: f32) -> (i32, i32) {
    (((x - min_x) * scale) as i32, ((y - min_y) * scale) as i32)
}

/// The Java `drawLineOnImage`'s Bresenham, writing the packed colour into an
/// RGBA8 buffer.
fn draw_line(px: &mut [u8], x1: i32, y1: i32, x2: i32, y2: i32, color: u32) {
    let (mut x, mut y) = (x1, y1);
    let (dx, dy) = ((x2 - x1).abs(), (y2 - y1).abs());
    let (sx, sy) = (if x1 < x2 { 1 } else { -1 }, if y1 < y2 { 1 } else { -1 });
    let mut err = dx - dy;
    loop {
        if x >= 0 && x < WORK_SIZE as i32 && y >= 0 && y < WORK_SIZE as i32 {
            let i = ((y as u32 * WORK_SIZE + x as u32) * 4) as usize;
            px[i] = ((color >> 16) & 0xFF) as u8;
            px[i + 1] = ((color >> 8) & 0xFF) as u8;
            px[i + 2] = (color & 0xFF) as u8;
        }
        if x == x2 && y == y2 {
            break;
        }
        let e2 = 2 * err;
        if e2 > -dy {
            err -= dy;
            x += sx;
        }
        if e2 < dx {
            err += dx;
            y += sy;
        }
    }
}

/// `img.resize(1000, 1000)`: stretch to exactly the work size (non-uniform,
/// like the Java), RGBA8.
pub fn to_work_rgba(img: &DynamicImage) -> Vec<u8> {
    img.resize_exact(WORK_SIZE, WORK_SIZE, image::imageops::FilterType::Triangle)
        .to_rgba8()
        .into_raw()
}

#[cfg(test)]
mod tests {
    use super::*;
    use emb_model::convert::{ColorMode, ConvertParams, HatchMode};

    #[test]
    fn resize_stretches_to_exactly_the_work_size() {
        let img = image::DynamicImage::ImageRgba8(image::RgbaImage::from_pixel(
            640,
            480,
            image::Rgba([255, 0, 0, 255]),
        ));
        let px = to_work_rgba(&img);
        assert_eq!(px.len(), (WORK_SIZE * WORK_SIZE * 4) as usize);
    }

    /// The converter's export path end to end: a PNG file → pipeline →
    /// PES file → read back into a non-empty design. The image is a
    /// white disk on black, so the outline stroke (the default FillB=false)
    /// has real content.
    #[test]
    fn image_file_to_pes_round_trips() {
        let mut img = image::RgbaImage::from_pixel(400, 400, image::Rgba([0, 0, 0, 255]));
        for (x, y, px) in img.enumerate_pixels_mut() {
            let dx = x as f32 + 0.5 - 200.0;
            let dy = y as f32 + 0.5 - 200.0;
            if dx * dx + dy * dy <= 150.0 * 150.0 {
                *px = image::Rgba([255, 255, 255, 255]);
            }
        }
        let path = std::env::temp_dir()
            .join("emb-converter-test")
            .join("disk.png");
        std::fs::create_dir_all(path.parent().expect("temp dir")).expect("temp dir");
        img.save(&path).expect("save png");

        let pixels = load_image(&path).expect("load png");
        let params = ConvertParams {
            hatch_mode: HatchMode::Cross,
            color_mode: ColorMode::BlackAndWhite,
            fill: true,
            spacing: 10.0,
            stroke_weight: 25.0,
            max_colors: 10,
            invert: false,
        };
        let mut model = emb_model::convert::convert_image(
            &pixels,
            WORK_SIZE as usize,
            WORK_SIZE as usize,
            &params,
        )
        .expect("convert");
        assert!(!model.polylines.is_empty());
        model.optimize();

        let out = std::env::temp_dir()
            .join("emb-converter-test")
            .join("out.pes");
        let design = model.to_design("out".into());
        emb_data::write_design(&out, &design).expect("write pes");
        let read = emb_data::pes::read(&std::fs::read(&out).expect("read pes")).expect("parse pes");
        assert!(!read.stitches.is_empty());
        assert_eq!(read.colors.len(), read.stitches.len());
        // The save is in the pipeline's NATIVE space (0..WORK_SIZE, NOT centred
        // on the export mm — the fix for the save≠preview divergence): the PES
        // header stores only the rounded extents, the reader returns
        // [0, 0, w, h], and the stitches land back at their native positions.
        assert_eq!(read.bounds, [0.0, 0.0, WORK_SIZE as f32, WORK_SIZE as f32]);
        for p in &read.stitches {
            assert!(
                p.x >= -0.5 && p.x <= WORK_SIZE as f32 + 0.5,
                "native space, not centred on the export mm"
            );
            assert!(p.y >= -0.5 && p.y <= WORK_SIZE as f32 + 0.5);
        }
    }

    /// JPEG is accepted at the decode level too (the Java's loadImage list):
    /// a JPEG file → pipeline → PES round-trips into a non-empty design, with
    /// the compression artefacts staying inside the mask.
    #[test]
    fn jpeg_file_to_pes_round_trips() {
        let mut img = image::RgbaImage::from_pixel(400, 400, image::Rgba([0, 0, 0, 255]));
        for (x, y, px) in img.enumerate_pixels_mut() {
            let dx = x as f32 + 0.5 - 200.0;
            let dy = y as f32 + 0.5 - 200.0;
            if dx * dx + dy * dy <= 150.0 * 150.0 {
                *px = image::Rgba([255, 255, 255, 255]);
            }
        }
        let path = std::env::temp_dir()
            .join("emb-converter-test")
            .join("disk.jpg");
        std::fs::create_dir_all(path.parent().expect("temp dir")).expect("temp dir");
        // JPEG has no alpha; the image crate's JPEG encoder wants RGB.
        image::DynamicImage::ImageRgba8(img)
            .to_rgb8()
            .save(&path)
            .expect("save jpg");

        let pixels = load_image(&path).expect("load jpg");
        let params = ConvertParams {
            hatch_mode: HatchMode::Cross,
            color_mode: ColorMode::BlackAndWhite,
            fill: true,
            spacing: 10.0,
            stroke_weight: 25.0,
            max_colors: 10,
            invert: false,
        };
        let mut model = emb_model::convert::convert_image(
            &pixels,
            WORK_SIZE as usize,
            WORK_SIZE as usize,
            &params,
        )
        .expect("convert");
        assert!(!model.polylines.is_empty());
        model.optimize();

        let out = std::env::temp_dir()
            .join("emb-converter-test")
            .join("out.jpg.pes");
        let design = model.to_design("out".into());
        emb_data::write_design(&out, &design).expect("write pes");
        let read = emb_data::pes::read(&std::fs::read(&out).expect("read pes")).expect("parse pes");
        assert!(!read.stitches.is_empty());
        assert_eq!(read.bounds, [0.0, 0.0, WORK_SIZE as f32, WORK_SIZE as f32]);
    }

    /// The .pes input path: a written design rasterizes back into a black-
    /// canvas work image, scaled to fit. The Java's own `PES.read` would produce
    /// a few-mm delta mess (no accumulation) — ours must read the real design.
    #[test]
    fn design_file_rasterizes_back_into_work_pixels() {
        // A closed diamond, 60x40 mm — a straight-line design would collapse to
        // 2-point contours after approxPolyDP and be dropped by the `>= 3`
        // filter, so the end-to-end needs cornered geometry.
        let design = emb_data::Design {
            bounds: [0.0, 0.0, 60.0, 40.0],
            stitches: vec![
                emb_data::Point { x: 30.0, y: 0.0 },
                emb_data::Point { x: 60.0, y: 20.0 },
                emb_data::Point { x: 30.0, y: 40.0 },
                emb_data::Point { x: 0.0, y: 20.0 },
                emb_data::Point { x: 30.0, y: 0.0 },
            ],
            colors: vec![0x0000FF; 5],
            jumps: vec![],
            title: "diamond".into(),
        };
        let path = std::env::temp_dir()
            .join("emb-converter-test")
            .join("in.pes");
        std::fs::create_dir_all(path.parent().expect("temp dir")).expect("temp dir");
        emb_data::write_design(&path, &design).expect("write pes");

        let px = load_design_pixels(&path).expect("rasterize design");
        assert_eq!(px.len(), (WORK_SIZE * WORK_SIZE * 4) as usize);
        // The colour round-trips through the PEC palette (format-mapped), so
        // assert the SHAPE: the stitch segments are non-black on a black canvas.
        // Uniform normalize scale ×16.67 puts the diamond corners at
        // (500,0), (999,333), (500,667), (0,333) — the right corner pixel sits
        // at x=1000 and is clipped, the others are drawn.
        let px_at = |x: u32, y: u32| -> [u8; 3] {
            let i = (y * WORK_SIZE + x) as usize * 4;
            [px[i], px[i + 1], px[i + 2]]
        };
        let non_black = |c: [u8; 3]| c != [0, 0, 0];
        let any_non_black_in = |x0: u32, x1: u32, y0: u32, y1: u32| {
            (y0..y1).any(|y| (x0..x1).any(|x| non_black(px_at(x, y))))
        };
        assert!(any_non_black_in(495, 505, 0, 1), "the top corner is drawn");
        assert!(any_non_black_in(0, 2, 330, 336), "the left corner is drawn");
        assert!(
            any_non_black_in(990, 1000, 330, 336),
            "the right edge reaches the canvas edge"
        );
        assert!(
            !non_black(px_at(500, 333)),
            "the diamond interior is not filled"
        );
        assert!(
            !non_black(px_at(500, 900)),
            "outside the diamond stays black"
        );

        // End to end: the rasterized design feeds the pipeline (the point of the
        // feature). The coloured outline on black extracts as the mask.
        let model = emb_model::convert::convert_image(
            &px,
            WORK_SIZE as usize,
            WORK_SIZE as usize,
            &emb_model::convert::ConvertParams {
                color_mode: emb_model::convert::ColorMode::BlackAndWhite,
                ..Default::default()
            },
        )
        .expect("convert the rasterized design");
        assert!(!model.polylines.is_empty(), "the design converts");
    }
}
