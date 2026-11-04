package br.com.genosha.metacore.comparison;

import br.com.genosha.metacore.format.image.fixtures.JpegFixtures;
import br.com.genosha.metacore.format.image.fixtures.PngFixtures;
import br.com.genosha.metacore.format.pdf.PdfFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Os mesmos arquivos de teste dos módulos de formato, para os dois lados da comparação. */
final class Fixtures {

  record Fixture(String name, String fileName, byte[] content) {}

  private Fixtures() {}

  static List<Fixture> all(String secretUri) {
    byte[] exif = JpegFixtures.basicExif();
    byte[] pngText = PngFixtures.withText();
    byte[] pdfInfo = PdfFixtures.withInfo();
    return List.of(
        new Fixture("jpeg-basic-exif", "photo.jpg", exif),
        new Fixture("jpeg-gps", "photo.jpg", JpegFixtures.withGps()),
        new Fixture("jpeg-xmp-only", "photo.jpg", JpegFixtures.xmpOnly()),
        new Fixture("jpeg-conflicting-xmp", "photo.jpg", JpegFixtures.withConflictingXmp()),
        new Fixture("jpeg-invalid-date", "photo.jpg", JpegFixtures.withInvalidDate()),
        new Fixture(
            "jpeg-truncated-half", "photo.jpg", JpegFixtures.truncate(exif, exif.length / 2)),
        new Fixture("jpeg-xxe", "photo.jpg", JpegFixtures.xmpWithExternalEntity(secretUri)),
        new Fixture(
            "text-named-jpg", "photo.jpg", "not an image".getBytes(StandardCharsets.US_ASCII)),
        new Fixture("png-text", "image.png", pngText),
        new Fixture("png-exif", "image.png", PngFixtures.withExif()),
        new Fixture("png-xmp", "image.png", PngFixtures.withXmp()),
        new Fixture(
            "png-zip-bomb-8mb", "image.png", PngFixtures.withCompressionBomb(8 * 1024 * 1024)),
        new Fixture("png-truncated", "image.png", PngFixtures.truncate(pngText, 120)),
        new Fixture("pdf-info", "doc.pdf", pdfInfo),
        new Fixture("pdf-1.7-info-and-xmp", "doc.pdf", PdfFixtures.infoAndXmp()),
        new Fixture("pdf-2.0-info-and-xmp", "doc.pdf", PdfFixtures.pdf20InfoAndXmp()),
        new Fixture("pdf-invalid-date", "doc.pdf", PdfFixtures.invalidDate()),
        new Fixture("pdf-user-password", "doc.pdf", PdfFixtures.userPassword()),
        new Fixture("pdf-owner-password", "doc.pdf", PdfFixtures.ownerPasswordOnly()),
        new Fixture(
            "pdf-truncated-half", "doc.pdf", PdfFixtures.truncate(pdfInfo, pdfInfo.length / 2)),
        new Fixture("pdf-xxe", "doc.pdf", PdfFixtures.xmpWithExternalEntity(secretUri)));
  }
}
