/*
MIT License

Copyright (c) 2021 Dr. Christopher C. Hall, aka DrPlantabyte

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package net.plantabyte.drptrace.math;

import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.Vec2;

/**
 * A collection of useful math functions, collected here as static methods.
 */
public class Util {
	/**
	 * Calculates the closest distance between a point and a line segment (defined
	 * by two points)
	 * @param L1 One end of the line segment
	 * @param L2 Other end of the line segment
	 * @param p The point to measure the distance from
	 * @return closest distance between the point and the line segment
	 */
	public static double distFromPointToLineSegment(Vec2 L1, Vec2 L2, Vec2 p){
		// vector AB
		Vec2 AB = new Vec2(
			L2.x - L1.x,
			L2.y - L1.y
		);
		// vector BP
		Vec2 BE = new Vec2(
			p.x - L2.x,
			p.y - L2.y
		);
		// vector AP
		Vec2 AE = new Vec2(
			p.x - L1.x,
			p.y - L1.y
		);
		// Variables to store dot product
		double AB_BE, AB_AE;
		// Calculating the dot product
		AB_BE = (AB.x * BE.x + AB.y * BE.y);
		AB_AE = (AB.x * AE.x + AB.y * AE.y);
		// Minimum distance from
		// point E to the line segment
		double dist = 0;
		// Case 1
		if (AB_BE > 0)
		{
			
			// Finding the magnitude
			double y = p.y - L2.y;
			double x = p.x - L2.x;
			dist = Math.sqrt(x * x + y * y);
		}
		// Case 2
		else if (AB_AE < 0)
		{
			double y = p.y - L1.y;
			double x = p.x - L1.x;
			dist = Math.sqrt(x * x + y * y);
		}
		// Case 3
		else
		{
			// Finding the perpendicular distance
			double x1 = AB.x;
			double y1 = AB.y;
			double x2 = AE.x;
			double y2 = AE.y;
			double mod = Math.sqrt(x1 * x1 + y1 * y1);
			dist = Math.abs(x1 * y2 - y1 * x2) / mod;
		}
		return dist;
	}
	
	/**
	 * Like Math.min(a, b), but for arrays
	 * @param darr array of values
	 * @return lowest value in the array
	 */
	public static double min(double[] darr){
		double m = darr[0];
		for(int i = 1; i < darr.length; i++){
			double n = darr[i];
			if(n < m){
				m = n;
			}
		}
		return m;
	}
	
	/**
	 * Like Math.max(a, b), but for arrays
	 * @param darr array of values
	 * @return highest value in the array
	 */
	public static double max(double[] darr){
		double m = darr[0];
		for(int i = 1; i < darr.length; i++){
			double n = darr[i];
			if(n > m){
				m = n;
			}
		}
		return m;
	}
	
	/**
	 * Returns the index of the highest value in the array
	 * @param darr array of values
	 * @return index in the array
	 */
	public static int indexOfMax(double[] darr){
		double m = darr[0];
		int index = 0;
		for(int i = 1; i < darr.length; i++){
			double n = darr[i];
			if(n > m){
				m = n;
				index = i;
			}
		}
		return index;
	}
	/**
	 * Returns the index of the lowest value in the array
	 * @param darr array of values
	 * @return index in the array
	 */
	public static int indexOfMin(double[] darr){
		double m = darr[0];
		int index = 0;
		for(int i = 1; i < darr.length; i++){
			double n = darr[i];
			if(n < m){
				m = n;
				index = i;
			}
		}
		return index;
	}


	public static final class LineRegressionResult{
		public final double slope;
		public final double yOffset;
		public final double rmse;

		public LineRegressionResult(double slope, double yOffset, double rmse) {
			this.slope = slope;
			this.yOffset = yOffset;
			this.rmse = rmse;
		}
	}

	/**
	 * Performs a linear regression on the given array of data points
	 * @param points Array of Vec2 points
	 * @return A <code>LineRegressionResult</code> holding the relevant results from teh linear regression
	 */
	public static LineRegressionResult linearRegression(Vec2[] points){
		return linearRegression(points, 0, points.length);
	}

	/**
	 * Performs a linear regression on the given array of data points
	 * @param points Array of Vec2 points
	 * @param startIndex start of subset to regress
	 * @param count number of points to regress
	 * @return A <code>LineRegressionResult</code> holding the relevant results from teh linear regression
	 */
	public static LineRegressionResult linearRegression(final Vec2[] points, final int startIndex, final int count){
		final double inverseCount = 1.0 / count;
		double xSum = 0;
		double ySum = 0;
		double SS_xy = 0;
		double SS_xx = 0;
		for(int i = startIndex; i < count; i++){
			final var p = points[i];
			xSum += p.x;
			ySum += p.y;
			SS_xy += p.x*p.y;
			SS_xx += p.x*p.x;
		}
		final double xMean = xSum * inverseCount;
		final double yMean = ySum * inverseCount;
		//
		SS_xy -= count * xMean * yMean;
		SS_xx -= count * xMean * xMean;
		final double slope = SS_xy / SS_xx;
		final double offset = yMean - slope * xMean;
		double se = 0;
		for(int i = startIndex; i < count; i++){
			final var p = points[i];
			final double e = p.x * slope + offset;
			se += e * e;
		}
		final double rmse = Math.sqrt(se*inverseCount);
		return new LineRegressionResult(slope, offset, rmse);
	}

	/**
	 * Performs a fast and incomplete linear regression on the given array of data points and returns the slope as a
	 * velocity vector
	 * @param points Array of Vec2 points
	 * @param startIndex start of subset to regress
	 * @param count number of points to regress
	 * @return  the slope as a vector (dx, dy)
	 */
	public static Vec2 linearRegressionAngle(final Vec2[] points, final int startIndex, final int count){
		final double inverseCount = 1.0 / count;
		final int end = startIndex + count;
		double xSum = 0;
		double ySum = 0;
		double SS_xy = 0;
		double SS_xx = 0;
		double SS_yy = 0;
		for(int i = startIndex; i < end; i++){
			final var p = points[i];
			xSum += p.x;
			ySum += p.y;
			SS_xy += p.x*p.y;
			SS_xx += p.x*p.x;
			SS_yy += p.y*p.y;
		}
		final double xMean = xSum * inverseCount;
		final double yMean = ySum * inverseCount;
		// WARNING: data may be a perfectly vertical line!
		SS_xy -= count * xMean * yMean;
		SS_xx -= count * xMean * xMean;
		SS_yy -= count * yMean * yMean;
		if(SS_yy < SS_xx) {
			// more horizontal than vertical
			return new Vec2(SS_xx, SS_xy);
		} else {
			// more vertical than horizontal
			return new Vec2(SS_xy, SS_yy);
		}
	}

	/**
	 * Applies a rolling average to an array
	 * @param values array to average
	 * @param windowSize Th rolling average window size (must be greater than 0, will nbe rounded up to the next odd number)
	 * @return a new array with averaged values
	 */
	public static double[] rollingAverage(final double[] values, final int windowSize){
		final int halfSize = Math.min(windowSize, values.length)/2;
		final double inverseWindowSize = 1.0 / (double)(2 * halfSize + 1);
		final double[] output = new double[values.length];
		final int limit = values.length-1;
		for(int i = 0; i < values.length; ++i){
			double sum = 0;
			for(int d = -halfSize; d <= halfSize; ++d){
				sum += values[Math.min(limit,Math.max(0, i+d))];
			}
			output[i] = sum * inverseWindowSize;
		}
		return output;
	}

	/**
	 * Estimates the root mean squared error (RMSE) for a bezier curve to a series of points. The bezier path is
	 * approximated, so the returned RMSE is not guarenteed to be especially accurate
	 * @param b a bezier curve
	 * @param pathPoints a series of points
	 * @return the RMSE of the points relative to the bezier curve
	 */
	public static double RMSE(final BezierCurve b, final Vec2[] pathPoints){
		return RMSE(b, pathPoints, 0, pathPoints.length);
	}

	/**
	 * Estimates the root mean squared error (RMSE) for a bezier curve to a series of points. The bezier path is
	 * approximated, so the returned RMSE is not guarenteed to be especially accurate
	 * @param b a bezier curve
	 * @param pathPoints a series of points
	 * @param index array position index
	 * @param count length of subset of array to fit
	 * @return the RMSE of the points relative to the bezier curve
	 */
	public static double RMSE(final BezierCurve b, final Vec2[] pathPoints, final int index, final int count){
		final int limit = index+count;
		final int k = 16; // tune for balancing performance and accuracy
		double totalRSE = 0;
		final Vec2[] bPoints = b.makePoints(k);
		// RMSE points to bezier
		for(int i = index; i < limit; i++){
			var p = pathPoints[i];
			double RSE = Double.MAX_VALUE;
			// approximating bezier as line segments to get mean squared error
			// (lowest squared error of all line segments for each point)
			for(int s = 1; s < k; s++){
				var L1 = bPoints[s-1];
				var L2 = bPoints[s];
				double dist = Util.distFromPointToLineSegment(L1, L2, p);
				if(dist < RSE) {RSE = dist;}
			}
			totalRSE += RSE;
		}
		// RMSE bezier to points
		for(int i = 0; i < bPoints.length; i++){
			var p = bPoints[i];
			double RSE = Double.MAX_VALUE;
			for(int s = index+1; s < limit; s++){
				var L1 = pathPoints[s-1];
				var L2 = pathPoints[s];
				double dist = Util.distFromPointToLineSegment(L1, L2, p);
				if(dist < RSE) {RSE = dist;}
			}
			totalRSE += RSE;
		}
		return totalRSE / count;
	}
}
