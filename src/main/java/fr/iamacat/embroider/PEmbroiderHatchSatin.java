package fr.iamacat.embroider;

import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import fr.iamacat.utils.PConstants;

public class PEmbroiderHatchSatin {
	public static PEmbroiderGraphics G;
	 public static void setGraphics(PEmbroiderGraphics _G) {
		 G = _G;
	 }

	public static class Pt{
		public int x;
		public int y;
		public Pt(int _x, int _y){
			x = _x;
			y = _y;
		}
		Pt(Pt p){
			x = p.x;
			y = p.y;
		}
		@Override
        public String toString(){
			return "("+x+","+y+")";
		}
	}

	public static class Im{
		int[] data;
		int w;
		int h;
		Im (int _w, int _h){
			data = new int[_w*_h];
			w = _w;
			h = _h;
		}
		public Im(Pixmap pixmap) {
			w = pixmap.getWidth();
			h = pixmap.getHeight();
			data = new int[w * h];

			// Loop through all pixels in the pixmap and convert them to binary data
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					// Get pixel at (x, y)
					int pixel = pixmap.getPixel(x, y);

					// Check the brightness of the pixel (using red, green, blue values)
					// Convert to binary: 0 if dark, 1 if bright
					int brightness = (pixel & 0xFF);  // Extract the red component (or use any channel)
					data[y * w + x] = (brightness > 128) ? 1 : 0;  // Binary thresholding
				}
			}
		}
		Im (Im im){
			w = im.w;
			h = im.h;
			data = new int[w*h];
			for (int i = 0; i < w*h; i++){
				data[i] = im.data[i];
			}
		}
		int get(int x, int y){
			if (x < 0 || x >= w || y < 0 || y >= h){
				return 0;
			}
			return data[y*w+x];
		}
		int get(Pt p){
			return get(p.x,p.y);
		}
		boolean isOn(int x, int y){
			return get(x,y)>0;
		}
		boolean isOn(Pt p){
			return isOn(p.x,p.y);
		}
		void set(int x, int y, int v){
			data[y*w+x] = v;
		}
		void set(Pt p, int v){
			set(p.x,p.y,v);
		}
		public Pixmap toPixmap() {
			// Create a Pixmap with width and height
			Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);

			// Loop through each pixel in the data array
			for (int i = 0; i < data.length; i++) {
				// Convert the data value to a grayscale intensity (0-255)
				int g = (data[i] * 127 + 128) & 255;

				// Set the pixel at position (x, y)
				int x = i % w;
				int y = i / w;

				// Set the pixel color in the Pixmap (ARGB format)
				Color color = new Color(g / 255f, g / 255f, g / 255f, 1f); // Normalize to 0-1 for each color channel
				pixmap.setColor(color);
				pixmap.drawPixel(x, y);  // Draw the pixel
			}

			return pixmap;
		}

	}

	static Pt hiPt(Im im){
		for (int i = 0; i < im.h; i++){
			for (int j = 0; j < im.w; j++){
				if (im.isOn(j,i)){
					return new Pt(j,i);
				}
			}
		}
		return null;
	}

	static Pt loPt(Im im){
		for (int i = im.h-1; i>=0; i--){
			for (int j = 0; j < im.w; j++){
				if (im.isOn(j,i)){
					return new Pt(j,i);
				}
			}
		}
		return null;
	}

	static boolean floodfill(Im prev, Im src, Im dst, Pt p0, int[] areaOut){
		boolean t = false;
		if (prev.get(p0) == -1){
			t = true;
		}
		if (src.get(p0) != 1){
			return t;
		}
		if (dst.get(p0) == 1){
			return t;
		}
		dst.set(p0,1);
		src.set(p0,0);

		if (areaOut != null){
			areaOut[0]++;
		}

		t |= floodfill(prev,src,dst,new Pt(p0.x-1,p0.y),areaOut);
		t |= floodfill(prev,src,dst,new Pt(p0.x+1,p0.y),areaOut);
		t |= floodfill(prev,src,dst,new Pt(p0.x,p0.y-1),areaOut);
		t |= floodfill(prev,src,dst,new Pt(p0.x,p0.y+1),areaOut);

		return t;
	}

	static boolean floodfillQ(Im prev, Im src, Im dst, Pt p0, int[] areaOut){
		boolean t = false;
		if (prev.get(p0) == -1){
			t = true;
		}
		if (src.get(p0) != 1){
			return t;
		}
		if (dst.get(p0) == 1){
			return t;
		}
		ArrayList<Pt> Q = new ArrayList<Pt>();
		Q.add(new Pt(p0));
		while (Q.size()>0){
			if (areaOut != null){
				areaOut[0]++;
			}

			Pt n = Q.get(0);
			Q.remove(0);
			Pt l = new Pt(n.x-1,n.y);
			Pt r = new Pt(n.x+1,n.y);
			Pt u = new Pt(n.x,n.y-1);
			Pt d = new Pt(n.x,n.y+1);
			if (src.get(l)==1){
				dst.set(l,1);
				src.set(l,0);
				Q.add(l);

			}
			if (src.get(r)==1){
				dst.set(r,1);
				src.set(r,0);
				Q.add(r);
			}
			if (src.get(u)==1){
				dst.set(u,1);
				src.set(u,0);
				Q.add(u);
			}
			if (src.get(d)==1){
				dst.set(d,1);
				src.set(d,0);
				Q.add(d);
			}
			if (prev.get(l) == -1 || 
					prev.get(r) == -1 || 
					prev.get(u) == -1 || 
					prev.get(d) == -1 ){
				t = true;
			}
		}
		return t;
	}


	static boolean floodBridgeHoleAt(Im src, Im cache, Pt p0){

		Im dst = new Im(src.w,src.h);
		ArrayList<Pt> Q = new ArrayList<Pt>();
		ArrayList<Pt> P = new ArrayList<Pt>();
		Q.add(new Pt(p0));
		dst.set(p0,1);
		Pt leftmost = new Pt(p0);
		while (Q.size()>0){

			Pt n = Q.get(0);
			Q.remove(0);
			P.add(new Pt(n));
			//if (n.x < leftmost.x){
			//  leftmost.x = n.x;
			//  leftmost.y = n.y;
			//}
			if (n.x <= 0 || n.x >= src.w-1 || n.y <= 0 || n.y >= src.h-1){
				for (int i = 0; i < P.size(); i++){
					cache.set(P.get(i),1);
				}
				return false;
			}
			if (cache.get(n) == 1){
				for (int i = 0; i < P.size(); i++){
					cache.set(P.get(i),1);
				}
				return false;
			}
			Pt l = new Pt(n.x-1,n.y);
			Pt r = new Pt(n.x+1,n.y);
			Pt u = new Pt(n.x,n.y-1);
			Pt d = new Pt(n.x,n.y+1);
			if (dst.get(l)==0 && src.get(l)==0){
				dst.set(l,1);
				Q.add(l);
			}
			if (dst.get(r)==0 && src.get(r)==0){
				dst.set(r,1);
				Q.add(r);
			}
			if (dst.get(u)==0 && src.get(u)==0){
				dst.set(u,1);
				Q.add(u);
			}
			if (dst.get(d)==0 && src.get(d)==0){
				dst.set(d,1);
				Q.add(d);
			}
		}
		for (int i = 0; i < src.h; i++){
			leftmost.y --;
			if (leftmost.y < 0 || 
					cache.get(leftmost) == 1 || 
					cache.get(leftmost.x-1,leftmost.y+1) == 1 ||
					cache.get(leftmost.x-1,leftmost.y+1) == 1 
					){
				break;
			}
			cache.set(leftmost,1);
			src.set(leftmost,0);
		}
		for (int i = 0; i < P.size(); i++){
			cache.set(P.get(i),1);
		}
		return true;
	}



	static int findArea(Im im){
		int a = 0;
		for (int i = 0; i < im.data.length; i++){
			if (im.data[i] > 0){
				a ++;
			}
		}
		return a;  
	}

	public static ArrayList<Pt> satinStitches(Im prevIm, Im im, Pt p0, int d){
		//im.toImage().save(random(1)+".png");

		ArrayList<Pt> pts = new ArrayList<Pt>();
		ArrayList<Pt> walk = new ArrayList<Pt>();
		pts.add(new Pt(p0));
		im.set(p0,-1);

		Pt p = new Pt(p0);
		int belowOn = -1;
		int belowOff = -1;
		int aboveOn = -1;

		int lineStart = p.x;

		boolean invBranch = false;

		for (int i = 0; i < Integer.MAX_VALUE; i++){

			if (im.get(p.x,p.y) != 0){
				if (im.isOn(p.x,p.y+d)){
					if (belowOn < 0){
						belowOn = p.x;
						//fill(0,255,64);
						//rect(p.x/reso,p.y/reso,1/reso,1/reso);
						//save("???.png"); 
					}else if (belowOff >= 0 && !invBranch){
						Pt q0 = new Pt(belowOn,p.y+d);
						Im mask = new Im(im.w,im.h);

						Im old = new Im(im);

						int[] area = {0};
						boolean touch = floodfillQ(prevIm,im,mask,q0,area);
						int at = findArea(im);
						//println(touch,area[0],at);
						if (touch || area[0] > at){
							invBranch = true;
							im = old;
						}else{

							if (d > 0){
								q0 = loPt(mask);
							}else{
								q0 = hiPt(mask);
							}
							if (q0!=null){
								pts.addAll(satinStitches(im,mask,q0,-d));
							}
							//pts.addAll(satinStitches(im,new Pt(p),abs(d)));
							//break;

							belowOn = -1;
							belowOff = -1;

						}
					}
				}else{
					if (belowOn >= 0 && belowOff < 0){
						belowOff = p.x;
					}
				}

				if (im.isOn(p.x,p.y-d)){
					if (aboveOn < 0){
						aboveOn = p.x;
					}
				}else{
					if (aboveOn >= 0){
						Pt q0 = new Pt(aboveOn,p.y-d);
						Im mask = new Im(im.w,im.h);
						floodfillQ(prevIm,im,mask,q0,null);

						if (d > 0){
							q0 = hiPt(mask);
						}else{
							q0 = loPt(mask);
						}
						if (q0 != null){
							pts.addAll(satinStitches(im,mask,q0,d));
						}
						aboveOn = -1;
					}
				}
			}


			p.x += 1;
			if (!im.isOn(p)){
				int lineEnd = p.x;
				if (invBranch){
					Pt q0 = new Pt(p.x,p.y+d);
					while(!im.isOn(q0)){
						q0.x--;
					}
					Im mask = new Im(im.w,im.h);
					floodfillQ(prevIm,im,mask,q0,null);

					if (d > 0){
						q0 = loPt(mask);
					}else{
						q0 = hiPt(mask);
					}
					if (q0 != null){
						pts.addAll(satinStitches(im,mask,q0,-d));
					}
					p.x = belowOn;
					p.y += d;

					while (im.isOn(p)){
						p.x -= 1;
					}

					belowOn = -1;
					aboveOn = -1;
					belowOff = -1;
					invBranch = false;

					continue;
				}else if (aboveOn >= 0){
					Pt q0 = new Pt(p.x,p.y-d);
					while(!im.isOn(q0)){
						q0.x--;
					}
					Im mask = new Im(im.w,im.h);
					floodfillQ(prevIm,im,mask,q0,null);

					if (d > 0){
						q0 = hiPt(mask);
					}else{
						q0 = loPt(mask);
					}
					if (q0 != null){
						pts.addAll(satinStitches(im,mask,q0,d));
					}

					belowOn = -1;
					aboveOn = -1;
					belowOff = -1;

				}
				p.y += d;
				belowOn = -1;
				belowOff = -1;
				aboveOn = -1;
				if (p.y >= im.h || p.y < 0){
					break;
				}
				while (im.isOn(p)){
					p.x+=1;
				}
				boolean once = false;
				for (int j = 0; j < im.w; j++){
					p.x -= 1;
					boolean on = im.isOn(p);
					if (on && !once){
						once = true;
					}
					if (!on && once){

						walk.add(new Pt((lineStart+lineEnd)/2,p.y));
						//walk.add(new Pt(p.x+2,p.y));
						lineStart = p.x+1;
						invBranch = false;
						break;
					}
				}
			}else{
				pts.add(new Pt(p));
				im.set(p,-1);
			}
		}
		for (int i = 0; i < walk.size(); i++){
			pts.add(0,walk.get(i));
		}
		return pts;
	}
	

	static ArrayList<Pt> boustrophedonStitches(Im prevIm, Im im, Pt p0, int d){
		//im.toImage().save(random(1)+".png");

		ArrayList<Pt> pts = new ArrayList<Pt>();
		ArrayList<Pt> walk = new ArrayList<Pt>();
		pts.add(new Pt(p0));
		im.set(p0,-1);

		Pt p = new Pt(p0);
		int belowOn = -1;
		int belowOff = -1;
		int aboveOn = -1;

		int lineStart = p.x;

		boolean invBranch = false;
		int dx = 1;

		for (int i = 0; i < Integer.MAX_VALUE; i++){

			if (im.get(p.x,p.y) != 0){
				if (im.isOn(p.x,p.y+d)){
					if (belowOn < 0){
						belowOn = p.x;
						//fill(0,255,64);
						//rect(p.x/reso,p.y/reso,1/reso,1/reso);
						//save("???.png"); 
					}else if (belowOff >= 0 && !invBranch){
						Pt q0 = new Pt(belowOn,p.y+d);
						Im mask = new Im(im.w,im.h);

						Im old = new Im(im);

						int[] area = {0};
						boolean touch = floodfillQ(prevIm,im,mask,q0,area);
						int at = findArea(im);
						//println(touch,area[0],at,touch || area[0] > at,q0);
						if (touch || area[0] > at){
							invBranch = true;
							im = old;
						}else{

							if (d > 0){
								q0 = loPt(mask);
							}else{
								q0 = hiPt(mask);
							}
							if (q0!=null){
								pts.addAll(boustrophedonStitches(im,mask,q0,-d));
							}
							//pts.addAll(satinStitches(im,new Pt(p),abs(d)));
							//break;

							belowOn = -1;
							belowOff = -1;

						}
					}
				}else{
					if (belowOn >= 0 && belowOff < 0){
						belowOff = p.x;
					}
				}

				if (im.isOn(p.x,p.y-d)){
					if (aboveOn < 0){
						aboveOn = p.x;
					}
				}else{
					if (aboveOn >= 0){
						Pt q0 = new Pt(aboveOn,p.y-d);
						Im mask = new Im(im.w,im.h);
						floodfillQ(prevIm,im,mask,q0,null);

						if (d > 0){
							q0 = hiPt(mask);
						}else{
							q0 = loPt(mask);
						}
						if (q0 != null){
							pts.addAll(boustrophedonStitches(im,mask,q0,d));
						}
						aboveOn = -1;
					}
				}
			}


			p.x += dx;
			if (!im.isOn(p)){
				int lineEnd = p.x;
				if (invBranch){
					Pt q0 = new Pt(p.x,p.y+d);
					while(!im.isOn(q0)){
						q0.x-=dx;
					}
					Im mask = new Im(im.w,im.h);
					floodfillQ(prevIm,im,mask,q0,null);

					if (d > 0){
						q0 = loPt(mask);
					}else{
						q0 = hiPt(mask);
					}
					if (q0 != null){
						pts.addAll(boustrophedonStitches(im,mask,q0,-d));
					}
					p.x = belowOn;
					//p.y += d;

					while (im.isOn(p)){
						p.x -= 1;
					}

					belowOn = -1;
					aboveOn = -1;
					belowOff = -1;
					invBranch = false;

					continue;
				}else if (aboveOn >= 0){
					Pt q0 = new Pt(p.x,p.y-d);
					while(!im.isOn(q0)){
						q0.x-=dx;
					}
					Im mask = new Im(im.w,im.h);
					floodfillQ(prevIm,im,mask,q0,null);

					if (d > 0){
						q0 = hiPt(mask);
					}else{
						q0 = loPt(mask);
					}
					if (q0 != null){
						pts.addAll(boustrophedonStitches(im,mask,q0,d));
					}

					belowOn = -1;
					aboveOn = -1;
					belowOff = -1;

				}
				p.y += d;
				belowOn = -1;
				belowOff = -1;
				aboveOn = -1;
				if (p.y >= im.h || p.y < 0){
					break;
				}
				while (!im.isOn(p) && 0 < p.x && p.x < im.w){
					p.x -= dx;
				}
				while (im.isOn(p)){
					p.x+=dx;
				}
				if (0 < p.x && p.x < im.w){
					walk.add(new Pt((lineStart+lineEnd)/2,p.y));
				}
				lineStart = p.x;
				invBranch = false;
				dx = -dx;
			}else{
				pts.add(new Pt(p));
				im.set(p,-1);
			}
		}
		for (int i = 0; i < walk.size(); i++){
			pts.add(0,walk.get(i));
		}
		return pts;
	}


	static void remove1pxHolesAndIslands(Im im){
		for (int i = 0; i < im.h; i++){
			for (int j = 0; j < im.w; j++){
				if (im.get(j,i) == 0){
					if (im.get(j-1,i) == 1
							&&im.get(j+1,i) == 1
							&&im.get(j,i-1) == 1
							&&im.get(j,i+1) == 1
							){
						im.set(j,i,1);
					}
				}else{
					if (im.get(j-1,i) == 0
							&&im.get(j+1,i) == 0
							&&im.get(j,i-1) == 0
							&&im.get(j,i+1) == 0
							){
						im.set(j,i,0);
					}
				}
			}
		}
	}
	static void removeNpxHolesAndIslands(Im im, int n){
		for (int i = 0; i < im.h; i++){
			for (int j = 0; j < im.w; j++){
				int x = j;
				if (im.get(x-1,i) == 1) {
					while (im.get(x,i) == 0 && im.get(x,i-1)==1 && im.get(x,i+1)==1){
						x++;
						if ( im.get(x+1,i) == 1) {
							break;
						}
					}
					if (x < j+n) {
						for (int k = j; k < x; k++) {
							im.set(k,i,1);
						}
					}
				}else {
					while (im.get(x,i) == 1 && im.get(x,i-1)==0 && im.get(x,i+1)==0){
						x++;
						if ( im.get(x+1,i) == 0) {
							break;
						}
					}
					if (x < j+n) {
						for (int k = j; k < x; k++) {
							im.set(k,i,0);
						}
					}
				}
			}
		}
	}


	static void bridgeHoles(Im im){
		Im cache = new Im(im.w,im.h);
		for (int i = 0; i < im.h; i++){
			boolean seenOn = false;
			for (int j = 0; j < im.w; j++){
				Pt p = new Pt(j,i);
				if (im.get(p) == 1){
					seenOn = true;
				}else{
					if (!seenOn){
						cache.set(p,1);
					}else{
						floodBridgeHoleAt(im,cache,p);
					}
				}
			}
		}
		//cache.toImage().save("?.png");
	}

	static ArrayList<ArrayList<Pt>> satinStitchesMultiple(Im im){
		Im cpy = new Im(im);
		Im src = new Im(im);
		ArrayList<ArrayList<Pt>> ret = new ArrayList<ArrayList<Pt>>();
		ArrayList<Pt> pts;
		if (G.SATIN_MODE != PEmbroiderGraphics.BOUSTROPHEDON) {
			pts = satinStitches(cpy,src,hiPt(im),1);
		}else {
			pts = boustrophedonStitches(cpy,src,hiPt(im),1);
		}
		ret.add(pts);
		for (int i = 0; i < pts.size(); i++){
			cpy.set(pts.get(i),0);
		}
		boolean redo = false;
		for (int i = 0; i < cpy.data.length; i++){
			if (cpy.data[i] > 0){
				redo = true;
				break;
			}
		}
		if (redo){
			ret.addAll(satinStitchesMultiple(cpy));
		}
		return ret;
	}

	public static ArrayList<ArrayList<Vector2>> hatchSatinRaster(Pixmap im, float d, int n) {
		// Create a new Pixmap for processing the image
		int newWidth = (int) Math.ceil(im.getWidth() / 2f);
		int newHeight = (int) Math.ceil(im.getHeight() / d);

		// Create a new Pixmap with the resized dimensions
		Pixmap pg = new Pixmap(newWidth, newHeight, Pixmap.Format.RGBA8888);

		// Resize the image (scaling down to fit into the new dimensions)
		pg.drawPixmap(im, 0, 0, 0, 0, im.getWidth(), im.getHeight(), newWidth, newHeight);

		// Create an Im object (you should implement this or adapt as per libGDX equivalent)
		Im srcImg = new Im(pg);  // Assuming 'Im' is a class that processes the Pixmap in your code

		// Remove holes and islands (implement or adapt to libGDX)
		removeNpxHolesAndIslands(srcImg, 3);

		// Bridge holes (implement or adapt to libGDX)
		bridgeHoles(srcImg);

		// Process satin stitches (implement or adapt to libGDX)
		ArrayList<ArrayList<Pt>> pts = satinStitchesMultiple(srcImg);

		ArrayList<ArrayList<Vector2>> ret = new ArrayList<>();

		// Resample the stitches
		for (int i = 0; i < pts.size(); i++) {
			ArrayList<ArrayList<Vector2>> p;
			if (G.SATIN_MODE != PEmbroiderGraphics.BOUSTROPHEDON) {
				p = resampleSatinStitches(pts.get(i), n);  // Assuming this function works as expected
			} else {
				p = resampleBoustrophedonStitches(pts.get(i), n);  // Assuming this function works as expected
			}

			for (int j = p.size() - 1; j >= 0; j--) {
				if (p.get(j).size() <= 2) {
					p.remove(j);
					continue;
				}

				// Rescale the coordinates to the original Pixmap size
				for (int k = 0; k < p.get(j).size(); k++) {
					p.get(j).get(k).x = (p.get(j).get(k).x + 0.5f) * (float)im.getWidth() / newWidth;
					p.get(j).get(k).y = (p.get(j).get(k).y + 0.5f) * (float)im.getHeight() / newHeight;
				}
			}

			ret.addAll(p);
		}

		return ret;
	}

	public static ArrayList<ArrayList<Vector2>> hatchSatinAngledRaster(Pixmap im, float ang, float d, int n) {
		if (Math.abs(ang) == 0.00001f) {
			return hatchSatinRaster(im, d, n);
		}

		// Load the pixels of the Pixmap
		int xmin = Integer.MAX_VALUE;
		int xmax = Integer.MIN_VALUE;
		int ymin = Integer.MAX_VALUE;
		int ymax = Integer.MIN_VALUE;

		for (int i = 0; i < im.getHeight(); i++) {
			for (int j = 0; j < im.getWidth(); j++) {
				if ((im.getPixel(j, i) & 0xFF) > 128) {  // Check if the pixel is bright enough
					xmin = Math.min(j, xmin);
					ymin = Math.min(i, ymin);
					xmax = Math.max(j, xmax);
					ymax = Math.max(i, ymax);
				}
			}
		}

		int rw = xmax - xmin;
		int rh = ymax - ymin;

		// Calculate the angle based on the bounding box aspect ratio
		float a0 = (float) Math.atan2(rh, rw);

		// Calculate the diagonal and the max horizontal and vertical distances
		float diag = (float) Math.hypot(rw / 2, rh / 2);
		float hh = (float) (Math.max(Math.abs(Math.sin(ang - a0)), Math.abs(Math.sin(ang + a0))) * diag);
		float ww = (float) (Math.max(Math.abs(Math.cos(ang - a0)), Math.abs(Math.cos(ang + a0))) * diag);

		int w = (int) Math.ceil(ww * 2) + 4;
		int h = (int) Math.ceil(hh * 2) + 4;
		int px = (w - im.getWidth()) / 2;
		int py = (h - im.getHeight()) / 2;

		// Create a new Pixmap for rotated image
		Pixmap rotatedPixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
		rotatedPixmap.setColor(0, 0, 0, 1); // Set background to black
		rotatedPixmap.fill();

		// Rotate and copy the original Pixmap onto the rotated one
		rotatedPixmap.drawPixmap(im, px, py);

		// Now apply the rotation
		float costh = (float) Math.cos(-ang);
		float sinth = (float) Math.sin(-ang);

		ArrayList<ArrayList<Vector2>> pts = hatchSatinRaster(rotatedPixmap, d, n);

		for (int i = 0; i < pts.size(); i++) {
			for (int j = 0; j < pts.get(i).size(); j++) {
				// Transform the coordinates back from the rotated space
				float dx = pts.get(i).get(j).x - w / 2;
				float dy = pts.get(i).get(j).y - h / 2;
				pts.get(i).get(j).x = -px + w / 2 + (dx * costh - dy * sinth);
				pts.get(i).get(j).y = -py + h / 2 + (dx * sinth + dy * costh);
			}
		}
		return pts;
	}


	public static ArrayList<ArrayList<Vector2>> resampleSatinStitches(ArrayList<Pt> pts, int n){
		
		ArrayList<ArrayList<Vector2>> ret = new ArrayList<ArrayList<Vector2>>();

		for (int i = 0; i < pts.size(); i++) {
			if (i == 0) {
				ret.add(new ArrayList<Vector2>());
				ret.get(0).add(new Vector2(pts.get(i).x, pts.get(i).y));
				continue;
			}
			if (i != pts.size()-1 && pts.get(i).y == pts.get(i-1).y && pts.get(i).y == pts.get(i+1).y && Math.abs(pts.get(i).x-pts.get(i-1).x) == 1 && pts.get(i+1).x-pts.get(i).x == pts.get(i).x-pts.get(i-1).x) {
				int hn = (int)Math.ceil(G.SATIN_RESAMPLING_OFFSET_FACTOR * ((float)pts.get(i).y * 2) * (float)n);
				if ((pts.get(i).x+hn) % n == 0) {
					ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
				}
			}else if (Math.abs(pts.get(i).y - pts.get(i-1).y) == 1 && pts.get(i-1).x-pts.get(i).x > 2) {
//
				for (int j = pts.get(i-1).x-1; j > pts.get(i).x; j--) {
					int hn = (int)Math.ceil(G.SATIN_RESAMPLING_OFFSET_FACTOR * ((float)pts.get(i).y * 2 + 1) * (float)n);
					if ((j+hn)%n == 0) {
						float t = 0.5f;
						if (G.SATIN_MODE != PEmbroiderGraphics.SIGSAG) {
							t = (float)(j-pts.get(i).x)/(float)(pts.get(i-1).x-pts.get(i).x);
						}
						float y = (float)pts.get(i).y * (1-t) + (float)pts.get(i-1).y * t;
						ret.get(ret.size()-1).add(new Vector2(j,y));
					}
				}
				ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
			}else if (i != pts.size()-1 && Math.abs(pts.get(i).y - pts.get(i-1).y) == 1 && pts.get(i+1).y - pts.get(i).y == pts.get(i).y - pts.get(i-1).y) {
				if (pts.get(i).y % 2 == 0) {
					ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
				}
			}else if (Math.abs(pts.get(i).y - pts.get(i-1).y) > 8 || Math.abs(pts.get(i).x - pts.get(i-1).x) > 8){
//				ret.add(new ArrayList<Vector2>());
				ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
			}else {
				ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
			}
		}
		
		return ret;
	}
	
	public static ArrayList<ArrayList<Vector2>> resampleBoustrophedonStitches(ArrayList<Pt> pts, int n){
		
		ArrayList<ArrayList<Vector2>> ret = new ArrayList<ArrayList<Vector2>>();

		for (int i = 0; i < pts.size(); i++) {
			if (i == 0) {
				ret.add(new ArrayList<Vector2>());
				ret.get(0).add(new Vector2(pts.get(i).x, pts.get(i).y));
				continue;
			}
			if (i != pts.size()-1 && pts.get(i).y == pts.get(i-1).y && pts.get(i).y == pts.get(i+1).y && Math.abs(pts.get(i).x-pts.get(i-1).x) == 1 && pts.get(i+1).x-pts.get(i).x == pts.get(i).x-pts.get(i-1).x) {
				int hn = (int)Math.ceil(G.SATIN_RESAMPLING_OFFSET_FACTOR * (float)pts.get(i).y * (float)n);
				
				if ((pts.get(i).x+hn) % n == 0) {
					ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
				}
				
			}else if (i != pts.size()-1 && Math.abs(pts.get(i).y - pts.get(i-1).y) == 1 && pts.get(i+1).y - pts.get(i).y == pts.get(i).y - pts.get(i-1).y) {
				if (pts.get(i).y % 2 == 0) {
					ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
				}
			}else if (Math.abs(pts.get(i).y - pts.get(i-1).y) > 8 || Math.abs(pts.get(i).x - pts.get(i-1).x) > 8){
//				ret.add(new ArrayList<Vector2>());
				ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
			}else {
				ret.get(ret.size()-1).add(new Vector2(pts.get(i).x, pts.get(i).y));
			}
		}
		
		return ret;
	}

}
