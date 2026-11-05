# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
O projeto segue [versionamento semântico](https://semver.org/lang/pt-BR/).
Enquanto a versão for 0.x, versões minor podem trazer mudanças
incompatíveis, sempre registradas aqui.

## [0.2.0] - 2026-11-04

### Biblioteca

- Módulo `metacore-isolation` (ADR 0016): `IsolatedAnalyzer` analisa
  arquivos e streams em processos Java separados, reaproveitados entre
  análises, com prazo rígido (`timeout`) e teto de memória
  (`maxHeapSize`). Um parser travado vira `FAILED` com aviso `TIMEOUT`;
  falta de memória ou queda da JVM do worker, `FAILED` com
  `WORKER_FAILED`. Streams continuam lidos sob demanda.
- `FileInfo.of(name, size)` calcula a extensão a partir do nome.
- Módulo `metacore-format-office` (ADR 0018): DOCX, XLSX e PPTX, com as
  propriedades `core`, `app` e `custom`, o content type e a presença de
  macros, além de ZIP genérico (entradas, tamanho, comentário). Leitor
  próprio, sem Apache POI e sem dependências além do JDK. Arquivos
  truncados são lidos pelos cabeçalhos locais.
- Chave `document.last_modified_by`.
- ZIP, JAR, EPUB e ODF deixam de sair como `UNKNOWN_FORMAT` e passam a
  sair como `zip`.

### CLI

- `inspect --isolate`, com `--timeout` e `--max-memory`.
- O runtime do executável passa a incluir `bin/java`, usado pelos
  workers.
- `inspect` vira o comando padrão: `metacore foto.jpg`. Sem argumentos,
  `metacore` mostra o nome em ASCII e exemplos, e sai com 0 (antes, 2).
- Pastas como entrada, com `-r`, `--include`, `--exclude` e `--hidden`
  (ADR 0017). `-` lê a entrada padrão.
- `-j N` analisa arquivos em paralelo, mantendo a ordem da saída.
- Saída `-o csv`, com uma linha por arquivo e proteção contra fórmulas.
- `-k` seleciona campos por chave, no texto, no JSON e no CSV.
- Nova saída em texto: campos agrupados, valores legíveis, cores
  (`--color`, `NO_COLOR`), resumo final e progresso ao gravar em
  arquivo. Caracteres de controle dos metadados viram escapes visíveis.
  A saída em texto nunca foi contrato; `-q` mantém o formato anterior.
- Inclui o módulo Office: `metacore relatorio.docx`.
- Ajuda agrupada por seções, erros de uso curtos com sugestões e
  `metacore completion` para bash e zsh.

## [0.1.0] - 2026-11-04

Primeira versão: core, CLI e os formatos JPEG, PNG e PDF.

### Biblioteca

- `MetadataAnalyzer` com `analyze` (arquivo, stream ou `InputSource`) e
  `identify`; imutável, thread-safe e com descoberta de módulos por
  `ServiceLoader`.
- Resultado com cinco estados (`SUCCESS`, `PARTIAL`, `UNSUPPORTED`,
  `UNKNOWN_FORMAT`, `FAILED`), avisos com códigos estáveis e consultas
  `find`, `findAll` e `findRaw`.
- Valores tipados (`MetadataValue`): texto, inteiro, decimal, fração,
  booleano, data com e sem fuso, binário e lista. `toString()` devolve o
  próprio valor (`4000`, `Genosha`, `2026-07-14T16:32:05-03:00`).
- Origem de cada campo (nome original, namespace e parser) e valor
  principal por chave, escolhido por precedência documentada.
- Formatos com hierarquia de supertipos e resolução de conflitos entre
  detectores.
- Limites de recursos (`AnalysisLimits`) e cancelamento cooperativo por
  prazo ou interrupção.
- `InputStream` lido sob demanda: só o que o parser precisa sai do
  stream, guardado em memória até um limiar e em arquivo temporário
  acima dele. O tamanho de `InputSource` e `FileInfo` é `OptionalLong`,
  porque um stream só sabe o tamanho se for lido até o fim.
- SPI para módulos de terceiros (`FormatDetector`, `MetadataParser`,
  `MetadataProvider`) e `metacore-testkit` com a suíte de contrato.

### Formatos

- JPEG: estrutura, JFIF, EXIF, GPS em graus decimais, XMP e demais
  segmentos do metadata-extractor, com leitura tolerante a truncamento.
- PNG: IHDR, texto, EXIF (`eXIf`), XMP e demais chunks, com leitura
  tolerante a truncamento e limite de descompactação.
- PDF: versão, páginas, criptografia, dicionário Info e XMP, com datas
  tipadas e precedência Info/XMP conforme a versão do PDF.

### CLI

- Comandos `identify` e `inspect`, saída em texto e em JSON (NDJSON,
  `schemaVersion` 1), códigos de saída 0 a 3.
- Executável com runtime próprio via jpackage (perfil `dist`) e workflow
  que gera pacotes para Linux, macOS e Windows a cada tag.

### Qualidade e segurança

- Proteção contra XXE no XMP e contra zip bomb no PNG, com testes.
- Fixtures geradas por código, golden files, testes de robustez com
  mutações reproduzíveis e alvos de fuzzing com Jazzer (com corpus
  inicial). Sem falhas em 10 min de JPEG (4,4 milhões de execuções),
  4 min de PNG (2,6 milhões) e 10 min de PDF (1,2 milhão).
- Benchmarks JMH (`metacore-benchmarks`) e limites padrão calibrados a
  partir deles (`docs/performance.md`).
- Dependências verificadas quanto a licença e vulnerabilidades
  conhecidas (`docs/dependencies.md`).
- CI com Java 21 (biblioteca) e 25 (tudo).
- Comparação reproduzível com o Apache Tika 4.1 (`metacore-comparison`,
  perfil `comparison`) e decisão registrada no ADR 0014.

[0.2.0]: https://github.com/metalicarus/metacore/releases/tag/v0.2.0
[0.1.0]: https://github.com/metalicarus/metacore/releases/tag/v0.1.0
