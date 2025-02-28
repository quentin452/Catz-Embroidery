package fr.iamacat.embroider;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

public class PEmbroiderHatchSpine{
	public static PEmbroiderGraphics G;
	 public static void setGraphics(PEmbroiderGraphics _G) {
		 G = _G;
	 }
	 public static void hatchSpine(Pixmap mask) {
		 hatchSpine(mask,G.HATCH_SPACING);
	 }
	 public static void hatchSpineSmooth(Pixmap mask, Batch batch) {
		 hatchSpineSmooth(mask,G.HATCH_SPACING,batch);
	 }
	 public static void hatchSpineVF(Pixmap mask) {
		 hatchSpineVF(mask,G.HATCH_SPACING);
	 }
	public static void hatchSpine(Pixmap mask, float d) {
		int w = mask.getWidth();
		int h = mask.getHeight();
		boolean[] im = new boolean[w * h];

		// Create a Pixmap and load the pixels
		for (int i = 0; i < w * h; i++) {
			int pixel = mask.getPixel(i % w, i / w);  // Get pixel at position (x, y)
			im[i] = (Color.rgba8888(new Color(pixel)) > 0.5f);  // Check if the pixel is "light" (threshold 127)
		}

		// Convert boolean array to int array (for contour tracing)
		int[] iim = new int[w * h];
		for (int i = 0; i < iim.length; i++) {
			iim[i] = im[i] ? 1 : 0;
		}

		// Find contours (assuming PEmbroiderTrace.findContours is implemented elsewhere)
		ArrayList<ArrayList<Vector2>> contours0 = PEmbroiderTrace.findContours(iim, w, h);

		// Approximate polygons for contours
		for (int i = 0; i < contours0.size(); i++) {
			contours0.set(i, PEmbroiderTrace.approxPolyDP(contours0.get(i), 2));
		}

		// Thinning and dilation (assuming PEmbroiderTrace thinning and dilation are implemented)
		PEmbroiderTrace.thinning(im, w, h);
		im = PEmbroiderTrace.dilate5(im, w, h);

		// Update int array after thinning and dilation
		for (int i = 0; i < iim.length; i++) {
			iim[i] = im[i] ? 1 : 0;
		}

		// Re-run contour extraction after dilation
		ArrayList<ArrayList<Vector2>> contours = PEmbroiderTrace.findContours(iim, w, h);
		for (int i = 0; i < contours.size(); i++) {
			contours.set(i, PEmbroiderTrace.approxPolyDP(contours.get(i), 1));
		}

		// Create a new Pixmap to work with for the output
		Pixmap pg = new Pixmap(w, h, Pixmap.Format.RGBA8888);
		pg.setColor(Color.BLACK);
		pg.fill();

		// Draw the mask on the new Pixmap (equivalent to Processing's image call)
		pg.drawPixmap(mask, 0, 0);

		// Prepare for hatching using contours
		ArrayList<Vector2> P = new ArrayList<Vector2>();
		ArrayList<Vector2> Q = new ArrayList<Vector2>();
		ArrayList<Vector2> V = new ArrayList<Vector2>();

		ArrayList<ArrayList<Vector2>> hatches = new ArrayList<ArrayList<Vector2>>();

		// Process the contours for hatching
		for (int i = 0; i < contours0.size(); i++) {
			ArrayList<Vector2> poly = contours0.get(i);

			for (int j = 0; j < poly.size(); j++) {
				Vector2 p = poly.get(j);
				Vector2 p1 = poly.get((j + 1) % poly.size());

				// Calculate the direction vectors for hatching
				float a0 = (float) Math.atan2(p1.y - p.y, p1.x - p.x);
				float a1 = a0 - (float) Math.PI / 2;  // Perpendicular direction

				float x = p.x;
				float y = p.y;
				float l = p.dst(p1);
				int n = (int) Math.ceil(l / d);  // Number of steps

				float dd = l / (float) n;
				float dx = dd * (float) Math.cos(a0);
				float dy = dd * (float) Math.sin(a0);

				float vx = dd * (float) Math.cos(a1);
				float vy = dd * (float) Math.sin(a1);

				// Generate the hatch lines
				for (int k = 0; k < n; k++) {
					x += dx;
					y += dy;
					P.add(new Vector2(x, y));
					Q.add(new Vector2(x, y));
					V.add(new Vector2(vx, vy));
				}
			}
		}

		// Iterate to create hatching strokes
		for (int i = 0; i < 50; i++) {
			for (int j = P.size() - 1; j >= 0; j--) {
				float x = P.get(j).x;
				float y = P.get(j).y;

				P.get(j).add(V.get(j));  // Apply movement

				// Correct the condition by adding parentheses around the bitwise operation
				if (((pg.getPixel((int) P.get(j).x, (int) P.get(j).y) >> 24) & 0xFF) > 127) {
					if (i > 0) {
						ArrayList<Vector2> H = new ArrayList<Vector2>();
						H.add(Q.get(j));
						H.add(new Vector2(x, y));
						hatches.add(H);
					}

					P.remove(j);
					Q.remove(j);
					V.remove(j);
					continue;
				}

				pg.drawLine((int) x, (int) y, (int) P.get(j).x, (int) P.get(j).y);
			}
		}


		// Draw final contours after hatching
		pg.setColor(Color.RED);
		for (int i = 0; i < contours.size(); i++) {
			ArrayList<Vector2> poly = contours.get(i);

			for (int j = 0; j < poly.size(); j++) {
				Vector2 p = poly.get(j);
				Vector2 p1 = poly.get((j + 1) % poly.size());

				float a0 = (float) Math.atan2(p1.y - p.y, p1.x - p.x);
				float a1 = a0 + (float) Math.PI / 2;

				float x = p.x;
				float y = p.y;
				float l = p.dst(p1);
				int n = (int) Math.ceil(l / d);

				float dd = l / (float) n;
				float dx = dd * (float) Math.cos(a0);
				float dy = dd * (float) Math.sin(a0);

				float vx = dd * (float) Math.cos(a1);
				float vy = dd * (float) Math.sin(a1);

				for (int k = 0; k < n; k++) {
					x += dx;
					y += dy;
					P.add(new Vector2(x, y));
					Q.add(new Vector2(x, y));
					V.add(new Vector2(vx, vy));
				}
			}
		}

		// Store the hatch result (assuming G.pushPolyline is defined in your context)
		for (int i = 0; i < hatches.size(); i++) {
			G.pushPolyline(hatches.get(i), G.currentStroke, 0);
		}
	}

	public static void hatchSpineSmooth(Pixmap mask, float d, Batch batch) {
		int w = mask.getWidth();
		int h = mask.getHeight();
		boolean[] im = new boolean[w * h];

		// Load mask image pixels into the im array
		for (int i = 0; i < w * h; i++) {
			int pixel = mask.getPixel(i % w, i / w);
			im[i] = ((pixel >> 16) & 0xFF) > 127;
		}

		int[] iim = new int[w * h];
		for (int i = 0; i < iim.length; i++) {
			iim[i] = im[i] ? 1 : 0;
		}

		// Find contours and approximate polygon edges
		ArrayList<ArrayList<Vector2>> contours0 = PEmbroiderTrace.findContours(iim, w, h);
		for (int i = 0; i < contours0.size(); i++) {
			contours0.set(i, PEmbroiderTrace.approxPolyDP(contours0.get(i), 1));
			contours0.set(i, PEmbroiderTrace.approxPolyDP(contours0.get(i), 3));
			contours0.set(i, G.smoothen(contours0.get(i), 0.5f, 200));
			contours0.set(i, G.resample(contours0.get(i), d, d, 0, 0));
		}

		// Reprocess mask pixels
		for (int i = 0; i < w * h; i++) {
			int pixel = mask.getPixel(i % w, i / w);
			im[i] = ((pixel >> 16) & 0xFF) > 127;
		}

		// Thinning and dilation operations on the mask
		PEmbroiderTrace.thinning(im, w, h);
		im = PEmbroiderTrace.dilate5(im, w, h);

		for (int i = 0; i < iim.length; i++) {
			iim[i] = im[i] ? 1 : 0;
		}
		ArrayList<ArrayList<Vector2>> contours = PEmbroiderTrace.findContours(iim, w, h);

		// Create a Pixmap to hold the result
		Pixmap resultPixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
		resultPixmap.setColor(com.badlogic.gdx.graphics.Color.WHITE);
		resultPixmap.fill(); // fill with white background

		// Draw contours into the Pixmap
		for (ArrayList<Vector2> contour : contours0) {
			for (int i = 0; i < contour.size(); i++) {
				Vector2 p = contour.get(i);
				Vector2 p1 = contour.get((i + 1) % contour.size());

				float a0 = MathUtils.atan2(p1.y - p.y, p1.x - p.x);
				float a1 = a0 - MathUtils.PI / 2;

				float x = (p.x + p1.x) / 2f;
				float y = (p.y + p1.y) / 2f;

				float vx = 2 * MathUtils.cos(a1);
				float vy = 2 * MathUtils.sin(a1);
				Vector2 velocity = new Vector2(vx, vy);

				// Create hatching points based on resampling
				ArrayList<Vector2> hatchPoints = new ArrayList<>();
				for (int j = 0; j < 500; j++) {
					x += velocity.x;
					y += velocity.y;

					if (x < 0 || x >= w || y < 0 || y >= h || resultPixmap.getPixel((int) x, (int) y) == com.badlogic.gdx.graphics.Color.BLACK.toIntBits()) {
						break;
					}

					hatchPoints.add(new Vector2(x, y));

					// Draw the line to the next hatch point
					resultPixmap.drawLine((int) p.x, (int) p.y, (int) x, (int) y);
				}

				// Now, add the hatching points to the batch
				Texture resultTexture = new Texture(resultPixmap);
				for (Vector2 point : hatchPoints) {
					batch.draw(new TextureRegion(resultTexture), point.x, point.y);
				}
				resultTexture.dispose(); // Dispose the texture after drawing
			}
		}

		// Final drawing and clipping for additional effects (e.g., raster effects)
		ArrayList<ArrayList<Vector2>> hpr = G.hatchParallelRaster(resultPixmap, MathUtils.PI / 2, d, 1);
		for (ArrayList<Vector2> hatch : hpr) {
			hatch = G.resample(hatch, 6, 6, 0, 0);
			if (hatch.size() < 2) {
				continue;
			}

			Vector2 p0 = hatch.get(0);
			Vector2 p1 = hatch.get(1);
			Vector2 p2 = hatch.get(hatch.size() - 2);
			Vector2 p3 = hatch.get(hatch.size() - 1);

			hatch.get(0).add(p0.cpy().sub(p1));
			hatch.get(hatch.size() - 1).add(p3.cpy().sub(p2));
		}

		// Combine all hatches and clip them with the original mask
		G.clip(hpr, mask);
		for (ArrayList<Vector2> hatch : hpr) {
			G.pushPolyline(hatch, G.currentStroke, 0);
		}
		resultPixmap.dispose(); // Dispose the Pixmap after use

	}
	 public static void hatchSpineVF(Pixmap mask, float d) {
		 hatchSpineVF(mask,d,2000);
	 }

	public static void hatchSpineVF(Pixmap mask, float d, int maxVertices) {
		int w = mask.getWidth();
		int h = mask.getHeight();
		boolean[] im = new boolean[w * h];

		// Read mask pixels into im array (Processing used loadPixels and pixels[])
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int pixel = mask.getPixel(x, y);
				// Here we check the red channel; adjust as needed.
				im[y * w + x] = ((pixel >> 16) & 0xFF) < 127;
			}
		}

		// Build a boolean mask jm (inverse of im)
		boolean[] jm = new boolean[w * h];
		for (int i = 0; i < im.length; i++) {
			jm[i] = ((mask.getPixel(i % w, i / w) >> 16) & 0xFF) > 127;
		}

		// Apply thinning and dilation functions (assumed ported to LibGDX)
		PEmbroiderTrace.thinning(jm, w, h);
		jm = PEmbroiderTrace.dilate5(jm, w, h);

		// Convert boolean mask to int array (1 if true, 0 if false)
		int[] ijm = new int[w * h];
		for (int i = 0; i < im.length; i++) {
			ijm[i] = jm[i] ? 1 : 0;
		}

		// Find contours from the processed mask (returns ArrayList of ArrayList<Vector2>)
		ArrayList<ArrayList<Vector2>> contours = PEmbroiderTrace.findContours(ijm, w, h);
		for (int i = 0; i < contours.size(); i++) {
			contours.set(i, PEmbroiderTrace.approxPolyDP(contours.get(i), 1));
		}

		// Re-read the original mask pixels into im (invert the test)
		for (int i = 0; i < mask.getWidth() * mask.getHeight(); i++) {
			int pixel = mask.getPixel(i % w, i / w);
			im[i] = ((pixel >> 16) & 0xFF) > 127;
		}

		// Process the im mask with thinning and dilation again
		PEmbroiderTrace.thinning(im, w, h);
		im = PEmbroiderTrace.dilate5(im, w, h);

		// Build a new int array from the updated boolean mask
		for (int i = 0; i < ijm.length; i++) {
			ijm[i] = im[i] ? 1 : 0;
		}
		ArrayList<ArrayList<Vector2>> contours0 = PEmbroiderTrace.findContours(ijm, w, h);
		for (int i = 0; i < contours0.size(); i++) {
			contours0.set(i, PEmbroiderTrace.approxPolyDP(contours0.get(i), 2));
			G.pushPolyline(contours0.get(i), G.currentStroke, 0);
		}

		// Compute a distance transform on contours0 (assumed implemented)
		float[] dt = G.perfectDistanceTransform(contours0, w, h);

		// Build lists for hatch generation
		ArrayList<Vector2> P = new ArrayList<>();
		ArrayList<Float> Q = new ArrayList<>();
		ArrayList<Vector2> V = new ArrayList<>();

		// Process the contours to create hatch spine in one direction (negative Q values)
		for (int i = 0; i < contours.size(); i++) {
			ArrayList<Vector2> poly = contours.get(i);
			for (int j = 0; j < poly.size(); j++) {
				Vector2 p  = poly.get(j);
				Vector2 p1 = poly.get((j + 1) % poly.size());
				float a0 = (float) Math.atan2(p1.y - p.y, p1.x - p.x);
				float a1 = a0 + (float)Math.PI / 2;
				float x = p.x;
				float y = p.y;
				float vx = 2 * (float) Math.cos(a1);
				float vy = 2 * (float) Math.sin(a1);
				float l = p.dst(p1);
				int n = (int) Math.ceil(l / d);
				float dd = l / (float)n;
				float dx = dd * (float) Math.cos(a0);
				float dy = dd * (float) Math.sin(a0);
				for (int k = 0; k < n; k++) {
					x += dx;
					y += dy;
					P.add(new Vector2(x, y));
					Q.add(-1f);
					V.add(new Vector2(vx, vy));
				}
			}
		}

		// Process the contours to create hatch spine in the opposite direction (positive Q values)
		for (int i = 0; i < contours0.size(); i++) {
			ArrayList<Vector2> poly = contours0.get(i);
			for (int j = 0; j < poly.size(); j++) {
				Vector2 p  = poly.get(j);
				Vector2 p1 = poly.get((j + 1) % poly.size());
				float a0 = (float) Math.atan2(p1.y - p.y, p1.x - p.x);
				float a1 = a0 - (float)Math.PI / 2;
				float x = p.x;
				float y = p.y;
				float vx = 2 * (float) Math.cos(a1);
				float vy = 2 * (float) Math.sin(a1);
				float l = p.dst(p1);
				int n = (int) Math.ceil(l / d);
				float dd = l / (float)n;
				float dx = dd * (float) Math.cos(a0);
				float dy = dd * (float) Math.sin(a0);
				for (int k = 0; k < n; k++) {
					x += dx;
					y += dy;
					P.add(new Vector2(x, y));
					Q.add(1f);
					V.add(new Vector2(vx, vy));
				}
			}
		}

		// Adjust distance transform values based on the original mask (set dt=0 where im is true)
		for (int i = 0; i < jm.length; i++) {
			dt[i] = im[i] ? 0 : dt[i];
		}

		int minVertices = 3;
		ArrayList<ArrayList<Vector2>> polys = new ArrayList<>();

		// Create a new Pixmap for drawing hatch lines
		Pixmap pg2 = new Pixmap(mask.getWidth(), mask.getHeight(), Pixmap.Format.RGBA8888);
		pg2.setColor(Color.WHITE);
		pg2.fill();  // Background white
		// Draw the original mask on pg2
		pg2.drawPixmap(mask, 0, 0);
		// Set drawing parameters (simulate stroke, strokeWeight, strokeJoin)
		// These are not built-in for Pixmap, so you must implement drawing of lines accordingly.

		// Now, using the list P, Q, and V, generate hatch curves:
		ArrayList<ArrayList<Vector2>> hatches = new ArrayList<>();
		for (int i = 0; i < P.size(); i++) {
			float x = P.get(i).x;
			float y = P.get(i).y;
			float[] ddt = (Q.get(i) < 0) ? dt : dt;  // This line is ambiguous; adjust if needed
			ArrayList<Vector2> poly = new ArrayList<>();
			int hate = 0;
			for (int j = 0; j < maxVertices; j++) {
				poly.add(new Vector2(x, y));
				Vector2 _p0 = new Vector2(x, y);
				Vector2 _p1 = new Vector2(x, y);
				float t0 = ddt[j];
				float t1 = ddt[Math.min(j + 1, ddt.length - 1)];
				float tr = MathUtils.random(t0, t1);
				float t = MathUtils.lerp(t0, tr, MathUtils.random());
				// Here, we combine _p0 and _p1 based on t.
				// This is a placeholder operation; you may need to define your own blending.
				poly.add(_p0.scl(1 - t).add(_p1.scl(t)));
				// hate not modified, placeholder
			}
			if (poly.size() < minVertices || poly.get(0).dst(poly.get(poly.size() - 1)) < 5) {
				continue;
			}
			// Draw the poly into pg2
			// Simulate beginShape()/endShape() by drawing lines between consecutive vertices
			for (int j = 0; j < poly.size() - 1; j++) {
				pg2.drawLine((int) poly.get(j).x, (int) poly.get(j).y,
						(int) poly.get(j + 1).x, (int) poly.get(j + 1).y);
			}
			polys.add(poly);
		}

		// Apply filters on pg2: simulate erode and dilate operations.
		pg2 = applyErode(pg2, 2);
		pg2 = applyDilate(pg2, 1);

		// Invert the image in pg2 (if needed)
		pg2 = applyInvert(pg2);

		// Optionally, find additional contours from pg2 and process them:
		ArrayList<ArrayList<Vector2>> hpr = G.hatchParallelRaster(pg2, (float)Math.PI/2, d, 2);
		for (int i = 0; i < hpr.size(); i++) {
			hpr.set(i, G.resample(hpr.get(i), 6, 6, 0, 0));
			if (hpr.get(i).size() < 2) {
				continue;
			}
			Vector2 p0 = hpr.get(i).get(0);
			Vector2 p1 = hpr.get(i).get(1);
			Vector2 p2 = hpr.get(i).get(hpr.get(i).size() - 2);
			Vector2 p3 = hpr.get(i).get(hpr.get(i).size() - 1);
			hpr.get(i).get(0).add(p0.cpy().sub(p1));
			hpr.get(i).get(hpr.get(i).size() - 1).add(p3.cpy().sub(p2));
		}
		hatches.addAll(hpr);

		// Clip the hatches with the original mask
		G.clip(hatches, mask);

		// Finally, push the hatches (draw them, etc.)
		for (int i = 0; i < hatches.size(); i++) {
			G.pushPolyline(hatches.get(i), G.currentStroke, 0);
		}
	}
	// Erode function
	public static Pixmap applyErode(Pixmap pixmap, int iterations) {
		Pixmap eroded = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), pixmap.getFormat());
		for (int i = 0; i < iterations; i++) {
			for (int x = 1; x < pixmap.getWidth() - 1; x++) {
				for (int y = 1; y < pixmap.getHeight() - 1; y++) {
					int color = getMinColorAround(pixmap, x, y);
					eroded.drawPixel(x, y, color);
				}
			}
			pixmap = new Pixmap(eroded.getPixels());  // Update pixmap for next iteration
		}
		return eroded;
	}

	// Dilate function
	public static Pixmap applyDilate(Pixmap pixmap, int iterations) {
		Pixmap dilated = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), pixmap.getFormat());
		for (int i = 0; i < iterations; i++) {
			for (int x = 1; x < pixmap.getWidth() - 1; x++) {
				for (int y = 1; y < pixmap.getHeight() - 1; y++) {
					int color = getMaxColorAround(pixmap, x, y);
					dilated.drawPixel(x, y, color);
				}
			}
			pixmap = new Pixmap(dilated.getPixels());  // Update pixmap for next iteration
		}
		return dilated;
	}

	// Invert function
	public static Pixmap applyInvert(Pixmap pixmap) {
		Pixmap inverted = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), pixmap.getFormat());
		for (int x = 0; x < pixmap.getWidth(); x++) {
			for (int y = 0; y < pixmap.getHeight(); y++) {
				int color = pixmap.getPixel(x, y);
				inverted.drawPixel(x, y, ~color);  // Invert the color
			}
		}
		return inverted;
	}

	// Helper functions to get the min and max color around a pixel (for erode and dilate)
	private static int getMinColorAround(Pixmap pixmap, int x, int y) {
		int minColor = Integer.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				minColor = Math.min(minColor, pixmap.getPixel(x + dx, y + dy));
			}
		}
		return minColor;
	}

	private static int getMaxColorAround(Pixmap pixmap, int x, int y) {
		int maxColor = Integer.MIN_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				maxColor = Math.max(maxColor, pixmap.getPixel(x + dx, y + dy));
			}
		}
		return maxColor;
	}
}
