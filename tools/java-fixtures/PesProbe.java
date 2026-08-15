import java.io.DataInputStream;
import java.io.FileInputStream;

public class PesProbe {
    public static void main(String[] args) throws Exception {
        try (DataInputStream dis = new DataInputStream(new FileInputStream("fixtures/simple.pes"))) {
            byte[] header = new byte[8];
            dis.readFully(header);
            System.out.println("header: " + new String(header));
            dis.skipBytes(8);
            int dataOffset = readIntLittleEndian(dis);
            System.out.println("dataOffset: " + dataOffset + " (0x" + Integer.toHexString(dataOffset) + ")");
            int skipped = dis.skipBytes(dataOffset - 16);
            System.out.println("skipped: " + skipped + ", available: " + dis.available());
            int count = 0;
            while (dis.available() >= 3 && count < 8) {
                int b1 = dis.readUnsignedByte();
                int b2 = dis.readUnsignedByte();
                int command = dis.readUnsignedByte();
                System.out.printf("rec %d: b1=0x%02X b2=0x%02X cmd=0x%02X", count, b1, b2, command);
                if (command == 0x80) {
                    System.out.println(" -> COLOR " + b1);
                } else {
                    short x = (short) ((b1 & 0xFF) | ((b2 & 0x03) << 8));
                    short y = (short) (((b2 >> 2) & 0x3F) | ((command & 0x0F) << 6));
                    if ((b2 & 0x80) != 0) x = (short) -x;
                    if ((command & 0x10) != 0) y = (short) -y;
                    System.out.println(" -> (" + x + ", " + y + ")");
                }
                count++;
            }
        }
    }

    private static int readIntLittleEndian(DataInputStream dis) throws Exception {
        byte[] bytes = new byte[4];
        dis.readFully(bytes);
        return java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
