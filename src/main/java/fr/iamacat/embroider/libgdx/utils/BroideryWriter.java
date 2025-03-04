package fr.iamacat.embroider.libgdx.utils;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static fr.iamacat.embroider.libgdx.utils.BezierUtil.renderBezierCurveToPixmap;
import static fr.iamacat.embroider.libgdx.utils.BezierUtil.scaleShapes;
public class BroideryWriter {

	public static String TITLE = null;
	private static void saveBezierShapesAsPES(String filename, String extension, List<BezierShape> shapes, float width, float height) {
		try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(Paths.get(filename + "." + extension)))) {
			// Write PES header
			PESUtil.writePESHeader(out, (int)(width * 10), (int)(height * 10),TITLE); // Convert mm to 0.1mm units

			//Collect all stitches with color changes
			List<PESUtil.Stitch> stitches = new ArrayList<>();

			int lastColor = -1;
			Point2D.Float lastPos = new Point2D.Float(0, 0);

			for (BezierShape shape : shapes) {
				int color = shape.getColor();
				if (color != lastColor) {
					// Add color change: trim and jump to start of new shape
					if (lastColor != -1) {
						stitches.add(new PESUtil.Stitch(0, 0, PESUtil.StitchType.TRIM));
					}
					List<Point2D.Float> points = PESUtil.sampleShape(shape);
					if (!points.isEmpty()) {
						stitches.addAll(PESUtil.createJump(lastPos, points.get(0)));
						lastPos = points.get(0);
					}
					lastColor = color;
				}

				// Add stitches for the current shape
				List<Point2D.Float> points = PESUtil.sampleShape(shape);
				for (Point2D.Float point : points) {
					int dx = (int) (point.x * 10 - lastPos.x); // Convert mm to 0.1mm
					int dy = (int) (point.y * 10 - lastPos.y);
					stitches.add(new PESUtil.Stitch(dx, dy, PESUtil.StitchType.NORMAL));
					lastPos.setLocation(point.x * 10, point.y * 10);
				}
			}

			// End with a STOP command
			stitches.add(new PESUtil.Stitch(0, 0, PESUtil.StitchType.STOP));

			// Write stitches to file
			PESUtil.writeStitches(out, stitches);
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("IO Error: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			System.err.println("Error: " + e.getMessage());
		}
	}
	private static void saveBezierShapesAsSVG(String filename,String extension,List<BezierShape> shapes, float width, float height) {
		try (BufferedWriter out = Files.newBufferedWriter(Paths.get(filename + "." + extension))) {
			out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
			out.write(String.format("<svg width=\"%.2f\" height=\"%.2f\" id=\"svgroot\" version=\"1.1\" viewBox=\"0 0 %.2f %.2f\" xmlns=\"http://www.w3.org/2000/svg\">\n", width, height, width, height));
			for (BezierShape shape : shapes) {
				int color = shape.getColor();
				int red = (color >> 16) & 0xFF;
				int green = (color >> 8) & 0xFF;
				int blue = color & 0xFF;
				String hexColor = String.format("#%02x%02x%02x", red, green, blue);
				out.write(String.format("<path style=\"fill:%s\" d=\"%s\" />\n", hexColor, shape.toSVGPathString()));
			}
			out.write("</svg>\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static void saveBezierShapesAsPNG(String filename, String extension, List<BezierShape> shapes, float width, float height) {
		Pixmap pixmap = new Pixmap((int) width, (int) height, Pixmap.Format.RGBA8888);

		// Draw the BezierShapes directly to the Pixmap
		for (BezierShape shape : shapes) {
			int color = shape.getColor();
			Color gdxColor = new Color((color >> 16 & 0xFF), (color >> 8 & 0xFF), (color & 0xFF), 1f);
			pixmap.setColor(gdxColor);
			for (BezierCurve curve : shape) {
				renderBezierCurveToPixmap(pixmap, curve, gdxColor);
			}
		}
		PixmapIO.writePNG(Gdx.files.absolute(filename + "." + extension), pixmap);
		pixmap.dispose(); // Dispose the Pixmap to free resources
	}

	public static void write(String filename, List<BezierShape> shapes, float width, float height) {
		if (shapes == null || shapes.isEmpty()) {
			throw new IllegalArgumentException("Shapes list cannot be null or empty");
		}
		String[] tokens = filename.split("\\.(?=[^\\.]+$)");
		float scale = PixelUtil.pixelToMm(width,height);
		width = scale;
		height = scale;
		scaleShapes(shapes, width, height);
		boolean isCustomTitle = true;
		if (TITLE == null) {
			isCustomTitle = false;
			String[] strs = tokens[0].split("/|\\\\");
			TITLE = strs[strs.length - 1];
		}
		TITLE = TITLE.substring(0, Math.min(8, TITLE.length()));
		try {
			switch (tokens[1].toUpperCase()) {
				case "PES":
					saveBezierShapesAsPES(tokens[0], tokens[1], shapes, width, height);
					break;
				case "SVG":
					saveBezierShapesAsSVG(tokens[0],tokens[1],shapes, width, height);
					break;
				case "PNG":
					saveBezierShapesAsPNG(tokens[0],tokens[1],shapes, width, height);
					break;
				default:
					throw new IOException("Unimplemented");
			}
		} catch (IOException e) {
			Gdx.app.error("PEmbroiderWriter","IO Error during writing file : " + filename , e);
		}

		// Cleanup temporary settings
		if (!isCustomTitle) TITLE = null;
	}
}
