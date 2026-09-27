package dev.network.media;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class ImageValidatorTest {
    private final ImageValidator validator = new ImageValidator();

    @Test
    void normalizesPngAndDropsTrailingMetadata() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), "png", output);
        output.write("private-metadata".getBytes());
        var image = validator.normalize(new ByteArrayInputStream(output.toByteArray()));
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.width()).isEqualTo(32);
        assertThat(new String(image.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("private-metadata");
    }

    @Test
    void rejectsOversizeInvalidAndDecompressionDimensions() throws Exception {
        assertThatThrownBy(
                () ->
                        validator.normalize(
                                new ByteArrayInputStream(new byte[ImageValidator.MAX_BYTES + 1])))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> validator.normalize(new ByteArrayInputStream("not a PNG".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out);
        byte[] data = out.toByteArray();
        java.nio.ByteBuffer.wrap(data, 16, 8).putInt(32768).putInt(32768);
        assertThatThrownBy(() -> validator.normalize(new ByteArrayInputStream(data)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsJpegWithoutTrustingFilename() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        assertThat(validator.normalize(new ByteArrayInputStream(out.toByteArray())).contentType())
                .isEqualTo("image/jpeg");
    }
}
