package br.com.genosha.metacore.format;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Conjunto imutável de formatos conhecidos, com a hierarquia de supertipos validada: todo {@code
 * parent} existe e não há ciclos.
 */
public final class FormatRegistry {

  private final Map<String, FileFormat> formats;

  private FormatRegistry(Map<String, FileFormat> formats) {
    this.formats = formats;
  }

  /**
   * Cria o registro. Formatos repetidos e idênticos são aceitos uma vez; dois formatos diferentes
   * com o mesmo {@code id} são rejeitados.
   *
   * @throws IllegalArgumentException se houver conflito de {@code id}, supertipo inexistente ou
   *     ciclo na hierarquia
   */
  public static FormatRegistry of(Collection<FileFormat> formats) {
    Map<String, FileFormat> byId = new LinkedHashMap<>();
    for (FileFormat format : formats) {
      FileFormat previous = byId.putIfAbsent(format.id(), format);
      if (previous != null && !previous.equals(format)) {
        throw new IllegalArgumentException(
            "Conflicting definitions for format '" + format.id() + "'");
      }
    }
    for (FileFormat format : byId.values()) {
      Set<String> seen = new HashSet<>();
      Optional<String> current = Optional.of(format.id());
      while (current.isPresent()) {
        String id = current.get();
        if (!seen.add(id)) {
          throw new IllegalArgumentException("Cycle in format hierarchy at '" + id + "'");
        }
        FileFormat node = byId.get(id);
        if (node == null) {
          throw new IllegalArgumentException(
              "Format '" + format.id() + "' has unknown ancestor '" + id + "'");
        }
        current = node.parent();
      }
    }
    return new FormatRegistry(Map.copyOf(byId));
  }

  /** Busca um formato pelo identificador. */
  public Optional<FileFormat> find(String id) {
    return Optional.ofNullable(formats.get(id));
  }

  /** Todos os formatos registrados. */
  public Collection<FileFormat> all() {
    return formats.values();
  }

  /**
   * O formato e seus supertipos, do mais específico ao mais genérico. Vazio se o formato não
   * existir.
   */
  public List<FileFormat> lineage(String id) {
    List<FileFormat> lineage = new ArrayList<>();
    Optional<FileFormat> current = find(id);
    while (current.isPresent()) {
      FileFormat format = current.get();
      lineage.add(format);
      current = format.parent().flatMap(this::find);
    }
    return List.copyOf(lineage);
  }

  /** Indica se {@code id} é o próprio {@code ancestorId} ou um descendente dele. */
  public boolean isSameOrDescendant(String id, String ancestorId) {
    return lineage(id).stream().anyMatch(format -> format.id().equals(ancestorId));
  }
}
