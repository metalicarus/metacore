package br.com.genosha.metacore.format.image.fixtures;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta uma estrutura TIFF (little-endian), como a que fica dentro do segmento EXIF. Suporta os
 * tipos usados nas fixtures e ponteiros entre IFDs.
 */
public final class TiffBuilder {

  private static final int ASCII = 2;
  private static final int SHORT = 3;
  private static final int LONG = 4;
  private static final int RATIONAL = 5;
  private static final int UNDEFINED = 7;

  /** Um diretório (IFD) com suas entradas. */
  public static final class Ifd {
    private final List<Entry> entries = new ArrayList<>();
    private Ifd next;

    public Ifd ascii(int tag, String value) {
      byte[] text = (value + "\0").getBytes(StandardCharsets.US_ASCII);
      return add(new Entry(tag, ASCII, text.length, text, null));
    }

    public Ifd shortValue(int tag, int value) {
      return add(new Entry(tag, SHORT, 1, le(2).putShort((short) value).array(), null));
    }

    public Ifd longValue(int tag, long value) {
      return add(new Entry(tag, LONG, 1, le(4).putInt((int) value).array(), null));
    }

    /** Uma ou mais frações, como pares (numerador, denominador). */
    public Ifd rational(int tag, long... pairs) {
      ByteBuffer buffer = le(pairs.length * 4);
      for (long value : pairs) {
        buffer.putInt((int) value);
      }
      return add(new Entry(tag, RATIONAL, pairs.length / 2, buffer.array(), null));
    }

    public Ifd undefined(int tag, byte[] value) {
      return add(new Entry(tag, UNDEFINED, value.length, value.clone(), null));
    }

    /** Ponteiro para outro IFD (por exemplo, ExifIFD 0x8769 ou GPS 0x8825). */
    public Ifd pointer(int tag, Ifd target) {
      return add(new Entry(tag, LONG, 1, null, target));
    }

    /** Próximo IFD da cadeia (IFD1, da miniatura). */
    public Ifd next(Ifd nextIfd) {
      this.next = nextIfd;
      return this;
    }

    private Ifd add(Entry entry) {
      entries.add(entry);
      return this;
    }

    private int size() {
      int size = 2 + 12 * entries.size() + 4;
      for (Entry entry : entries) {
        size += entry.outOfLineSize();
      }
      return size;
    }
  }

  private record Entry(int tag, int type, int count, byte[] data, Ifd target) {
    int outOfLineSize() {
      if (data == null || data.length <= 4) {
        return 0;
      }
      return data.length + (data.length % 2);
    }
  }

  private TiffBuilder() {}

  /** Serializa o TIFF a partir do IFD0, incluindo todos os IFDs alcançáveis. */
  public static byte[] build(Ifd ifd0) {
    List<Ifd> order = new ArrayList<>();
    collect(ifd0, order);
    Map<Ifd, Integer> offsets = new IdentityHashMap<>();
    int offset = 8;
    for (Ifd ifd : order) {
      offsets.put(ifd, offset);
      offset += ifd.size();
    }

    ByteBuffer out = le(offset);
    out.put(new byte[] {'I', 'I', 42, 0}).putInt(8);
    for (Ifd ifd : order) {
      int start = offsets.get(ifd);
      int dataOffset = start + 2 + 12 * ifd.entries.size() + 4;
      out.position(start);
      out.putShort((short) ifd.entries.size());
      ByteArrayOutputStream outOfLine = new ByteArrayOutputStream();
      for (Entry entry : ifd.entries) {
        out.putShort((short) entry.tag()).putShort((short) entry.type()).putInt(entry.count());
        if (entry.target() != null) {
          out.putInt(offsets.get(entry.target()));
        } else if (entry.data().length <= 4) {
          byte[] inline = new byte[4];
          System.arraycopy(entry.data(), 0, inline, 0, entry.data().length);
          out.put(inline);
        } else {
          out.putInt(dataOffset + outOfLine.size());
          outOfLine.writeBytes(entry.data());
          if (entry.data().length % 2 == 1) {
            outOfLine.write(0);
          }
        }
      }
      out.putInt(ifd.next == null ? 0 : offsets.get(ifd.next));
      out.put(outOfLine.toByteArray());
    }
    return out.array();
  }

  private static void collect(Ifd ifd, List<Ifd> order) {
    if (ifd == null || order.stream().anyMatch(i -> i == ifd)) {
      return;
    }
    order.add(ifd);
    for (Entry entry : ifd.entries) {
      collect(entry.target(), order);
    }
    collect(ifd.next, order);
  }

  private static ByteBuffer le(int size) {
    return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
  }
}
