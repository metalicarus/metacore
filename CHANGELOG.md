# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
O projeto segue [versionamento semântico](https://semver.org/lang/pt-BR/).
Enquanto a versão for 0.x, versões minor podem trazer mudanças
incompatíveis, sempre registradas aqui.

## [0.1.0] - a definir

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
- Limites de recursos (`AnalysisLimits`), spool de streams em memória ou
  em arquivo temporário e cancelamento cooperativo por prazo ou
  interrupção.
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

[0.1.0]: https://github.com/metalicarus/metacore/releases/tag/v0.1.0
