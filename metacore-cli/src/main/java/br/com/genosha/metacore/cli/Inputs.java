package br.com.genosha.metacore.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Expande os argumentos em entradas. Arquivos informados são analisados como vieram; pastas são
 * percorridas em ordem alfabética, sem seguir links simbólicos, pulando arquivos e pastas ocultos e
 * aplicando {@code --include} e {@code --exclude}.
 */
final class Inputs {

  /** As entradas e quantas subpastas ficaram de fora por falta de {@code --recursive}. */
  record Expansion(List<Input> inputs, int skippedDirectories) {}

  private final boolean recursive;
  private final boolean hidden;
  private final List<Glob> include;
  private final List<Glob> exclude;
  private final List<Input> inputs = new ArrayList<>();
  private int skippedDirectories;
  private boolean standardInput;

  private Inputs(boolean recursive, boolean hidden, List<String> include, List<String> exclude) {
    this.recursive = recursive;
    this.hidden = hidden;
    this.include = include.stream().map(Glob::new).toList();
    this.exclude = exclude.stream().map(Glob::new).toList();
  }

  static Expansion expand(
      List<Path> arguments,
      boolean recursive,
      boolean hidden,
      List<String> include,
      List<String> exclude) {
    Inputs inputs = new Inputs(recursive, hidden, include, exclude);
    arguments.forEach(inputs::argument);
    return new Expansion(List.copyOf(inputs.inputs), inputs.skippedDirectories);
  }

  private void argument(Path argument) {
    if (argument.toString().equals(Input.STANDARD_INPUT)) {
      if (standardInput) {
        inputs.add(
            Input.unreadable(
                argument, "<stdin>", "Standard input can only be given once (as '-')"));
      } else {
        standardInput = true;
        inputs.add(Input.standardInput());
      }
    } else if (Files.isDirectory(argument)) {
      directory(argument, argument);
    } else {
      Path name = argument.getFileName();
      inputs.add(Input.file(argument, name == null ? argument.toString() : name.toString()));
    }
  }

  private void directory(Path root, Path dir) {
    List<Path> entries;
    try (Stream<Path> list = Files.list(dir)) {
      entries = list.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
    } catch (IOException | UncheckedIOException e) {
      inputs.add(Input.unreadable(dir, label(root, dir), "Could not read directory: " + e));
      return;
    }
    for (Path entry : entries) {
      String name = entry.getFileName().toString();
      if (!hidden && name.startsWith(".")) {
        continue;
      }
      BasicFileAttributes attributes;
      try {
        attributes =
            Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      } catch (IOException e) {
        inputs.add(Input.unreadable(entry, label(root, entry), "Could not read: " + e));
        continue;
      }
      if (attributes.isDirectory()) {
        if (recursive) {
          directory(root, entry);
        } else {
          skippedDirectories++;
        }
      } else if (attributes.isRegularFile() && selected(root.relativize(entry))) {
        inputs.add(Input.file(entry, label(root, entry)));
      }
    }
  }

  private boolean selected(Path relative) {
    return (include.isEmpty() || include.stream().anyMatch(g -> g.matches(relative)))
        && exclude.stream().noneMatch(g -> g.matches(relative));
  }

  private static String label(Path root, Path file) {
    String relative = root.relativize(file).toString();
    return relative.isEmpty() ? file.toString() : relative;
  }

  /**
   * Padrão de nome, sem diferenciar maiúsculas. Sem barra, vale para o nome do arquivo; com barra,
   * para o caminho relativo à pasta informada.
   */
  private record Glob(PathMatcher matcher, boolean wholePath) {

    Glob(String pattern) {
      this(
          FileSystems.getDefault().getPathMatcher("glob:" + pattern.toLowerCase(Locale.ROOT)),
          pattern.contains("/"));
    }

    boolean matches(Path relative) {
      Path target = wholePath ? relative : relative.getFileName();
      return matcher.matches(Path.of(target.toString().toLowerCase(Locale.ROOT)));
    }
  }
}
