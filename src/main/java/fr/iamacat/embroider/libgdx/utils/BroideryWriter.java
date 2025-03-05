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
// TODO FIX ONLY SVG SAVER SAVING SHAPES GOODLY
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
		int SCALE = 16; // SUPERSAMPLING FOR QUALITY
		int scaledWidth = (int) (width * SCALE);
		int scaledHeight = (int) (height * SCALE);

		// Création de la grande Pixmap
		Pixmap largePixmap = new Pixmap(scaledWidth, scaledHeight, Pixmap.Format.RGBA8888);
		largePixmap.setColor(Color.CLEAR);
		largePixmap.fill();

		for (BezierShape shape : shapes) {
			int color = shape.getColor();
			Color gdxColor = new Color(
					(color >> 16 & 0xFF) / 255f,
					(color >> 8 & 0xFF) / 255f,
					(color & 0xFF) / 255f,
					1f
			);
			largePixmap.setColor(gdxColor);

			List<Vec2> polygonPoints = new ArrayList<>();
			for (BezierCurve curve : shape) {
				List<Vec2> sampledPoints = BezierUtil.sampleBezierCurve(curve);
				for (Vec2 point : sampledPoints) {
					polygonPoints.add(new Vec2(point.x * SCALE, point.y * SCALE));
				}
			}

			if (!polygonPoints.isEmpty() && !polygonPoints.get(polygonPoints.size() - 1).equals(polygonPoints.get(0))) {
				polygonPoints.add(polygonPoints.get(0));
			}

			BezierUtil.fillPolygon(largePixmap, polygonPoints, gdxColor);

			for (BezierCurve curve : shape) {
				renderBezierCurveToPixmap(largePixmap, curve, gdxColor, SCALE);
			}
		}

		Pixmap finalPixmap = new Pixmap((int) width, (int) height, Pixmap.Format.RGBA8888);
		finalPixmap.drawPixmap(largePixmap, 0, 0, scaledWidth, scaledHeight, 0, 0, (int) width, (int) height);

		PixmapIO.writePNG(Gdx.files.absolute(filename + "." + extension), finalPixmap);

		largePixmap.dispose();
		finalPixmap.dispose();
	}

	public static void write(String filename, List<BezierShape> shapes, float width, float height) {
		if (shapes == null || shapes.isEmpty()) {
			throw new IllegalArgumentException("Shapes list cannot be null or empty");
		}
		String[] tokens = filename.split("\\.(?=[^\\.]+$)");
		float scale = PixelUtil.pixelToMm(width, height);
		width = scale;
		height = scale;
		boolean isCustomTitle = TITLE != null;
		if (TITLE == null) {
			String[] strs = tokens[0].split("/|\\\\");
			TITLE = strs[strs.length - 1].substring(0, 8);
		}

		try {
			switch (tokens[1].toUpperCase()) {
				case "PES" -> saveBezierShapesAsPES(tokens[0], shapes, width, height);
				case "SVG" -> saveBezierShapesAsSVG(tokens[0], tokens[1], shapes, width, height);
				case "PNG" -> saveBezierShapesAsPNG(tokens[0], tokens[1], shapes, width, height);
				default -> throw new IOException("Unimplemented");
			}
		} catch (IOException e) {
			Gdx.app.error("PEmbroiderWriter","IO Error during writing file : " + filename , e);
		}

		if (!isCustomTitle) TITLE = null;
	}
}
