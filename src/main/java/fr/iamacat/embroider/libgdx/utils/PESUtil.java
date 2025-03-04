package fr.iamacat.embroider.libgdx.utils;

import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;
import net.plantabyte.drptrace.geometry.Vec2;

import java.awt.geom.Point2D;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PESUtil {
    public static class Stitch {
        int dx, dy;
        StitchType type;

        Stitch(int dx, int dy, StitchType type) {
            this.dx = dx;
            this.dy = dy;
            this.type = type;
        }
    }

    enum StitchType {
        NORMAL(0), JUMP(0x80), TRIM(0x01), STOP(0x10);
        final int code;
        StitchType(int code) { this.code = code; }
    }
    public static List<Point2D.Float> sampleShape(BezierShape shape) {
        List<Point2D.Float> points = new ArrayList<>();
        for (BezierCurve curve : shape) {
            for (float t = 0; t <= 1; t += 0.1) {
                Vec2 point = curve.f(t);
                points.add(new Point2D.Float((float) point.x, (float) point.y));
            }
        }
        if (shape.isClosed() && !points.isEmpty()) {
            points.add(points.get(0));
        }
        return points;
    }

    public static List<Stitch> createJump(Point2D.Float from, Point2D.Float to) {
        List<Stitch> jumps = new ArrayList<>();
        int dx = (int) (to.x * 10 - from.x);
        int dy = (int) (to.y * 10 - from.y);
        // Split large jumps into multiple steps if needed
        jumps.add(new Stitch(dx, dy, StitchType.JUMP));
        return jumps;
    }
    public static void writeStitches(DataOutputStream out, List<Stitch> stitches) throws IOException {
        for (Stitch stitch : stitches) {
            // Encode stitch deltas and type into 3 bytes
            out.writeByte(stitch.dx);
            out.writeByte(stitch.dy);
            out.writeByte(stitch.type.code);
        }
    }
    public static void writePESHeader(DataOutputStream out, int width, int height, String TITLE) throws IOException {
        out.writeBytes("#PES0001"); // PES version (8 bytes)
        out.writeInt(0); // Corrected: 4-byte design section offset placeholder (was writeShort)

        // Write title (up to 8 bytes, padded with nulls)
        String title = TITLE != null ? TITLE : "DESIGN";
        out.writeBytes(title.substring(0, Math.min(title.length(), 8)));
        for (int i = title.length(); i < 8; i++) {
            out.writeByte(0); // Pad remaining bytes
        }

        out.writeShort(1); // Hoop size code (100x100mm)
        out.writeShort((short) width); // Use short if PES expects 2-byte width
        out.writeShort((short) height); // Use short if PES expects 2-byte height
    }
}
