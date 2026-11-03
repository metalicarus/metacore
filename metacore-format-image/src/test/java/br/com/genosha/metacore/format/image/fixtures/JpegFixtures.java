package br.com.genosha.metacore.format.image.fixtures;

import br.com.genosha.metacore.format.image.fixtures.TiffBuilder.Ifd;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Gera JPEGs de teste por código, para não depender de arquivos de terceiros. A imagem base vem do
 * codificador JPEG do próprio JDK; os segmentos EXIF e XMP são montados aqui e inseridos logo após
 * o APP0 (JFIF).
 *
 * <p>Todas as fixtures têm 64x48 pixels.
 */
public final class JpegFixtures {

  public static final int WIDTH = 64;
  public static final int HEIGHT = 48;

  // Tags EXIF usadas nas fixtures.
  private static final int MAKE = 0x010F;
  private static final int MODEL = 0x0110;
  private static final int ORIENTATION = 0x0112;
  private static final int IMAGE_WIDTH = 0x0100;
  private static final int IMAGE_HEIGHT = 0x0101;
  private static final int EXIF_IFD = 0x8769;
  private static final int GPS_IFD = 0x8825;
  private static final int EXPOSURE_TIME = 0x829A;
  private static final int EXIF_VERSION = 0x9000;
  private static final int DATETIME_ORIGINAL = 0x9003;
  private static final int OFFSET_TIME_ORIGINAL = 0x9011;
  private static final int EXIF_IMAGE_WIDTH = 0xA002;
  private static final int EXIF_IMAGE_HEIGHT = 0xA003;
  private static final int GPS_LATITUDE_REF = 0x0001;
  private static final int GPS_LATITUDE = 0x0002;
  private static final int GPS_LONGITUDE_REF = 0x0003;
  private static final int GPS_LONGITUDE = 0x0004;

  private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};
  private static final byte[] XMP_HEADER =
      "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);

  private JpegFixtures() {}

  /** JPEG sem EXIF nem XMP, só com JFIF e a estrutura da imagem. */
  public static byte[] plain() {
    return baseImage();
  }

  /** EXIF básico: fabricante, modelo, orientação, data com fuso, exposição e dimensões. */
  public static byte[] basicExif() {
    return withSegments(exif(basicIfd0(subIfd(true), null, null)));
  }

  /** EXIF básico mais coordenadas GPS (São Paulo: 23°33'00"S, 46°38'00"W). */
  public static byte[] withGps() {
    Ifd gps =
        new Ifd()
            .ascii(GPS_LATITUDE_REF, "S")
            .rational(GPS_LATITUDE, 23, 1, 33, 1, 0, 1)
            .ascii(GPS_LONGITUDE_REF, "W")
            .rational(GPS_LONGITUDE, 46, 1, 38, 1, 0, 1);
    return withSegments(exif(basicIfd0(subIfd(true), gps, null)));
  }

  /** EXIF com IFD1 declarando as dimensões da miniatura (160x120). */
  public static byte[] withThumbnailDimensions() {
    Ifd ifd1 = new Ifd().longValue(IMAGE_WIDTH, 160).longValue(IMAGE_HEIGHT, 120);
    return withSegments(exif(basicIfd0(subIfd(true), null, ifd1)));
  }

  /** EXIF sem {@code OffsetTimeOriginal}: a data fica sem fuso. */
  public static byte[] withoutOffset() {
    return withSegments(exif(basicIfd0(subIfd(false), null, null)));
  }

  /** EXIF com data inválida, para exercitar a falha de conversão. */
  public static byte[] withInvalidDate() {
    Ifd sub = new Ifd().ascii(DATETIME_ORIGINAL, "0000:00:00 00:00:00");
    return withSegments(exif(new Ifd().pointer(EXIF_IFD, sub)));
  }

  /** EXIF e XMP com fabricantes divergentes. */
  public static byte[] withConflictingXmp() {
    return withSegments(
        exif(basicIfd0(subIfd(true), null, null)),
        xmp(
            xmpPacket(
                "",
                "tiff:Make=\"Other Maker\" exif:DateTimeOriginal=\"2026-07-14T16:32:05-03:00\"",
                "")));
  }

  /** Só XMP, com data, exposição e fabricante. */
  public static byte[] xmpOnly() {
    return withSegments(
        xmp(
            xmpPacket(
                "",
                "tiff:Make=\"Genosha\" exif:ExposureTime=\"1/250\""
                    + " xmp:CreateDate=\"2026-01-02T03:04:05Z\"",
                "")));
  }

  /**
   * XMP com DOCTYPE declarando uma entidade externa que aponta para {@code secretUri}. Um parser
   * seguro não pode resolver a entidade.
   */
  public static byte[] xmpWithExternalEntity(String secretUri) {
    String doctype = "<!DOCTYPE x:xmpmeta [<!ENTITY xxe SYSTEM \"" + secretUri + "\">]>\n";
    return withSegments(xmp(xmpPacket(doctype, "", "<tiff:Make>&xxe;</tiff:Make>")));
  }

  /** XMP com expansão exponencial de entidades ("billion laughs"). */
  public static byte[] xmpWithEntityExpansion() {
    StringBuilder doctype = new StringBuilder("<!DOCTYPE x:xmpmeta [<!ENTITY l0 \"lol\">");
    for (int i = 1; i <= 10; i++) {
      doctype.append("<!ENTITY l").append(i).append(" \"");
      doctype.append(("&l" + (i - 1) + ";").repeat(10)).append("\">");
    }
    doctype.append("]>\n");
    return withSegments(xmp(xmpPacket(doctype.toString(), "", "<tiff:Make>&l10;</tiff:Make>")));
  }

  /** Corta o conteúdo no tamanho indicado. */
  public static byte[] truncate(byte[] content, int length) {
    return java.util.Arrays.copyOf(content, length);
  }

  /** TIFF do EXIF básico, sem o cabeçalho {@code Exif\0\0}; usado no chunk {@code eXIf} do PNG. */
  static byte[] basicExifTiff() {
    return TiffBuilder.build(basicIfd0(subIfd(true), null, null));
  }

  private static Ifd subIfd(boolean withOffset) {
    Ifd sub =
        new Ifd()
            .rational(EXPOSURE_TIME, 1, 125)
            .undefined(EXIF_VERSION, "0232".getBytes(StandardCharsets.US_ASCII))
            .ascii(DATETIME_ORIGINAL, "2026:07:14 16:32:05");
    if (withOffset) {
      sub.ascii(OFFSET_TIME_ORIGINAL, "-03:00");
    }
    return sub.longValue(EXIF_IMAGE_WIDTH, WIDTH).longValue(EXIF_IMAGE_HEIGHT, HEIGHT);
  }

  private static Ifd basicIfd0(Ifd sub, Ifd gps, Ifd ifd1) {
    Ifd ifd0 =
        new Ifd()
            .ascii(MAKE, "Genosha")
            .ascii(MODEL, "MC-1")
            .shortValue(ORIENTATION, 6)
            .pointer(EXIF_IFD, sub);
    if (gps != null) {
      ifd0.pointer(GPS_IFD, gps);
    }
    if (ifd1 != null) {
      ifd0.next(ifd1);
    }
    return ifd0;
  }

  private static byte[] exif(Ifd ifd0) {
    return segment(0xE1, concat(EXIF_HEADER, TiffBuilder.build(ifd0)));
  }

  private static byte[] xmp(String packet) {
    return segment(0xE1, concat(XMP_HEADER, packet.getBytes(StandardCharsets.UTF_8)));
  }

  /** Pacote XMP com os namespaces tiff, exif e xmp declarados. */
  static String xmpPacket(String doctype, String attributes, String elements) {
    return "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
        + doctype
        + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n"
        + " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
        + "  <rdf:Description rdf:about=\"\""
        + " xmlns:tiff=\"http://ns.adobe.com/tiff/1.0/\""
        + " xmlns:exif=\"http://ns.adobe.com/exif/1.0/\""
        + " xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\" "
        + attributes
        + ">"
        + elements
        + "</rdf:Description>\n"
        + " </rdf:RDF>\n"
        + "</x:xmpmeta>\n"
        + "<?xpacket end=\"w\"?>";
  }

  private static byte[] segment(int marker, byte[] payload) {
    int length = payload.length + 2;
    if (length > 0xFFFF) {
      throw new IllegalArgumentException("Segment too large: " + length);
    }
    byte[] segment = new byte[payload.length + 4];
    segment[0] = (byte) 0xFF;
    segment[1] = (byte) marker;
    segment[2] = (byte) (length >> 8);
    segment[3] = (byte) length;
    System.arraycopy(payload, 0, segment, 4, payload.length);
    return segment;
  }

  /** Insere os segmentos depois do APP0 (ou logo após o SOI, se não houver APP0). */
  private static byte[] withSegments(byte[]... segments) {
    byte[] base = baseImage();
    int insertAt = 2;
    if ((base[2] & 0xFF) == 0xFF && (base[3] & 0xFF) == 0xE0) {
      insertAt = 4 + (((base[4] & 0xFF) << 8) | (base[5] & 0xFF));
    }
    List<byte[]> parts = new ArrayList<>();
    parts.add(java.util.Arrays.copyOfRange(base, 0, insertAt));
    parts.addAll(List.of(segments));
    parts.add(java.util.Arrays.copyOfRange(base, insertAt, base.length));
    return concat(parts.toArray(byte[][]::new));
  }

  private static byte[] baseImage() {
    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        image.setRGB(x, y, (x * 4) << 16 | (y * 5) << 8 | 0x80);
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(image, "jpg", out)) {
        throw new IllegalStateException("No JPEG writer available");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  private static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }
}
