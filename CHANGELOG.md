# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
O projeto segue [versionamento semântico](https://semver.org/lang/pt-BR/).

## [Não lançado]

### Adicionado

- Suporte a PNG (Fase 5): IHDR, texto (`tEXt`, `zTXt`, `iTXt`), EXIF
  (`eXIf`), XMP e demais chunks, com leitura tolerante a truncamento e
  limite de descompactação para texto compactado.
- Suporte a PDF (Fase 5) no `metacore-format-pdf`: versão, páginas,
  criptografia, dicionário Info e XMP, com datas tipadas e precedência
  Info/XMP conforme a versão do PDF.
- `ResultRenderer` e `GoldenFiles` no `metacore-testkit`.
- ADR 0012.

### Alterado

- Datas no JSON e no texto da CLI sempre com segundos
  (`2026-08-01T09:00:00Z`, e não `2026-08-01T09:00Z`).
- CLI (Fase 4): comandos `identify` e `inspect`, saída em texto e em
  JSON (NDJSON, `schemaVersion` 1), opções `--file`, `--max-file-size`,
  `--show-path`, `--strict`, `--verbose`, `--quiet` e
  `--include-binary`, códigos de saída 0 a 3 e perfil `dist` que gera um
  executável com runtime próprio via jpackage.
- ADR 0011.
- Suporte a JPEG (Fase 3) no `metacore-format-image`: detector,
  parser com EXIF, GPS, XMP e demais segmentos do metadata-extractor,
  leitura tolerante a arquivos truncados, chaves normalizadas com
  precedência, fixtures geradas por código, golden files, testes de XXE,
  testes de robustez e alvo de fuzzing com Jazzer.
- `docs/keys.md` com o vocabulário de chaves e a precedência do JPEG.
- ADR 0010.
- Contratos do core (Fase 2): `MetadataAnalyzer` com `analyze` e
  `identify`, `MetadataResult`, `AnalysisLimits`, formatos com
  hierarquia (`FileFormat`, `FormatRegistry`), detecção com resolução de
  conflitos, valores tipados (`MetadataValue`), avisos com códigos
  estáveis, `InputSource` com spool de streams, cancelamento cooperativo
  e SPI (`FormatDetector`, `MetadataParser`, `MetadataProvider`) com
  descoberta por `ServiceLoader`.
- Módulo `metacore-testkit` com a suíte de contrato para parsers.
- ADRs 0004 a 0009.
- Estrutura Maven multi-módulo: `metacore-bom`, `metacore-core`,
  `metacore-format-image`, `metacore-format-pdf`, `metacore-cli` e
  `metacore-examples`.
- Build com `--release 21` para a biblioteca e `--release 25` para a
  CLI.
- JUnit 5, AssertJ, Mockito, JaCoCo, Spotless (google-java-format) e
  perfil `security` com OWASP Dependency-Check.
- CI no GitHub Actions com Java 21 e 25.
- Documentação inicial e ADRs 0001 a 0003.
