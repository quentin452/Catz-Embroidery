package fr.iamacat.embroider.libgdx.utils;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Matrix3;
import com.badlogic.gdx.math.Vector2;
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
	public static void saveBezierShapesAsPES(String filename, List<BezierShape> shapes, float width, float height) {
		try {
			float[] bounds = {0, 0, width, height};
			ArrayList<Vec2> stitches = new ArrayList<>();
			ArrayList<Integer> colors = new ArrayList<>();
			ArrayList<Boolean> jumps = new ArrayList<>();

			for (BezierShape shape : shapes) {
				int color = shape.getColor();
				for (BezierCurve curve : shape) {
					List<Vec2> sampledPoints = BezierUtil.sampleBezierCurve(curve);
					for (int i = 0; i < sampledPoints.size(); i++) {
						Vec2 point = sampledPoints.get(i);
						stitches.add(point);
						colors.add(color);
						jumps.add(i == 0);
					}
				}
			}
			PESUtil.write(filename, bounds, stitches, colors, TITLE, jumps);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void saveBezierShapesAsSVG(String filename, String extension, List<BezierShape> shapes, float width, float height) {
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
		float scale = PixelUtil.pixelToMm(width, height);

		scaleShapes(shapes, scale, scale); // Applique directement la mise à l'échelle

		boolean isCustomTitle = TITLE != null;
		if (TITLE == null) {
			String[] strs = tokens[0].split("/|\\\\");
			TITLE = strs[strs.length - 1].substring(0, 8);
		}

		try {
			switch (tokens[1].toUpperCase()) {
				case "PES" -> saveBezierShapesAsPES(tokens[0], shapes, width * scale, height * scale);
				case "SVG" -> saveBezierShapesAsSVG(tokens[0], tokens[1], shapes, width * scale, height * scale);
				case "PNG" -> saveBezierShapesAsPNG(tokens[0], tokens[1], shapes, width * scale, height * scale);
				default -> throw new IOException("Unimplemented");
			}
		} catch (IOException e) {
			Gdx.app.error("PEmbroiderWriter","IO Error during writing file : " + filename , e);
		}

		if (!isCustomTitle) TITLE = null;
	}
}
