import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

public final class Iec104SendSinglePoint {

    private static final byte[] SINGLE_POINT_FRAME = bytes(
            0x68, 0x0E, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x01, 0x03, 0x00, 0x01, 0x00,
            0x01, 0x00, 0x00, 0x01);

    private Iec104SendSinglePoint() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java examples/Iec104SendSinglePoint.java <host> <port>");
            System.exit(2);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) Duration.ofSeconds(3).toMillis());
            OutputStream output = socket.getOutputStream();
            output.write(SINGLE_POINT_FRAME);
            output.flush();
        }
        System.out.printf("sent IEC104 single-point frame to %s:%d%n", host, port);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }
}
