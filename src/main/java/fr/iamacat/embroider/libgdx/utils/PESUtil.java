package fr.iamacat.embroider.libgdx.utils;

import net.plantabyte.drptrace.geometry.Vec2;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class PESUtil {
    public static final int STITCH = 0;
    public static final int END = 4;
    public static final int COLOR_CHANGE = 5;

    public static final int COMMAND_MASK = 0xFF;
    private static final int[] PEC_COLORS = {
            0x1a0a94, 0x0f75ff, 0x00934c, 0xbabdfe, 0xec0000, 0xe4995a, 0xcc48ab, 0xfdc4fa,
            0xdd84cd, 0x6bd38a, 0xe4a945, 0xffbd42, 0xffe600, 0x6cd900, 0xc1a941, 0xb5ad97,
            0xba9c5f, 0xfaf59e, 0x808080, 0x000000, 0x001cdf, 0xdf00b8, 0x626262, 0x69260d,
            0xff0060, 0xbf8200, 0xf39178, 0xff6805, 0xf0f0f0, 0xc832cd, 0xb0bf9b, 0x65bfeb,
            0xffba04, 0xfff06c, 0xfeca15, 0xf38101, 0x37a923, 0x23465f, 0xa6a695, 0xcebfa6,
            0x96aa02, 0xffe3c6, 0xff99d7, 0x007004, 0xedccfb, 0xc089d8, 0xe7d9b4, 0xe90e86,
            0xcf6829, 0x408615, 0xdb1797, 0xffa704, 0xb9ffff, 0x228927, 0xb612cd, 0x00aa00,
            0xfea9dc, 0xfed510, 0x0097df, 0xffff84, 0xcfe774, 0xffc864, 0xffc8c8, 0xffc8c8
    };
    public static int VERSION = 6;
    public static boolean TRUNCATED = false;

    static final int MASK_07_BIT = 0b01111111;
    static final int TRIM_CODE = 0b00100000;
    static final int PEC_ICON_WIDTH = 48;
    static final int PEC_ICON_HEIGHT = 38;

    public static void write(String name, float[] bounds, ArrayList<Vec2> stitches, ArrayList<Integer> colors, String title, ArrayList<Boolean> jumps) throws IOException {

        class _BinWriter{
            int position = 0;
            OutputStream stream;
            OutputStream original;
            Stack<ByteArrayOutputStream> streamStack;

            _BinWriter() throws IOException{
                stream = new FileOutputStream(name+".pes");
                original = stream;
                streamStack = new Stack<>();
            }

            private void writeData(String type, int value, int bytes) throws IOException {
                byte[] data = new byte[bytes];
                for(int i = 0; i < bytes; i++)
                    data[i] = (byte)((value >> (8*(type.equals("LE") ? i : bytes-1-i))) & 0xFF);
                write(data);
            }

            public void writeInt8(int v) throws IOException { writeData("BE", v, 1); }
            public void writeInt16LE(int v) throws IOException { writeData("LE", v, 2); }
            public void writeInt16BE(int v) throws IOException { writeData("BE", v, 2); }
            public void writeInt24LE(int v) throws IOException { writeData("LE", v, 3); }
            public void writeInt32LE(int v) throws IOException { writeData("LE", v, 4); }

            public void writeSpaceHolder16LE(int value) throws IOException {
                ByteArrayOutputStream baos = pop();
                stream.write(value & 0xFF);
                stream.write((value >> 8) & 0xFF);
                stream.write(baos.toByteArray());
            }
            private ByteArrayOutputStream pop() {
                ByteArrayOutputStream pop = streamStack.pop();
                if (streamStack.isEmpty()) {
                    stream = original;
                } else {
                    stream = streamStack.peek();
                }
                return pop;
            }
            public void writeSpaceHolder24LE(int value) throws IOException {
                ByteArrayOutputStream baos = pop();
                stream.write(value & 0xFF);
                stream.write((value >> 8) & 0xFF);
                stream.write((value >> 16) & 0xFF);
                stream.write(baos.toByteArray());
            }
            public void writeSpaceHolder32LE(int value) throws IOException {
                ByteArrayOutputStream baos = pop();
                stream.write(value & 0xFF);
                stream.write((value >> 8) & 0xFF);
                stream.write((value >> 16) & 0xFF);
                stream.write((value >> 24) & 0xFF);
                stream.write(baos.toByteArray());
            }
            public int tell() {
                return position;
            }
            public void write(String string) throws IOException {
                position += string.length();
                stream.write(string.getBytes());
            }
            public void write(byte[] bytes) throws IOException {
                position += bytes.length;
                stream.write(bytes);
            }
            public void space_holder(int skip) {
                position += skip;
                ByteArrayOutputStream push = new ByteArrayOutputStream();
                if (streamStack == null) {
                    streamStack = new Stack<>();
                }
                streamStack.push(push);
                stream = push;
            }
            public Object[] write_pec() throws IOException {

                Object[] data = write_pec_header();
                write_pec_block();
                write_pec_graphics();
                for (int i = 1; i < colors.size(); i++) {
                    if (!colors.get(i).equals(colors.get(i - 1))) {
                        write_pec_graphics();
                    }
                }

                return data;
            }
            public int find_color(int color) {
                int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
                int closestIndex = 0;
                float minDistance = Float.MAX_VALUE;

                for (int i = 0; i < PEC_COLORS.length; i++) {
                    int pc = PEC_COLORS[i];
                    int dr = r - ((pc >> 16) & 0xFF);
                    int dg = g - ((pc >> 8) & 0xFF);
                    int db = b - (pc & 0xFF);
                    float distance = dr*dr + dg*dg + db*db;

                    if (distance < minDistance) {
                        minDistance = distance;
                        closestIndex = i;
                    }
                }
                return closestIndex + 1;
            }
            public Object[] write_pec_header() throws IOException {
                ArrayList<Integer> colorIndexList = new ArrayList<>();
                write(String.format(Locale.ENGLISH, "LA:%-16s\r", title).getBytes());
                writeRepeatedInt8(0x20, 12); // Write 12 spaces
                writeInt8(0xFF);
                writeInt8(0x00);
                writeInt8(PEC_ICON_WIDTH / 8);
                writeInt8(PEC_ICON_HEIGHT);
                LinkedHashSet<Integer> paletteSet = new LinkedHashSet<>(colors);
                ArrayList<Integer> palette = new ArrayList<>(paletteSet);
                writeRepeatedInt8(0x20, 12); // Write another 12 spaces
                writeInt8(palette.size() - 1);
                colorIndexList.add(palette.size() - 1);
                for (int color : palette) {
                    int idx = find_color(color);
                    colorIndexList.add(idx);
                    writeInt8(idx);
                }
                writeRepeatedInt8(0x20, 463 - palette.size()); // Fill remaining space
                return new Object[]{colorIndexList, palette};
            }

            private void writeRepeatedInt8(int value, int count) throws IOException {
                for (int i = 0; i < count; i++) {
                    writeInt8(value);
                }
            }

            void write_pec_block() throws IOException {
                int width = (int) Math.rint(bounds[2]-bounds[0]);
                int height = (int) Math.rint(bounds[3]-bounds[1]);
                int stitch_block_start_position = tell();
                writeInt8(0x00);
                writeInt8(0x00);
                space_holder(3);

                writeInt8(0x31);
                writeInt8(0xFF);
                writeInt8(0xF0);
                /* write 2 byte x size */
                writeInt16LE((short) Math.round(width));
                /* write 2 byte y size */
                writeInt16LE((short) Math.round(height));

                /* Write 4 miscellaneous int16's */
                writeInt16LE((short) 0x1E0);
                writeInt16LE((short) 0x1B0);

                writeInt16BE((0x9000 | -Math.round(bounds[0])));
                writeInt16BE((0x9000 | -Math.round(bounds[1])));

                pec_encode();

                int stitch_block_length = tell() - stitch_block_start_position;
                writeSpaceHolder24LE(stitch_block_length);
            }
            void write_pec_graphics() throws IOException {
                write(new byte[]{
                        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                        (byte) 0xF0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x0F,
                        (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10,
                        (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x20,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x40,
                        (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x20,
                        (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x10,
                        (byte) 0xF0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x0F,
                        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
                });
            }

            public int encode_long_form(int value) {
                value &= 0b00001111_11111111;
                value |= 0b10000000_00000000;
                return value;
            }
            public int flagTrim(int longForm) {
                return longForm | (TRIM_CODE << 8);
            }

            private void pec_encode() throws IOException {
                boolean color_two = true;
                double xx = 0, yy = 0;

                for (int i = 0, ie = stitches.size(); i < ie; i++) {
                    if (i > 0 && !colors.get(i).equals(colors.get(i - 1))) {
                        writeInt8(0xfe);
                        writeInt8(0xb0);
                        writeInt8(color_two ? 2 : 1);
                        color_two = !color_two;
                    }

                    int dx = (int) Math.rint(stitches.get(i).x - xx);
                    int dy = (int) Math.rint(stitches.get(i).y - yy);
                    xx += dx;
                    yy += dy;

                    if (i == 0) {
                        writeInt16BE(flagTrim(encode_long_form(dx)));
                        writeInt16BE(flagTrim(encode_long_form(dy)));
                        writeInt8(0x00);
                        writeInt8(0x00);
                        continue;
                    }

                    if (dx >= -64 && dx < 63 && dy >= -64 && dy < 63) {
                        writeInt8(dx & MASK_07_BIT);
                        writeInt8(dy & MASK_07_BIT);
                    } else {
                        writeInt16BE(encode_long_form(dx));
                        writeInt16BE(encode_long_form(dy));
                    }
                }
                writeInt8(0xff); // End of encoding
            }

            public void writePesString16(String string) throws IOException {
                writeInt16LE(string.length());
                write(string.getBytes());
            }
            public void writePesString8(String string) throws IOException {
                if (string == null) {
                    writeInt8(0);
                    return;
                }
                if (string.length() > 255) {
                    string = string.substring(0, 255);
                }
                writeInt8(string.length());
                write(string.getBytes());
            }


            public ArrayList<Integer> write_pes_blocks(float left, float top, float right, float bottom, float cx, float cy) throws IOException {
                if (stitches.size() == 0) {
                    return null;
                }
                writePesString16("CEmbOne");
                write_pes_sewsegheader(left, top, right, bottom);
                space_holder(2);
                writeInt16LE(0xFFFF);
                writeInt16LE(0x0000); //FFFF0000 means more blocks exist.
                writePesString16("CSewSeg");
                Object[] data = write_pes_embsewseg_segments(left, bottom, cx, cy);
                Integer sections = (Integer) data[0];
                ArrayList<Integer> colorlog = (ArrayList<Integer>) data[1];
                writeSpaceHolder16LE(sections);
                return colorlog;
            }

            public Object[] write_pes_embsewseg_segments(float left, float bottom, float cx, float cy) throws IOException {
                ArrayList<Integer> segment = new ArrayList<>();
                ArrayList<Integer> colorlog = new ArrayList<>();
                int section = 0;
                int flag;
                int adjust_x = (int) (left + cx);
                int adjust_y = (int) (bottom + cy);
                int colorCode = find_color(colors.get(0));
                colorlog.add(section);
                colorlog.add(colorCode);
                segment.add((-adjust_x));
                segment.add((-adjust_y));
                segment.add((int) (stitches.get(0).x - adjust_x));
                segment.add((int) (stitches.get(0).y - adjust_y));
                writeSegment(segment, flag = 1, colorCode);
                section++;
                for (int i = 0, ie = stitches.size(); i < ie; i++) {
                    int thisColor = colors.get(i);
                    int mode = (i > 0 && !colors.get(i - 1).equals(thisColor)) ? COLOR_CHANGE & COMMAND_MASK : STITCH & COMMAND_MASK;
                    if (flag != -1) {
                        writeInt16LE(0x8003);
                    }
                    if (mode == COLOR_CHANGE) {
                        colorCode = find_color(thisColor);
                        colorlog.add(section);
                        colorlog.add(colorCode);
                        flag = 1;
                    } else {  // STITCH mode
                        while (i < ie && colors.get(i).equals(thisColor)) {
                            segment.add((int) (stitches.get(i).x - adjust_x));
                            segment.add((int) (stitches.get(i).y - adjust_y));
                            i++;
                        }
                        i--; // Adjust index after loop
                        flag = 0;
                    }
                    if (!segment.isEmpty()) {
                        writeSegment(segment, flag, colorCode);
                        section++;
                        segment.clear();
                    } else {
                        flag = -1;
                    }
                }
                writeInt16LE(colorlog.size() / 2);
                for (Integer v : colorlog) {
                    writeInt16LE(v);
                }
                writeInt16LE(0x0000);
                writeInt16LE(0x0000);
                return new Object[]{section, colorlog};
            }

            private void writeSegment(List<Integer> segment, int flag, int colorCode) throws IOException {
                writeInt16LE(flag);
                writeInt16LE((short) colorCode);
                writeInt16LE((short) (segment.size() / 2));
                for (Integer v : segment) {
                    writeInt16LE(v);
                }
            }

            public int write_pes_sewsegheader(float left, float top, float right, float bottom) throws IOException {
                float height = bottom - top;
                float width = right - left;
                for (int i = 0; i < 8; i++) {
                    writeInt16LE(0);
                }
                float transX = 350f + (1300 / 2f) - (width / 2f);
                float transY = 100f + height + (1800 / 2f) - (height / 2f);
                writeInt32LE(Float.floatToIntBits(1f));  // Scale X
                writeInt32LE(Float.floatToIntBits(0f));  // Skew X
                writeInt32LE(Float.floatToIntBits(0f));  // Skew Y
                writeInt32LE(Float.floatToIntBits(1f));  // Scale Y
                writeInt32LE(Float.floatToIntBits(transX));
                writeInt32LE(Float.floatToIntBits(transY));
                writeInt16LE(1);
                writeInt16LE(0);
                writeInt16LE(0);
                writeInt16LE((short) width);
                writeInt16LE((short) height);
                writeInt32LE(0);
                writeInt32LE(0);
                return tell();
            }

            public void write_pes_header_v6(int distinctBlockObjects) throws IOException {
                // Write standard header values
                writeInt16LE(0x01); // Hoop type
                writeInt8(0x30);
                writeInt8(0x32);
                // Write metadata strings
                String[] metadata = {title, "category", "author", "keywords", "comments"};
                for (String data : metadata) {
                    writePesString8(data);
                }
                // Write default configuration values
                int[] defaultValues = {
                        0, // optimizeHoopChange
                        0, // designPageIsCustom
                        0x64, 0x64, // hoopWidth and hoopHeight
                        0, // UseExistingDesignArea
                        0xC8, 0xC8, 0x64, 0x64, 0x64, // Design dimensions and sections
                        0x07, // BackgroundColor
                        0x13, // ForegroundColor
                        0x01, 0x01, 0x00, // ShowGrid, WithAxes, SnapToGrid
                        100, // GridInterval
                        0x01, // curves?
                        0x00 // OptimizeEntryExitPoints
                };
                for (int value : defaultValues) {
                    writeInt16LE(value);
                }
                // Transformation Matrix (identity matrix)
                float[] transformMatrix = {1f, 0f, 0f, 1f, 0f, 0f};
                for (float element : transformMatrix) {
                    writeInt32LE(Float.floatToIntBits(element));
                }
                // Write patterns (defaulted to zero)
                int[] patternCounts = {0, 0, 0}; // programmableFillPatterns, motifPatterns, featherPatterns
                for (int count : patternCounts) {
                    writeInt16LE(count);
                }
                // Count distinct colors
                int colorCount = 1;
                for (int i = 1; i < colors.size(); i++) {
                    if (!colors.get(i).equals(colors.get(i - 1))) {
                        colorCount++;
                    }
                }
                writeInt16LE(colorCount); // numberOfColors
                writeInt16LE(distinctBlockObjects); // number of distinct blocks
            }

            void write_pes_addendum(Object[] color_info) throws IOException {
                ArrayList<Integer> color_index_list = (ArrayList<Integer>) color_info[0];
                ArrayList<Integer> rgb_list = (ArrayList<Integer>) color_info[1];
                int count = color_index_list.size();
                for (int i = 0, ie = count; i < ie; i++) {
                    writeInt8(color_index_list.get(i));
                }
                for (int i = count, ie = 128 - count; i < ie; i++) {
                    writeInt8(0x20);
                }
                for (int s = 0, se = rgb_list.size(); s < se; s++) {
                    for (int i = 0, ie = 0x90; i < ie; i++) {
                        writeInt8(0x00);
                    }
                }
                for (int s = 0, se = rgb_list.size(); s < se; s++) {
                    writeInt24LE(rgb_list.get(s));
                }
            }

            void write_truncated_version_6() throws IOException {
                write("#PES0060");
                space_holder(4);
                write_pes_header_v6(0);
                for (int i = 0; i < 5; i++) {
                    writeInt8(0x00);
                }
                writeInt16LE(0x0000);
                writeInt16LE(0x0000);
                int current_position = tell();
                writeSpaceHolder32LE(current_position);
                Object[] color_info = write_pec();
                write_pes_addendum(color_info);
                writeInt16LE(0x0000); //found v6, not 5,4
                stream.close();
            }
            void write_version_6() throws IOException {
                write("#PES0060");
                float cx = (bounds[0] + bounds[2]) / 2;
                float cy = (bounds[1] + bounds[3]) / 2;
                space_holder(4);
                boolean hasStitches = !stitches.isEmpty();
                write_pes_header_v6(hasStitches ? 1 : 0);
                writeInt16LE(hasStitches ? 0xFFFF : 0x0000);
                writeInt16LE(0x0000);
                if (hasStitches) {
                    ArrayList<Integer> log = write_pes_blocks(bounds[0] - cx, bounds[1] - cy, bounds[2] - cx, bounds[3] - cy, cx, cy);
                    // In version 6, there's some node, tree, order logic.
                    writeInt32LE(0);
                    writeInt32LE(0);
                    for (int i = 0; i < Objects.requireNonNull(log).size(); i++) {
                        writeInt32LE(i);
                        writeInt32LE(0);
                    }
                }
                writeSpaceHolder32LE(tell());
                write_pes_addendum(write_pec());
                writeInt16LE(0x0000); // Found in v6, not in v5, v4.
            }
        } _BinWriter bin = new _BinWriter();
        if (VERSION == 6) {
            if (TRUNCATED) {
                bin.write_truncated_version_6();
            }else {
                bin.write_version_6();
            }
        } else {
            System.out.println("Error: PES version inexistent or unimplemented");
        }
    }
}