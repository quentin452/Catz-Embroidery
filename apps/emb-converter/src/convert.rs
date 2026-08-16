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
    /// centred PES file → read back into a non-empty design. The image is a
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
        let design = model.centered_design("out", 95.0, 95.0);
        emb_data::write_design(&out, &design).expect("write pes");
        let read = emb_data::pes::read(&std::fs::read(&out).expect("read pes")).expect("parse pes");
        assert!(!read.stitches.is_empty());
        assert_eq!(read.colors.len(), read.stitches.len());
        // The Java's write(): translate(-export/2, -export/2); the PES header
        // stores only the rounded extents (pes.rs read returns [0, 0, w, h]),
        // and the design is NOT scaled to the mm (D005 notes it) — the
        // stitches sit in the translated image space.
        assert_eq!(read.bounds, [0.0, 0.0, 95.0, 95.0]);
        for p in &read.stitches {
            assert!(
                p.x > -48.0 && p.x < 952.5,
                "centred on the export mm, not scaled"
            );
            assert!(p.y > -48.0 && p.y < 952.5);
        }
    }
}
