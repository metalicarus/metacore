package br.com.genosha.metacore.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ReadOnlyBufferException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DetectionRequestTest {

  private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

  @Test
  void matchesUnsignedSignatures() {
    DetectionRequest request = DetectionRequest.of(JPEG_HEADER, Optional.empty());

    assertThat(request.startsWith(0xFF, 0xD8, 0xFF)).isTrue();
    assertThat(request.startsWith(0xFF, 0xD9)).isFalse();
    assertThat(request.matchesAt(2, 0xFF, 0xE0)).isTrue();
  }

  @Test
  void doesNotMatchBeyondTheHeader() {
    DetectionRequest request = DetectionRequest.of(JPEG_HEADER, Optional.empty());

    assertThat(request.matchesAt(3, 0xE0, 0x00)).isFalse();
    assertThat(request.matchesAt(-1, 0xFF)).isFalse();
  }

  @Test
  void copiesTheHeaderAndExposesItReadOnly() {
    byte[] header = JPEG_HEADER.clone();
    DetectionRequest request = DetectionRequest.of(header, Optional.empty());
    header[0] = 0;

    assertThat(request.byteAt(0)).isEqualTo(0xFF);
    assertThatThrownBy(() -> request.header().put((byte) 0))
        .isInstanceOf(ReadOnlyBufferException.class);
  }

  @Test
  void extractsExtensionInLowercase() {
    assertThat(extensionOf("photo.JPG")).contains("jpg");
    assertThat(extensionOf("archive.tar.gz")).contains("gz");
    assertThat(extensionOf(".bashrc")).isEmpty();
    assertThat(extensionOf("README")).isEmpty();
    assertThat(extensionOf("trailing.")).isEmpty();
    assertThat(DetectionRequest.of(new byte[0], Optional.empty()).extension()).isEmpty();
  }

  private static Optional<String> extensionOf(String name) {
    return DetectionRequest.of(new byte[0], Optional.of(name)).extension();
  }
}
