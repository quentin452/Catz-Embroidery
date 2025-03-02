package fr.iamacat.embroider.libgdx.utils;
import com.badlogic.gdx.Gdx;
import net.plantabyte.drptrace.geometry.BezierShape;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class BroideryWriter {

	public static String TITLE = null;
// TODO FIX WIDTH AND HEIGHT
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

	public static void write(String filename, List<BezierShape> shapes, float width, float height) {
		boolean isCustomTitle = true;
		String[] tokens = filename.split("\\.(?=[^\\.]+$)");
		if (TITLE == null) {
			isCustomTitle = false;
			String[] strs = tokens[0].split("/|\\\\");
			TITLE = strs[strs.length - 1];
		}
		System.out.println(TITLE);
		TITLE = TITLE.substring(0, Math.min(8, TITLE.length()));
		try {
			switch (tokens[1].toUpperCase()) {
				case "SVG":
					saveBezierShapesAsSVG(tokens[0],tokens[1],shapes, width, height);
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
