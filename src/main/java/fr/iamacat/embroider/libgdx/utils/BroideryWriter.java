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
import org.embroideryio.embroideryio.EmbConstant;
import org.embroideryio.embroideryio.EmbPattern;
import org.embroideryio.embroideryio.EmbThread;
import org.embroideryio.embroideryio.PesWriter;

import java.awt.geom.Point2D;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

// TODO FIX SCALING IS NOT GOOD FOR ,WE CAN SEE MISSING USED PIXELS
// TODO FIX PES COLORS AND FILL NOT GOOD
public class BroideryWriter {
	public static String TITLE = null;
	public static void saveBezierShapesAsPES(String filename, List<BezierShape> shapes, float width, float height) {
		try {
			EmbPattern pattern = new EmbPattern();

			if (!shapes.isEmpty()) {
				// Assume all shapes share the same color; adjust if needed
				int color = shapes.get(0).getColor();
				EmbThread thread = new EmbThread(color);
				pattern.addThread(thread);

				Vec2 lastPosition = null;
				for (BezierShape shape : shapes) {
					boolean firstPointInShape = true;
					for (BezierCurve curve : shape) {
						List<Vec2> sampledPoints = BezierUtil.sampleBezierCurve(curve);
						for (Vec2 point : sampledPoints) {
							float x = (float) point.x;
							float y = (float) point.y;
							if (firstPointInShape) {
								// JUMP only if not starting from the last point
								if (lastPosition == null || !isConnected(lastPosition, point)) {
									pattern.addStitchAbs(x, y, EmbConstant.JUMP);
								}
								firstPointInShape = false;
							} else {
								pattern.addStitchAbs(x, y, EmbConstant.STITCH);
							}
							lastPosition = point;
						}
					}
				}
				pattern.addStitchAbs(0, 0, EmbConstant.STOP); // Single STOP at the end
			}

			PesWriter writer = new PesWriter();
			writer.set(PesWriter.PROP_PES_VERSION, 6);
			try (FileOutputStream out = new FileOutputStream(filename + ".pes")) {
				writer.write(pattern, out);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Helper method to check continuity between shapes
	private static boolean isConnected(Vec2 last, Vec2 current) {
		return last.x == current.x && last.y == current.y;
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
		int SCALE = 16; // Supersampling for quality
		int scaledWidth = (int) (width * SCALE);
		int scaledHeight = (int) (height * SCALE);

		// Create a scaled Pixmap
		Pixmap largePixmap = new Pixmap(scaledWidth, scaledHeight, Pixmap.Format.RGBA8888);
		largePixmap.setColor(Color.CLEAR);
		largePixmap.fill();

		// Delegate rendering of Bezier shapes to BezierUtil
		BezierUtil.renderShapesToPixmap(largePixmap, shapes, SCALE);

		// Scale down to final size
		Pixmap finalPixmap = new Pixmap((int) width, (int) height, Pixmap.Format.RGBA8888);
		finalPixmap.drawPixmap(largePixmap, 0, 0, scaledWidth, scaledHeight, 0, 0, (int) width, (int) height);

		// Save to file
		PixmapIO.writePNG(Gdx.files.absolute(filename + "." + extension), finalPixmap);

		// Dispose Pixmaps
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
