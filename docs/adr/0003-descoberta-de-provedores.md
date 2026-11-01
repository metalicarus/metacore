# 0003. Descoberta de provedores e JPMS

- Estado: Aceito
- Data: 2026-11-01

## Contexto

Os módulos de formato precisam ser encontrados pelo core sem que o core
dependa deles. Ainda não está confirmado que metadata-extractor e PDFBox
funcionam bem no module path.

## Decisão

- Os módulos de formato se registram por `ServiceLoader`, com arquivos
  em `META-INF/services`.
- No MVP, cada JAR publica `Automatic-Module-Name` no manifesto, sem
  `module-info.java`:

  | Módulo | `Automatic-Module-Name` |
  |---|---|
  | `metacore-core` | `br.com.genosha.metacore.core` |
  | `metacore-format-image` | `br.com.genosha.metacore.format.image` |
  | `metacore-format-pdf` | `br.com.genosha.metacore.format.pdf` |
  | `metacore-cli` | `br.com.genosha.metacore.cli` |
  | `metacore-examples` | `br.com.genosha.metacore.examples` |

- O nome é definido pela propriedade `module.name` de cada módulo e
  aplicado pelo `maven-jar-plugin` no POM pai.

## Consequências

- Os nomes de módulo ficam estáveis desde já; adotar `module-info.java`
  depois não muda o nome que os consumidores usam.
- Sem `module-info.java`, o encapsulamento do pacote `internal` é só por
  convenção até a adoção completa do JPMS.
