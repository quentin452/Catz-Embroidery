package fr.iamacat.embroider.libgdx.utils;

import net.plantabyte.drptrace.geometry.BezierCurve;
import net.plantabyte.drptrace.geometry.BezierShape;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Stack;

public class PESUtil {

    public static final int NO_COMMAND = -1;
    public static final int STITCH = 0;
    public static final int JUMP = 1;
    public static final int TRIM = 2;
    public static final int STOP = 3;
    public static final int END = 4;
    public static final int COLOR_CHANGE = 5;
    public static final int NEEDLE_SET = 9;
    public static final int SEQUIN_MODE = 6;
    public static final int SEQUIN_EJECT = 7;
    public static final int SLOW = 0xB;
    public static final int FAST = 0xC;

    public static final int COMMAND_MASK = 0xFF;

    static String logPrefix = "[PEmbroider Writer] ";

    public static class PESWriter {
        public static int VERSION = 1;
        public static boolean TRUNCATED = false;

        static final int MASK_07_BIT = 0b01111111;
        static final int JUMP_CODE = 0b00010000;
        static final int TRIM_CODE = 0b00100000;
        static final int FLAG_LONG = 0b10000000;

        static final int PEC_ICON_WIDTH = 48;
        static final int PEC_ICON_HEIGHT = 38;

        public static void write(String name, float[] bounds, ArrayList<BezierShape> stitches, ArrayList<Integer> colors, String title, ArrayList<Boolean> jumps) throws IOException {

            class _BinWriter{
                int position = 0;
                OutputStream stream;
                OutputStream original;
                Stack<ByteArrayOutputStream> streamStack;

                _BinWriter() throws IOException {
                    stream = new FileOutputStream(name+".pes");
                    original = stream;
                    streamStack = new Stack<>();
                }

                public void writeInt8(int value) throws IOException {
                    position += 1;
                    stream.write(value);
                };
                public void writeInt16LE(int value) throws IOException {
                    position += 2;
                    stream.write(value & 0xFF);
                    stream.write((value >> 8) & 0xFF);
                }
                public void writeInt16BE(int value) throws IOException {
                    position += 2;
                    stream.write((value >> 8) & 0xFF);
                    stream.write(value & 0xFF);
                }

                public void writeInt32LE(int value) throws IOException {
                    position += 4;
                    stream.write(value & 0xFF);
                    stream.write((value >> 8) & 0xFF);
                    stream.write((value >> 16) & 0xFF);
                    stream.write((value >> 24) & 0xFF);
                }
                public void writeInt24LE(int value) throws IOException {
                    position += 3;
                    stream.write(value & 0xFF);
                    stream.write((value >> 8) & 0xFF);
                    stream.write((value >> 16) & 0xFF);
                }

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
                    write_pec_graphics();
                    for (int i = 0; i < colors.size(); i++) {
                        if (i > 0 && !colors.get(i-1).equals(colors.get(i))) {
                            write_pec_graphics();
                        }
                    }

                    return data;
                }
                public int find_color(int color) {
                    int r = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int b = (color) & 0xFF;
                    int[] std = new int[] {//https://edutechwiki.unige.ch/en/Embroidery_format_PEC
                            0x1a0a94,0x0f75ff,0x00934c,0xbabdfe,0xec0000,0xe4995a,0xcc48ab,0xfdc4fa,0xdd84cd,0x6bd38a,
                            0xe4a945,0xffbd42,0xffe600,0x6cd900,0xc1a941,0xb5ad97,0xba9c5f,0xfaf59e,0x808080,0x000000,
                            0x001cdf,0xdf00b8,0x626262,0x69260d,0xff0060,0xbf8200,0xf39178,0xff6805,0xf0f0f0,0xc832cd,
                            0xb0bf9b,0x65bfeb,0xffba04,0xfff06c,0xfeca15,0xf38101,0x37a923,0x23465f,0xa6a695,0xcebfa6,
                            0x96aa02,0xffe3c6,0xff99d7,0x007004,0xedccfb,0xc089d8,0xe7d9b4,0xe90e86,0xcf6829,0x408615,
                            0xdb1797,0xffa704,0xb9ffff,0x228927,0xb612cd,0x00aa00,0xfea9dc,0xfed510,0x0097df,0xffff84,
                            0xcfe774,0xffc864,0xffc8c8,0xffc8c8};
                    float md = 195075;
                    int mi = 0;
                    for (int i = 0; i < std.length; i++) {
                        int r0 = (std[i] >> 16) & 0xFF;
                        int g0 = (std[i] >> 8) & 0xFF;
                        int b0 = (std[i]) & 0xFF;
                        float d = (float)(Math.pow(r-r0,2)+Math.pow(g-g0, 2)+Math.pow(b-b0, 2));
                        if (d < md) {
                            md = d;
                            mi = i;
                        }
                    }
                    return mi+1;
                }

                public Object[] write_pec_header() throws IOException {
                    ArrayList<Integer> color_index_list = new ArrayList<>();

                    write(String.format(Locale.ENGLISH, "LA:%-16s\r", title).getBytes());
                    for (int i = 0; i < 12; i++) {
                        writeInt8(0x20);
                    }
                    writeInt8(0xFF);
                    writeInt8(0x00);

                    writeInt8(PEC_ICON_WIDTH / 8);
                    writeInt8(PEC_ICON_HEIGHT);


                    ArrayList<Integer> palette = new ArrayList<Integer>();
                    for (int i = 0; i < colors.size(); i++) {
                        if (i==0 || (!colors.get(i).equals(colors.get(i-1)))) {
//							if (!palette.contains(colors.get(i))) {
                            palette.add(colors.get(i));
//							}
                        }
                    }

                    for (int i = 0; i < 12; i++) {
                        writeInt8(0x20);
                    }
                    color_index_list.add(palette.size()-1);
                    writeInt8(palette.size()-1);
                    for (int i = 0; i < palette.size(); i++) {
                        int idx = find_color(palette.get(i));
                        color_index_list.add(idx);
                        writeInt8(idx);
                    }

                    for (int i = 0; i < (463-palette.size()); i++) {
                        writeInt8(0x20);
                    }
                    return new Object[] {color_index_list, palette};
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

                    int dx, dy;
                    boolean jumping = false;
                    double xx = 0, yy = 0;

                    for (int i = 0, ie = stitches.size(); i < ie; i++) {

                        if (i > 0 && !colors.get(i).equals(colors.get(i-1))) {
                            // color change
                            writeInt8(0xfe);
                            writeInt8(0xb0);
                            writeInt8((color_two) ? 2 : 1);
                            color_two = !color_two;
                        }

                        float x = stitches.get(i).x;
                        float y = stitches.get(i).y;

//			            println(x+" "+y);
                        dx = (int) Math.rint(x - xx);
                        dy = (int) Math.rint(y - yy);
                        int odx = dx;
                        int ody = dy;
                        xx += dx;
                        yy += dy;

                        if (i == 0) {
//		                    jumping = true;
                            dx = encode_long_form(dx);
                            dx = flagTrim(dx);
                            dy = encode_long_form(dy);
                            dy = flagTrim(dy);
                            writeInt16BE(dx);
                            writeInt16BE(dy);
                            writeInt8((byte) 0x00);
                            writeInt8((byte) 0x00);
                            dx = 0;
                            dy = 0;
                        }


//			            if ((jumping) && (dx != 0) && (dy != 0)) {
//			            	writeInt8((byte) 0x00);
//			            	writeInt8((byte) 0x00);
//			            	jumping = false;
//			            }
                        if (dx < 63 && dx > -64 && dy < 63 && dy > -64) {
                            writeInt8(dx & MASK_07_BIT);
                            writeInt8(dy & MASK_07_BIT);
                        } else {
                            dx = encode_long_form(dx);
                            dy = encode_long_form(dy);
                            writeInt16BE(dx);
                            writeInt16BE(dy);
                        }


                    }
                    writeInt8(0xff);//end
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
                    int flag = -1;

                    int mode;
                    int adjust_x = (int) (left + cx);
                    int adjust_y = (int) (bottom + cy);

                    int colorIndex = 0;
                    int colorCode = 0;

                    colorCode = find_color(colors.get(0));
                    colorlog.add(section);
                    colorlog.add(colorCode);

                    float lastx = 0, lasty = 0;
                    float x, y;


                    x = lastx;
                    y = lasty;
                    segment.add((int) (x - adjust_x));
                    segment.add((int) (y - adjust_y));
                    x = stitches.get(0).x;
                    y = stitches.get(0).y;
                    segment.add((int) (x - adjust_x));
                    segment.add((int) (y - adjust_y));
                    flag = 1;
                    writeInt16LE(flag);
                    writeInt16LE((short) colorCode);
                    writeInt16LE((short) segment.size() / 2);
                    for (Integer v : segment) {
                        writeInt16LE(v);
                    }
                    section++;
                    segment.clear();

                    for (int i = 0, ie = stitches.size(); i < ie; i++) {
                        int thisColor = colors.get(i);
                        mode = STITCH & COMMAND_MASK;
                        if (i > 0 && !colors.get(i-1).equals(thisColor)) {
                            mode = COLOR_CHANGE & COMMAND_MASK;
                        }
                        if ((mode != END) && (flag != -1)) {
                            writeInt16LE(0x8003);
                        }
                        switch (mode) {
                            case COLOR_CHANGE:
                                colorCode = find_color(colors.get(i));
                                colorlog.add(section);
                                colorlog.add(colorCode);
                                flag = 1;
                                break;
                            case STITCH:
                                while (i < ie && colors.get(i).equals(thisColor) ) {
                                    lastx = stitches.get(i).x;
                                    lasty = stitches.get(i).y;
                                    x = lastx;
                                    y = lasty;
                                    segment.add((int) (x - adjust_x));
                                    segment.add((int) (y - adjust_y));
                                    i++;
                                }
                                i--;
                                flag = 0;
                                break;
                        }
                        if (segment.size() != 0) {
                            writeInt16LE(flag);
                            writeInt16LE((short) colorCode);
                            writeInt16LE((short) segment.size() / 2);
                            for (Integer v : segment) {
//			                	processing.core.PApplet.println(v);
                                writeInt16LE(v);
                            }
                            section++;
                        } else {
                            flag = -1;
                        }
                        segment.clear();
                    }
                    int count = colorlog.size() / 2;
                    writeInt16LE(count);
                    for (Integer v : colorlog) {
                        writeInt16LE(v);
                    }
                    writeInt16LE(0x0000);
                    writeInt16LE(0x0000);
                    return new Object[]{section, colorlog};
                }

                public int write_pes_sewsegheader(float left, float top, float right, float bottom) throws IOException {

                    float height = bottom - top;
                    float width = right - left;
                    int hoopHeight = 1800, hoopWidth = 1300;
                    writeInt16LE(0);  //writeInt16LE((int) left);
                    writeInt16LE(0);  //writeInt16LE((int) top);
                    writeInt16LE(0);  //writeInt16LE((int) right);
                    writeInt16LE(0);  //writeInt16LE((int) bottom);
                    writeInt16LE(0);  //writeInt16LE((int) left);
                    writeInt16LE(0);  //writeInt16LE((int) top);
                    writeInt16LE(0);  //writeInt16LE((int) right);
                    writeInt16LE(0);  //writeInt16LE((int) bottom);
                    float transX = 0;
                    float transY = 0;
                    transX += 350f;
                    transY += 100f + height;
                    transX += hoopWidth / 2;
                    transY += hoopHeight / 2;
                    transX += -width / 2;
                    transY += -height / 2;
                    writeInt32LE(Float.floatToIntBits(1f));
                    writeInt32LE(Float.floatToIntBits(0f));
                    writeInt32LE(Float.floatToIntBits(0f));
                    writeInt32LE(Float.floatToIntBits(1f));
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
                    writeInt16LE(0x01); // 0 = 100x100 else 130x180 or above
                    writeInt8(0x30);
                    writeInt8(0x32);
                    String name = title;

                    writePesString8(name);
                    writePesString8("category");
                    writePesString8("author");
                    writePesString8("keywords");
                    writePesString8("comments");

                    writeInt16LE(0);//boolean optimizeHoopChange = (readInt16LE() == 1);

                    writeInt16LE(0);//boolean designPageIsCustom = (readInt16LE() == 1);

                    writeInt16LE(0x64); //hoopwidth
                    writeInt16LE(0x64); //hoopheight
                    writeInt16LE(0);// 1 means "UseExistingDesignArea" 0 means "Design Page Area"

                    writeInt16LE(0xC8);//int designWidth = readInt16LE();
                    writeInt16LE(0xC8);//int designHeight = readInt16LE();
                    writeInt16LE(0x64);//int designPageSectionWidth = readInt16LE();
                    writeInt16LE(0x64);//int designPageSectionHeight = readInt16LE();
                    writeInt16LE(0x64);//int p6 = readInt16LE(); // 100

                    writeInt16LE(0x07);//int designPageBackgroundColor = readInt16LE();
                    writeInt16LE(0x13);//int designPageForegroundColor = readInt16LE();
                    writeInt16LE(0x01); //boolean ShowGrid = (readInt16LE() == 1);
                    writeInt16LE(0x01);//boolean WithAxes = (readInt16LE() == 1);
                    writeInt16LE(0x00);//boolean SnapToGrid = (readInt16LE() == 1);
                    writeInt16LE(100);//int GridInterval = readInt16LE();

                    writeInt16LE(0x01);//int p9 = readInt16LE(); // curves?
                    writeInt16LE(0x00);//boolean OptimizeEntryExitPoints = (readInt16LE() == 1);

                    writeInt8(0);//int fromImageStringLength = readInt8();
                    //String FromImageFilename = readString(fromImageStringLength);

                    writeInt32LE(Float.floatToIntBits(1f));
                    writeInt32LE(Float.floatToIntBits(0f));
                    writeInt32LE(Float.floatToIntBits(0f));
                    writeInt32LE(Float.floatToIntBits(1f));
                    writeInt32LE(Float.floatToIntBits(0f));
                    writeInt32LE(Float.floatToIntBits(0f));
                    writeInt16LE(0);//int numberOfProgrammableFillPatterns = readInt16LE();
                    writeInt16LE(0);//int numberOfMotifPatterns = readInt16LE();
                    writeInt16LE(0);//int featherPatternCount = readInt16LE();
                    ArrayList<Integer> chart = new ArrayList<Integer>();
                    int color_count = 1;
                    for (int i = 1; i < colors.size(); i++) {
                        if (!colors.get(i).equals(colors.get(i-1))) {
                            color_count ++;
                        }
                    }
                    writeInt16LE(color_count);//int numberOfColors = readInt16LE();
                    for (Integer t : chart) {
                        write_pes_thread(t);
                    }
                    writeInt16LE(distinctBlockObjects);//number of distinct blocks
                }
                public void write_pes_thread(int color) throws IOException {
                    writePesString8(Integer.toString(find_color(color)));
                    writeInt8((color >> 16) & 255);
                    writeInt8((color >> 8) & 255);
                    writeInt8(color & 255);
                    writeInt8(0); //unknown
                    writeInt32LE(0xA);
                    writePesString8("description");
                    writePesString8("brand");
                    writePesString8("chart");
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
                public void write_pes_header_v1(int distinctBlockObjects) throws IOException {
                    writeInt16LE(0x01); //1 is scale to fit.
                    writeInt16LE(0x01); // 0 = 100x100 else 130x180 or above
                    writeInt16LE(distinctBlockObjects);//number of distinct blocks
                }

                void write_version_1() throws IOException {

                    write("#PES0001");

                    float pattern_left = bounds[0];
                    float pattern_top = bounds[1];
                    float pattern_right = bounds[2];
                    float pattern_bottom = bounds[3];

                    float cx = ((pattern_left + pattern_right) / 2);
                    float cy = ((pattern_top + pattern_bottom) / 2);


                    float left = pattern_left - cx;
                    float top = pattern_top - cy;
                    float right = pattern_right - cx;
                    float bottom = pattern_bottom - cy;

                    int placeholder_pec_block = tell();
                    space_holder(4);

                    if (stitches.size() == 0) {
                        write_pes_header_v1(0);
                        writeInt16LE(0x0000);
                        writeInt16LE(0x0000);
                    } else {
                        write_pes_header_v1(1);
                        writeInt16LE(0xFFFF);
                        writeInt16LE(0x0000);
                        write_pes_blocks(left, top, right, bottom, cx, cy);
                    }
                    writeSpaceHolder32LE(tell());
                    write_pec();
                    stream.close();
                }
                void write_truncated_version_1() throws IOException {
                    write("#PES0001");
                    writeInt8(0x16);
                    for (int i = 0; i < 13; i++) {
                        writeInt8(0x00);
                    }
                    write_pec();
                    stream.close();
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

                    float pattern_left = bounds[0];
                    float pattern_top = bounds[1];
                    float pattern_right = bounds[2];
                    float pattern_bottom = bounds[3];

                    float cx = ((pattern_left + pattern_right) / 2);
                    float cy = ((pattern_top + pattern_bottom) / 2);

                    float left = pattern_left - cx;
                    float top = pattern_top - cy;
                    float right = pattern_right - cx;
                    float bottom = pattern_bottom - cy;

                    int placeholder_pec_block = tell();
                    space_holder(4);

                    if (stitches.size() == 0) {
                        write_pes_header_v6( 0);
                        writeInt16LE(0x0000);
                        writeInt16LE(0x0000);
                    } else {
                        write_pes_header_v6( 1);
                        writeInt16LE(0xFFFF);
                        writeInt16LE(0x0000);
                        ArrayList<Integer> log = write_pes_blocks(left, top, right, bottom, cx, cy);
                        //In version 6 there is some node, tree, order thing.
                        writeInt32LE(0);
                        writeInt32LE(0);
                        for (int i = 0, ie = log.size(); i < ie; i++) {
                            writeInt32LE(i);
                            writeInt32LE(0);
                        }
                    }
                    writeSpaceHolder32LE(tell());
                    Object[] color_info = write_pec();
                    write_pes_addendum(color_info);
                    writeInt16LE(0x0000); //found v6, not 5,4

                }
            }; _BinWriter bin = new _BinWriter();
            if (VERSION == 1) {
                if (TRUNCATED) {
                    bin.write_truncated_version_1();
                }else {
                    bin.write_version_1();
                }
            }else if (VERSION == 6){
                if (TRUNCATED) {
                    bin.write_truncated_version_6();
                }else {
                    bin.write_version_6();
                }

            }else {
                System.out.println(logPrefix+"Error: PES version inexistent or unimplemented");
            }

        }

    }
}
