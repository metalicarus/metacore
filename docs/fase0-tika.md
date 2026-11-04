# Fase 0: comparação com o Apache Tika

- Data: 2026-11-03
- Decisão: [ADR 0014](adr/0014-relacao-com-o-tika.md)

O blueprint (seção 1.4) condicionou o projeto a quatro hipóteses sobre o
que o MetaCore entregaria melhor que o Apache Tika. Esta comparação
roda os dois sobre os mesmos arquivos e mede cada hipótese.

## Método

- **Versões**: MetaCore 0.1.0-SNAPSHOT e Tika 4.1.0, só com os módulos
  equivalentes (`tika-core`, `tika-parser-image-module`,
  `tika-parser-pdf-module`). Os dois usam exatamente as mesmas
  bibliotecas por baixo: metadata-extractor 2.21.0, PDFBox 3.0.8 e
  XMPCore 6.1.11. As diferenças medidas são, portanto, das camadas de
  cada projeto.
- **Entradas**: as fixtures geradas por código dos módulos de formato
  (21 casos, incluindo truncados, criptografados, zip bomb e XXE) e as
  entradas grandes do benchmark (`docs/performance.md`).
- **Uso**: cada biblioteca do jeito documentado. MetaCore com
  `MetadataAnalyzer.createDefault()`; Tika com `AutoDetectParser`,
  `TikaInputStream` e um `DefaultHandler` que descarta o texto.
- **Medidas**: média por análise depois de 3 s de aquecimento e 5 s de
  medição, na mesma thread, com a alocação por
  `ThreadMXBean.getCurrentThreadAllocatedBytes`. Mais simples que JMH,
  mas igual para os dois lados.
- **Reproduzir**: `mvn -Pcomparison package -pl metacore-comparison -am
  -DskipTests` e
  `java -cp "metacore-comparison/target/metacore-comparison-0.1.0-SNAPSHOT.jar:metacore-comparison/target/lib/*"
  br.com.genosha.metacore.comparison.ComparisonMain <saída>`.

## Resultados por hipótese

### H1. Valores tipados: confirmada

O `Metadata` do Tika guarda texto (`String[]` por nome). Os getters
tipados (`getDate`, `getInt`) só reinterpretam esse texto para algumas
propriedades conhecidas. Nas mesmas fixtures:

| Campo | MetaCore | Tika |
|---|---|---|
| Tempo de exposição | `RATIONAL` `1/125` | `exif:ExposureTime = "0.008"` (a fração se perde) |
| Data da captura (EXIF com `OffsetTimeOriginal -03:00`) | `OFFSET_DATE_TIME` `2026-07-14T16:32:05-03:00` | `exif:DateTimeOriginal = "2026-07-14T19:32:05"`: convertida para UTC, mas sem o `Z`, ela parece hora local; o mesmo formato é usado para datas sem fuso |
| Latitude e longitude | `DECIMAL` `-23.5500000` / `-46.6333333` | `geo:lat = "-23.55"`, `geo:long = "-46.633333"` |
| Coordenada bruta | lista de frações `[46/1, 38/1, 0/1]` | `"-46° 37' 60\""` (descrição com erro de arredondamento) |
| Dimensões | `INTEGER` `64` | `"64"` em `tiff:ImageWidth`; `"64 pixels"` no campo bruto |
| Palavras-chave do PDF | `LIST` `[pdf, metadata, testes]` | `dc:subject = [pdf, metadata; testes, Metadados]`: mistura palavras-chave e assunto e só separa por vírgula |
| Data inválida no PDF | campo bruto `"yesterday"` e aviso `VALUE_CONVERSION_FAILED` | descartada sem aviso |

### H2. Origem por campo: confirmada em parte

O Tika preserva mais do que o blueprint supunha. Os campos brutos do
metadata-extractor saem com o diretório no nome
(`img:Exif IFD0:Make`, `img:Exif SubIFD:Exposure Time`), o XMP sai com
prefixo próprio (`xmp:tiff:Make`) e o Info do PDF também
(`pdf:docinfo:title`). O que falta:

- as chaves normalizadas (`tiff:Make`, `dc:title`) não dizem de qual
  fonte vieram;
- divergências entre fontes não são apontadas: no JPEG com EXIF
  `Genosha` e XMP `Other Maker`, os dois valores aparecem em chaves
  diferentes, sem aviso; o MetaCore marca o principal e gera
  `METADATA_CONFLICT`;
- os valores brutos são descrições em texto (`"1/125 sec"`,
  `"Right side, top (Rotate 90 CW)"`), não os valores originais;
- não há como perguntar "de onde veio este valor" sem conhecer as
  convenções de nome de cada parser.

### H3. Estado explícito: confirmada

| Fixture | MetaCore | Tika |
|---|---|---|
| jpeg-basic-exif | SUCCESS — 24 campos (8 normalizados) | image/jpeg — 38 chaves |
| jpeg-gps | SUCCESS — 30 campos (10 normalizados) | image/jpeg — 44 chaves |
| jpeg-xmp-only | SUCCESS — 19 campos (6 normalizados) | image/jpeg — 28 chaves |
| jpeg-conflicting-xmp | SUCCESS — 27 campos (8 normalizados); avisos: METADATA_CONFLICT | image/jpeg — 40 chaves |
| jpeg-invalid-date | SUCCESS — 16 campos (3 normalizados); avisos: VALUE_CONVERSION_FAILED | image/jpeg — 24 chaves |
| jpeg-truncated-half | PARTIAL — 24 campos (8 normalizados); avisos: TRUNCATED | **exceção** TikaException: TIKA-198: Illegal IOException from org.apache.tika.parser.image.JpegParser@106cc... (image/jpeg — 5 chaves) |
| jpeg-xxe | PARTIAL — 15 campos (3 normalizados); avisos: CORRUPTED_SEGMENT | image/jpeg — 23 chaves |
| text-named-jpg | UNKNOWN_FORMAT — 0 campos (0 normalizados) | text/plain — 5 chaves |
| png-text | SUCCESS — 11 campos (4 normalizados) | image/png — 28 chaves |
| png-exif | SUCCESS — 16 campos (9 normalizados) | image/png — 25 chaves |
| png-xmp | SUCCESS — 10 campos (6 normalizados) | image/png — 26 chaves |
| png-zip-bomb-8mb | PARTIAL — 7 campos (4 normalizados); avisos: LIMIT_REACHED | image/png — 26 chaves |
| png-truncated | PARTIAL — 9 campos (4 normalizados); avisos: TRUNCATED | **exceção** TikaException: image/png parse error (image/png — 10 chaves) |
| pdf-info | SUCCESS — 11 campos (10 normalizados) | application/pdf — 47 chaves |
| pdf-1.7-info-and-xmp | SUCCESS — 7 campos (6 normalizados); avisos: METADATA_CONFLICT | application/pdf — 42 chaves |
| pdf-2.0-info-and-xmp | SUCCESS — 7 campos (6 normalizados); avisos: METADATA_CONFLICT | application/pdf — 42 chaves |
| pdf-invalid-date | SUCCESS — 3 campos (2 normalizados); avisos: VALUE_CONVERSION_FAILED | application/pdf — 31 chaves |
| pdf-user-password | PARTIAL — 1 campos (0 normalizados); avisos: ENCRYPTED | **exceção** EncryptedDocumentException: Unable to process: document is encrypted (application/pdf — 10 chaves) |
| pdf-owner-password | SUCCESS — 4 campos (3 normalizados) | application/pdf — 33 chaves |
| pdf-truncated-half | FAILED — 0 campos (0 normalizados); avisos: TRUNCATED, PARSER_ERROR | **exceção** TikaException: TIKA-198: Illegal IOException from org.apache.tika.parser.pdf.PDFParser@5b202a3a (application/pdf — 8 chaves) |
| pdf-xxe | PARTIAL — 2 campos (2 normalizados); avisos: CORRUPTED_SEGMENT | application/pdf — 31 chaves |

Destaques:

- **Truncamento**: com metade de um JPEG, o Tika lança `TikaException`
  e devolve 5 chaves (só tipo e nome); o MetaCore devolve os 24 campos
  lidos até o corte, com `PARTIAL` e `TRUNCATED`. No PNG truncado, o
  Tika também lança exceção, depois de preencher parte dos metadados.
- **Criptografia**: o Tika lança `EncryptedDocumentException`; o
  MetaCore devolve `PARTIAL` com `ENCRYPTED`.
- **Zip bomb**: o Tika descompacta o `zTXt` de 8 MB e guarda o texto
  inteiro, duas vezes (`img:Text TextEntry` e `img:zTXt zTXtEntry`); o
  MetaCore descarta o chunk com `LIMIT_REACHED`.
- **XXE**: os dois estão protegidos (o XMPCore recusa DOCTYPE), e nenhum
  vazou o arquivo apontado pela entidade. O Tika descarta o XMP sem
  aviso; o MetaCore avisa `CORRUPTED_SEGMENT`.
- **Sem parser**: o Tika usa o `EmptyParser` em silêncio; o MetaCore
  separa `UNSUPPORTED` de `UNKNOWN_FORMAT`.

### H4. Leveza: confirmada (depois de corrigir o spool de streams)

| | MetaCore (core, image, pdf) | Tika (core, image, pdf) |
|---|---|---|
| JARs no classpath | 10 | 23 |
| Tamanho | ~4,8 MB | ~16,7 MB (inclui BouncyCastle, commonmark, jai-imageio, pdfbox-tools, picocli) |
| Primeira análise numa JVM nova (JPEG pequeno) | ~340 ms | ~470 ms |
| Primeira análise numa JVM nova (PDF de 500 páginas) | ~355 ms | ~615 ms |

| Entrada | Origem | MetaCore tempo | MetaCore alocação | Tika tempo | Tika alocação |
|---|---|---|---|---|---|
| JPEG 64x48 com EXIF e GPS | stream | 30 µs | 71 KB | 134 µs | 208 KB |
| JPEG 64x48 com EXIF e GPS | arquivo | 50 µs | 48 KB | 222 µs | 134 KB |
| JPEG 4000x3000 (~4 MB) | stream | 18 µs | 58 KB | 202 µs | 551 KB |
| JPEG 4000x3000 (~4 MB) | arquivo | 37 µs | 44 KB | 265 µs | 542 KB |
| PNG 2000x1500 (~9 MB) | stream | 2,6 ms | 8,1 MB | 22,0 ms | 563 KB |
| PNG 2000x1500 (~9 MB) | arquivo | 1,2 ms | 56 KB | 2,5 ms | 561 KB |
| PDF com 500 páginas | stream | 1,5 ms | 1,0 MB | 3,9 ms | 4,3 MB |
| PDF com 500 páginas | arquivo | 1,4 ms | 979 KB | 3,4 ms | 4,3 MB |

- O MetaCore é mais rápido em todas as entradas, de arquivo ou de
  stream: de 2 a 11 vezes.
- Aloca menos em quase todas, de 3 a 12 vezes. A exceção é o PNG grande
  como stream (8,1 MB contra 563 KB): o PNG é lido até o fim e, num
  stream, os bytes ficam guardados até `spoolMemoryThreshold` para
  poderem ser relidos.
- No PDF, parte da diferença vem de o Tika extrair também o texto das
  páginas, que o MetaCore não lê.

**Primeira medição, antes da correção.** O spool antigo copiava o
stream inteiro antes da análise, e o MetaCore perdia nas entradas
grandes como stream: JPEG de 4 MB em 2,9 ms e 13,8 MB (Tika: 174 µs e
551 KB) e PNG de 9 MB em 36 ms e 16,1 MB (Tika: 20,5 ms). A Fase 0
apontou o problema e ele foi corrigido antes da publicação (ADR 0015).

## Onde o Tika é melhor

- **Detecção**: mais de mil tipos MIME, inclusive texto (o arquivo
  `text-named-jpg` sai como `text/plain` no Tika e `UNKNOWN_FORMAT` no
  MetaCore).
- **Abrangência**: centenas de formatos, extração de texto, arquivos
  compostos e documentos embutidos.
- **PDF técnico**: permissões, fontes, atualizações incrementais,
  offsets de `%%EOF`, XFA e outros dados que o MetaCore não extrai.
- **Isolamento**: o `tika-pipes` roda a análise em outro processo, com
  timeout de verdade; o MetaCore só tem cancelamento cooperativo.
- **Maturidade**: anos de uso em produção e um corpus de regressão muito
  maior.

## Conclusão

Pelo critério do blueprint, que pede ao menos H1 e H2 confirmadas, o
MetaCore **segue como projeto independente**. H1, H3 e H4 são os
diferenciais fortes; H2 se confirma de forma mais modesta. H4 só se
confirmou por inteiro depois de corrigir o spool de streams, que a
própria comparação revelou.

O posicionamento correto não é "alternativa ao Tika", e sim:
**metadados tipados, com origem e com estado explícito, para poucos
formatos, com poucas dependências**. Para detecção ampla, extração de
texto ou formatos que o MetaCore não cobre, o Tika continua sendo a
escolha.

## Ações

1. ~~Antes de publicar o 0.1.0: corrigir o spool de streams para ler
   sob demanda.~~ Feito no ADR 0015.
2. ~~Ajustar o README com o posicionamento acima.~~ Feito.
3. Depois do 0.1: avaliar um módulo opcional que use o detector do
   `tika-core` (cerca de 1 MB) para ampliar a identificação sem trazer
   os parsers do Tika.
