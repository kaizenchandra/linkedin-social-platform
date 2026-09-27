import java.net.Socket;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Direct Docker-network probe: avoids Docker Desktop's host-port proxy buffering.
 */
public class SlowReader {
    public static void main(String[] args) throws Exception {
        var control = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String token = control.readLine(), cursor = control.readLine();
        try (var socket = new Socket()) {
            socket.setReceiveBufferSize(1024);
            socket.connect(new java.net.InetSocketAddress("api-gateway", 8080), 5000);
            socket.setSoTimeout(10000);
            String request = "GET /api/v1/conversations/stream HTTP/1.1\r\nHost: api-gateway\r\nAuthorization: Bearer " + token + "\r\nLast-Event-ID: " + cursor + "\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            var headers = new StringBuilder();
            while (!headers.toString().endsWith("\r\n\r\n")) {
                int value = socket.getInputStream().read();
                if (value < 0 || headers.length() > 8192) throw new IOException("Missing response headers");
                headers.append((char) value);
            }
            if (!headers.toString().contains("200 OK") || !headers.toString().contains("text/event-stream"))
                throw new IOException("Stream rejected");
            System.out.println("READY " + socket.getReceiveBufferSize());
            System.out.flush();
            control.readLine(); // The orchestrator releases this client after checking the server deadline.
        }
    }
}
