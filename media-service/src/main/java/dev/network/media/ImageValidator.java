package dev.network.media;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class ImageValidator {
  public static final int MAX_BYTES = 5 * 1024 * 1024,
      MAX_DIMENSION = 8192,
      MAX_PIXELS = 16_000_000;

  public record Image(byte[] bytes, String contentType, int width, int height) {}

  public Image normalize(InputStream input) throws IOException {
    byte[] bytes = input.readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds5MiB");
    boolean png =
        bytes.length >= 8
            && Arrays.equals(
                Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    boolean jpeg =
        bytes.length >= 3
            && (bytes[0] & 255) == 255
            && (bytes[1] & 255) == 216
            && (bytes[2] & 255) == 255;
    if (!png && !jpeg)
      throw new IllegalArgumentException("Only valid JPEG and PNG images are accepted");
    String format = png ? "png" : "jpeg";
    try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new IllegalArgumentException("Malformed image");
      var reader = readers.next();
      try {
        reader.setInput(stream, true, true);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w < 1 || h < 1 || w > MAX_DIMENSION || h > MAX_DIMENSION || (long) w * h > MAX_PIXELS)
          throw new IllegalArgumentException("Decoded image exceeds limits");
        BufferedImage decoded = reader.read(0);
        if (decoded == null) throw new IllegalArgumentException("Malformed image");
        var clean =
            new BufferedImage(w, h, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = clean.createGraphics();
        try {
          graphics.drawImage(decoded, 0, 0, null);
        } finally {
          graphics.dispose();
          decoded.flush();
        }
        var output =
            new ByteArrayOutputStream() {
              @Override
              public synchronized void write(byte[] b, int off, int len) {
                if (count + len > MAX_BYTES)
                  throw new IllegalArgumentException("Normalized image exceeds5MiB");
                super.write(b, off, len);
              }

              @Override
              public synchronized void write(int b) {
                if (count >= MAX_BYTES)
                  throw new IllegalArgumentException("Normalized image exceeds5MiB");
                super.write(b);
              }
            };
        try {
          if (!ImageIO.write(clean, format, output))
            throw new IllegalArgumentException("Unsupported image");
        } finally {
          clean.flush();
        }
        if (output.size() > MAX_BYTES)
          throw new IllegalArgumentException("Normalized image exceeds5MiB");
        return new Image(output.toByteArray(), png ? "image/png" : "image/jpeg", w, h);
      } finally {
        reader.dispose();
      }
    } catch (javax.imageio.IIOException e) {
      throw new IllegalArgumentException("Malformed image");
    }
  }
}
