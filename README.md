# MetaCore

Biblioteca Java para identificar formatos de arquivo e extrair metadados
com uma API uniforme e tipada, acompanhada de uma ferramenta de linha de
comando.

> **Estado:** pré-alfa. Os contratos do core (Fase 2) estão prontos e
> testados com detectores e parsers falsos; ainda não há suporte a
> formatos reais. O plano completo está em
> [docs/blueprint.md](docs/blueprint.md).

## Uso

```java
MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();
MetadataResult result = analyzer.analyze(Path.of("foto.jpg"));

result.find(MetadataKeys.IMAGE_WIDTH).ifPresent(m -> System.out.println(m.value()));
```

`MetadataAnalyzer` é imutável e thread-safe. Exceções só aparecem quando
a análise não pode começar (arquivo inexistente ou acima do limite); o
resto vira `status` e `warnings` no resultado.

## Módulos

| Módulo | Conteúdo | Java |
|---|---|---|
| `metacore-bom` | Alinhamento de versões para quem consome a biblioteca | — |
| `metacore-core` | Modelos públicos, SPI e orquestração da análise | 21+ |
| `metacore-testkit` | Suíte de contrato para parsers (dependência de teste) | 21+ |
| `metacore-format-image` | JPEG e PNG (metadata-extractor) | 21+ |
| `metacore-format-pdf` | PDF (Apache PDFBox) | 21+ |
| `metacore-cli` | Ferramenta `metacore` (picocli) | 25 |
| `metacore-examples` | Exemplos de integração; não é publicado | 21+ |

Coordenadas Maven: `groupId` `br.com.genosha`. Pacotes:
`br.com.genosha.metacore`.

## Build

Requisitos: Maven 3.9+ e JDK 25 (o JDK 25 compila a biblioteca com
`--release 21` e a CLI com `--release 25`).

```bash
mvn verify
```

Para verificar só a biblioteca em JDK 21:

```bash
mvn verify -pl '!metacore-cli'
```

Comandos úteis:

| Comando | O que faz |
|---|---|
| `mvn spotless:apply` | Formata o código (google-java-format) |
| `mvn -Psecurity verify` | Roda o OWASP Dependency-Check; defina `NVD_API_KEY` para acelerar |

O relatório de cobertura do JaCoCo fica em
`<módulo>/target/site/jacoco/index.html`.

## Documentação

- [Blueprint e roadmap](docs/blueprint.md)
- [Registros de decisão (ADRs)](docs/adr/README.md)
- [Como contribuir](CONTRIBUTING.md)
- [Segurança](SECURITY.md)
- [Changelog](CHANGELOG.md)

## Licença

[Apache License 2.0](LICENSE).
