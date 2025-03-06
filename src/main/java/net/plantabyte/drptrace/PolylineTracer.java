package net.plantabyte.drptrace;
import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;
import java.util.ArrayList;
import java.util.List;
// TODO IF interval is high it should decrease stitchs count instead of increasing it

public class PolylineTracer extends Tracer {
	private final int interval;

	public PolylineTracer(int interval) {
		if (interval < 1) throw new IllegalArgumentException("Interval must be ≥ 1");
		this.interval = interval;
	}

	@Override
	public BezierShape tracePath(Vec2[] pathPoints, boolean closedLoop) {
		validateInput(pathPoints, closedLoop);

		List<Integer> nodes = sampleNodes(pathPoints);
		BezierShape shape = createBeziers(pathPoints, nodes, closedLoop);
		smoothJoints(shape, closedLoop);

		return shape;
	}

	private void validateInput(Vec2[] points, boolean closed) {
		int minPoints = closed ? 3 : 2;
		if (points.length < minPoints) {
			throw new IllegalArgumentException(
					String.format("Need at least %d points for %s path",
							minPoints, closed ? "closed" : "open")
			);
		}
	}

	private List<Integer> sampleNodes(Vec2[] points) {
		List<Integer> nodes = new ArrayList<>();
		for (int i = 0; i < points.length; i += interval) {
			nodes.add(i);
		}
		nodes.add(points.length - 1);
		return nodes;
	}

	private BezierShape createBeziers(Vec2[] points, List<Integer> nodes, boolean closed) {
		BezierShape shape = new BezierShape(nodes.size() + 2);
		shape.setClosed(closed);

		for (int i = 1; i < nodes.size(); i++) {
			int start = nodes.get(i-1);
			int end = nodes.get(i);

			BezierCurve curve = new BezierCurve(
					points[start],
					points[Math.min(start+1, points.length-1)],
					points[Math.max(end-1, 0)],
					points[end]
			);
			curve.fitToPoints(points, start, end-start);

			shape.add(curve);
		}
		return shape;
	}

	private void smoothJoints(BezierShape shape, boolean closed) {
		final double SMOOTH_ANGLE = 0.75 * Math.PI;
		int offset = closed ? 0 : -1;

		for (int i = 0; i < shape.size()-1-offset; i++) {
			BezierCurve current = shape.get(i);
			BezierCurve next = shape.get((i+1) % shape.size());

			if (current.getP4().angleBetween(current.getP3(), next.getP2()) > SMOOTH_ANGLE) {
				adjustControlPoints(current, next);
			}
		}
	}

	private void adjustControlPoints(BezierCurve a, BezierCurve b) {
		Vec2 midpoint = a.getP4().midpoint(b.getP1());
		a.setP3(midpoint);
		b.setP2(midpoint);
	}
}