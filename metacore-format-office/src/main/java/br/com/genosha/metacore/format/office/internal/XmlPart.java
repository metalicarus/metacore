package br.com.genosha.metacore.format.office.internal;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Uma parte XML do pacote lida como árvore pequena. O parser do JDK roda sem DTD e sem entidades
 * externas, e um {@code DOCTYPE} recusa a parte inteira: partes OPC nunca têm um (ISO/IEC 29500-2,
 * 8.1.4), e é por ele que entram XXE e expansão de entidades.
 *
 * @param namespace URI do namespace
 * @param name nome local
 * @param attributes atributos sem namespace, pelo nome local
 * @param text texto direto do elemento, sem espaços nas pontas
 * @param children elementos filhos
 */
public record XmlPart(
    String namespace,
    String name,
    Map<String, String> attributes,
    String text,
    List<XmlPart> children) {

  private static final int MAX_DEPTH = 32;
  private static final int MAX_ELEMENTS = 100_000;

  private static final XMLInputFactory FACTORY = factory();

  private static XMLInputFactory factory() {
    XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory;
  }

  /** Lê a parte inteira. */
  public static XmlPart parse(byte[] content) throws XMLStreamException {
    XMLStreamReader reader;
    synchronized (FACTORY) {
      reader = FACTORY.createXMLStreamReader(new ByteArrayInputStream(content));
    }
    try {
      return read(reader);
    } finally {
      reader.close();
    }
  }

  private static XmlPart read(XMLStreamReader reader) throws XMLStreamException {
    Deque<Builder> open = new ArrayDeque<>();
    XmlPart root = null;
    int elements = 0;
    while (reader.hasNext()) {
      switch (reader.next()) {
        case XMLStreamConstants.DTD -> throw new XMLStreamException("DOCTYPE is not allowed");
        case XMLStreamConstants.ENTITY_REFERENCE ->
            throw new XMLStreamException("Entity references are not allowed");
        case XMLStreamConstants.START_ELEMENT -> {
          if (++elements > MAX_ELEMENTS || open.size() >= MAX_DEPTH) {
            throw new XMLStreamException("Part is too large or too deeply nested");
          }
          Map<String, String> attributes = new LinkedHashMap<>();
          for (int i = 0; i < reader.getAttributeCount(); i++) {
            String namespace = reader.getAttributeNamespace(i);
            if (namespace == null || namespace.isEmpty()) {
              attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            }
          }
          String namespace = reader.getNamespaceURI();
          open.push(
              new Builder(namespace == null ? "" : namespace, reader.getLocalName(), attributes));
        }
        case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE -> {
          if (!open.isEmpty()) {
            open.peek().text.append(reader.getText());
          }
        }
        case XMLStreamConstants.END_ELEMENT -> {
          XmlPart element = open.pop().build();
          if (open.isEmpty()) {
            root = element;
          } else {
            open.peek().children.add(element);
          }
        }
        default -> {}
      }
    }
    if (root == null) {
      throw new XMLStreamException("Part has no root element");
    }
    return root;
  }

  /** Primeiro filho com o namespace e o nome informados. */
  public Optional<XmlPart> child(String childNamespace, String childName) {
    return children.stream()
        .filter(c -> c.namespace.equals(childNamespace) && c.name.equals(childName))
        .findFirst();
  }

  /** Textos das folhas abaixo deste elemento, em ordem. */
  public List<String> leafTexts() {
    List<String> texts = new ArrayList<>();
    collect(this, texts);
    return texts;
  }

  private static void collect(XmlPart element, List<String> texts) {
    if (element.children.isEmpty()) {
      if (!element.text.isEmpty()) {
        texts.add(element.text);
      }
      return;
    }
    element.children.forEach(c -> collect(c, texts));
  }

  private static final class Builder {
    private final String namespace;
    private final String name;
    private final Map<String, String> attributes;
    private final StringBuilder text = new StringBuilder();
    private final List<XmlPart> children = new ArrayList<>();

    Builder(String namespace, String name, Map<String, String> attributes) {
      this.namespace = namespace;
      this.name = name;
      this.attributes = attributes;
    }

    XmlPart build() {
      return new XmlPart(
          namespace, name, Map.copyOf(attributes), text.toString().strip(), List.copyOf(children));
    }
  }
}
