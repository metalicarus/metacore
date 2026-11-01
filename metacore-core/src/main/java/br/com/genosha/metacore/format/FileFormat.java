package br.com.genosha.metacore.format;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Identidade de um formato de arquivo. Não carrega confiança: ela pertence a {@link
 * DetectionResult}.
 *
 * @param id identificador estável, por exemplo {@code jpeg}
 * @param mimeType tipo MIME principal
 * @param displayName nome legível
 * @param extensions extensões comuns, em minúsculas e sem ponto
 * @param parent identificador do supertipo, por exemplo {@code zip} para {@code docx}
 */
public record FileFormat(
    String id,
    String mimeType,
    String displayName,
    List<String> extensions,
    Optional<String> parent) {

  private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._+-]*");
  private static final Pattern EXTENSION = Pattern.compile("[a-z0-9][a-z0-9_+-]*");

  public FileFormat {
    requireId(id, "id");
    requireText(mimeType, "mimeType");
    requireText(displayName, "displayName");
    extensions = List.copyOf(extensions);
    for (String extension : extensions) {
      if (!EXTENSION.matcher(extension).matches()) {
        throw new IllegalArgumentException(
            "Extension must be lowercase and without a leading dot: " + extension);
      }
    }
    Objects.requireNonNull(parent, "parent");
    parent.ifPresent(
        p -> {
          requireId(p, "parent");
          if (p.equals(id)) {
            throw new IllegalArgumentException("Format cannot be its own parent: " + id);
          }
        });
  }

  /** Cria um formato sem supertipo. */
  public static FileFormat of(
      String id, String mimeType, String displayName, String... extensions) {
    return new FileFormat(id, mimeType, displayName, List.of(extensions), Optional.empty());
  }

  /** Devolve uma cópia com o supertipo informado. */
  public FileFormat withParent(String parentId) {
    return new FileFormat(id, mimeType, displayName, extensions, Optional.of(parentId));
  }

  /** Indica se a extensão (sem ponto, em qualquer caixa) é uma das extensões comuns do formato. */
  public boolean hasExtension(String extension) {
    return extensions.contains(extension.toLowerCase(Locale.ROOT));
  }

  private static void requireId(String value, String name) {
    Objects.requireNonNull(value, name);
    if (!ID.matcher(value).matches()) {
      throw new IllegalArgumentException("Invalid format " + name + ": " + value);
    }
  }

  private static void requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
