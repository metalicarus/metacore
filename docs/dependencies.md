# Dependências e licenças

O MetaCore é distribuído sob a [Apache License 2.0](../LICENSE). As
dependências de runtime abaixo foram verificadas na preparação do
release 0.1.0 (novembro de 2026): todas têm licença compatível com a
redistribuição sob Apache 2.0, e nenhuma tinha vulnerabilidade conhecida
na base [OSV](https://osv.dev).

| Dependência | Versão | Licença | Usada em |
|---|---|---|---|
| `org.slf4j:slf4j-api` | 2.0.20 | MIT | core |
| `com.drewnoakes:metadata-extractor` | 2.21.0 | Apache 2.0 | format-image |
| `com.adobe.xmp:xmpcore` | 6.1.11 | BSD 3-Clause | format-image (via metadata-extractor), format-pdf |
| `org.apache.pdfbox:pdfbox`, `pdfbox-io`, `fontbox` | 3.0.8 | Apache 2.0 | format-pdf |
| `commons-logging:commons-logging` | 1.4.0 | Apache 2.0 | format-pdf (via PDFBox) |
| `info.picocli:picocli` | 4.7.7 | Apache 2.0 | cli |
| `com.fasterxml.jackson.core:jackson-*` | 2.22.4 | Apache 2.0 | cli |
| `ch.qos.logback:logback-classic`, `logback-core` | 1.6.5 | EPL 1.0 ou LGPL 2.1 | cli |

Notas:

- A biblioteca não embute dependências: cada JAR publicado só declara as
  suas. Quem usa o `metacore-core` recebe apenas a SLF4J API.
- O executável da CLI embute todos os JARs acima, sem alterações, com
  seus arquivos `META-INF/LICENSE` e `META-INF/NOTICE`, e um runtime
  OpenJDK reduzido (GPLv2 com Classpath Exception), cujos avisos ficam em
  `lib/runtime/legal`. O arquivo [NOTICE](../NOTICE) reúne os avisos
  exigidos pelas dependências Apache.
- O Logback entra só na CLI. A escolha entre EPL e LGPL é do
  distribuidor; ambas permitem a redistribuição do JAR sem alterações.

## Dependências de build e teste

JUnit 5, AssertJ, Mockito, Jazzer, JMH, Spotless, JaCoCo e OWASP
Dependency-Check são usados só no build e nos testes, e não fazem parte
dos artefatos publicados (exceto `junit-jupiter-api` e `assertj-core`,
dependências do `metacore-testkit`, que deve ser usado com escopo
`test`).

## Como verificar de novo

```bash
mvn -Psecurity verify
```

O perfil `security` roda o OWASP Dependency-Check e falha com CVSS 7 ou
mais. Defina `NVD_API_KEY` para não esperar o download lento da base do
NVD.
