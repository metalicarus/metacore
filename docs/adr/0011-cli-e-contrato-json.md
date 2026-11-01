# 0011. CLI e contrato JSON

- Estado: Aceito
- Data: 2026-11-01

## Contexto

Fase 4 do blueprint: comandos `identify` e `inspect`, saída legível por
padrão, JSON estável para automação, códigos de saída definidos e
distribuição sem exigir Java instalado.

## Decisão

### Comandos e opções

- `metacore identify FILE...` e `metacore inspect FILE...`, com
  picocli.
- Opções comuns: `-o/--output text|json`, `-f/--file OUT`,
  `--max-file-size SIZE` (`512`, `64K`, `200M`, `1G`, base 1024),
  `--show-path`, `--strict`.
- Só no `inspect`: `-v/--verbose` (todos os campos, com origem),
  `-q/--quiet` (uma linha por arquivo) e `--include-binary`.
- A saída em texto é para pessoas e não é contrato. Por padrão mostra só
  os valores principais das chaves normalizadas e quantos campos ficaram
  ocultos.
- Mensagens, rótulos e ajuda da CLI ficam em inglês, como as mensagens
  de aviso do core.
- Análise recursiva de diretórios ficou para depois; um diretório é
  rejeitado como entrada inválida.

### Contrato JSON (`schemaVersion` 1)

- Uma linha por entrada (NDJSON), inclusive para uma entrada só. Sem
  formatação: para ler, use `jq`.
- `inspect`: `schemaVersion`, `keysVersion`, `file`, `detection`,
  `status`, `metadata`, `warnings`, nessa ordem.
- `identify`: `schemaVersion`, `file`, `detection`, `warnings`.
  `detection` é `null` quando o formato é desconhecido.
- `file`: `name`, `size`, `extension` e, só com `--show-path`, `path`
  (como foi informado).
- `detection`: `format` (`id`, `mimeType`, `displayName`, `parent`),
  `confidence`, `evidence`, `detectorId`.
- Cada metadado: `key` (ou `null`), `value`, `valueType`, `rawName`,
  `namespace`, `source`, `primary`.
- Valores: texto como string; inteiro e decimal como número (decimal sem
  notação exponencial, com a escala original); fração como
  `{numerator, denominator}`; datas como ISO-8601 (com fuso só quando
  conhecido); binário como `{size}` e, com `--include-binary`,
  `base64`; lista como array de `{value, valueType}`.
- Avisos: `code`, `message`, `location` (ou `null`).
- Entrada que não pôde ser lida vira
  `{schemaVersion, input, error: {type, message}}`, com `type`
  `INVALID_INPUT` ou `INPUT_LIMIT_EXCEEDED`, para que toda entrada tenha
  sua linha.
- A duração da análise não sai no JSON (determinismo).
- Um golden file (`metacore-cli/src/test/resources/golden/cli`) protege o
  contrato.

### Códigos de saída

| Código | Significado |
|---|---|
| 0 | Todas as entradas foram analisadas (inclui `PARTIAL`, `UNSUPPORTED` e `UNKNOWN_FORMAT`) |
| 1 | Alguma entrada terminou em `FAILED` ou não pôde ser lida |
| 2 | Uso inválido (padrão do picocli) |
| 3 | Com `--strict`, alguma entrada não terminou em `SUCCESS` |

Com várias entradas, vale o pior: 1, depois 3, depois 0.

### Saídas e logs

- Resultado em stdout (ou no arquivo de `--file`); erros de entrada em
  texto e logs em stderr. Logback com nível `WARN`, sempre em stderr.

### Distribuição

- Perfil Maven `dist`: `mvn -Pdist package -pl metacore-cli -am`
  gera `metacore-cli/target/dist/metacore`, uma imagem do jpackage com
  runtime Java 25 reduzido pelo jlink.
- Módulos do runtime calculados com `jdeps`: `java.base`,
  `java.desktop`, `java.logging`, `java.naming`, `java.sql`, `java.xml`
  e `jdk.charsets`. Com `--compress=zip-6`, a imagem tem cerca de 67 MB
  e cada execução leva perto de 0,8 s.
- Testado em Linux. Instaladores (`deb`, `rpm`, `msi`, `dmg`) e
  GraalVM native-image ficam para a Fase 6.

## Consequências

- Mudanças incompatíveis no JSON exigem `schemaVersion` novo; campos
  novos podem ser acrescentados no fim de cada objeto.
- O `java.desktop` (exigido pelo PDFBox e por partes do
  metadata-extractor) responde por boa parte do tamanho da imagem.
