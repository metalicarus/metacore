# Chaves normalizadas

Vocabulário versão **1** (`MetadataKeys.VERSION`, publicado como
`keysVersion` no JSON).

## Convenções

- Segmentos em minúsculas separados por ponto; cada segmento começa por
  letra e contém letras, dígitos ou sublinhado
  (`[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*`).
- Toda chave tem um tipo fixo. Um valor que não pode ser convertido
  para esse tipo não recebe a chave: fica como campo bruto, com aviso
  `VALUE_CONVERSION_FAILED`.
- A mesma chave pode ter vários valores, de fontes diferentes. Exatamente
  um é o principal (`primary`), escolhido pela tabela de precedência
  (menor número vence; no empate, o primeiro emitido). Valores
  divergentes geram `METADATA_CONFLICT` (ADR 0005).
- Todo campo extraído aparece no resultado, com ou sem chave.
- Remover uma chave ou mudar seu tipo exige nova versão do vocabulário.

## Vocabulário

| Chave | Tipo | Descrição |
|---|---|---|
| `image.width` | `INTEGER` | Largura em pixels |
| `image.height` | `INTEGER` | Altura em pixels |
| `image.bit_depth` | `INTEGER` | Bits por amostra |
| `image.color_type` | `TEXT` | Tipo de cor (PNG) |
| `image.orientation` | `INTEGER` | Orientação EXIF, de 1 a 8 |
| `image.thumbnail.width` | `INTEGER` | Largura da miniatura |
| `image.thumbnail.height` | `INTEGER` | Altura da miniatura |
| `image.camera.make` | `TEXT` | Fabricante da câmera |
| `image.camera.model` | `TEXT` | Modelo da câmera |
| `image.camera.exposure_time` | `RATIONAL` | Tempo de exposição em segundos |
| `image.datetime_original` | `LOCAL_DATE_TIME` ou `OFFSET_DATE_TIME` | Momento da captura; com fuso só quando a fonte informa |
| `location.latitude` | `DECIMAL` | Graus decimais, sul negativo, 7 casas |
| `location.longitude` | `DECIMAL` | Graus decimais, oeste negativo, 7 casas |
| `document.title` | `TEXT` | Título |
| `document.author` | `TEXT` | Autor |
| `document.subject` | `TEXT` | Assunto |
| `document.keywords` | `LIST` | Palavras-chave |
| `document.creator` | `TEXT` | Aplicativo que criou o documento |
| `document.producer` | `TEXT` | Aplicativo que gerou o arquivo |
| `document.created` | `LOCAL_DATE_TIME` ou `OFFSET_DATE_TIME` | Criação |
| `document.modified` | `LOCAL_DATE_TIME` ou `OFFSET_DATE_TIME` | Última modificação |
| `document.page_count` | `INTEGER` | Número de páginas |
| `document.format_version` | `TEXT` | Versão do formato, por exemplo `1.7` |

As chaves `file.mime_type` e `file.extension` estão reservadas; essas
informações já saem em `detection` e `file` no resultado.

## Namespaces

| Namespace | Origem |
|---|---|
| `JPEG` | Estrutura do JPEG (SOF) |
| `JFIF` | Segmento APP0 JFIF |
| `EXIF:IFD0` | EXIF, imagem principal |
| `EXIF:SubIFD` | EXIF, dados da captura |
| `EXIF:IFD1` | EXIF, miniatura |
| `EXIF:GPS` | EXIF, localização |
| `EXIF:Interop` | EXIF, interoperabilidade |
| `PNG:IHDR`, `PNG:tEXt`, `PNG:zTXt`, `PNG:iTXt`, `PNG:tIME`... | Chunks do PNG |
| `PDF` | Estrutura do PDF: versão, páginas, criptografia |
| `PDF:Info` | Dicionário Info do PDF |
| `XMP` | Propriedades XMP; `rawName` é o caminho, por exemplo `tiff:Make` ou `dc:title[1]` |
| outros | Nome do diretório no metadata-extractor (`ICC Profile`, `IPTC`, `Huffman`...) |

## Precedência por formato

### JPEG

| Chave | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| `image.width` / `image.height` | `JPEG` Image Width/Height | `EXIF:SubIFD` Exif Image Width/Height | `EXIF:IFD0` Image Width/Height | `XMP` exif:PixelXDimension/PixelYDimension |
| `image.bit_depth` | `JPEG` Data Precision | | | |
| `image.orientation` | `EXIF:IFD0` Orientation | `XMP` tiff:Orientation | | |
| `image.camera.make` / `model` | `EXIF:IFD0` Make/Model | `XMP` tiff:Make/Model | | |
| `image.camera.exposure_time` | `EXIF:SubIFD` Exposure Time | `XMP` exif:ExposureTime | | |
| `image.datetime_original` | `EXIF:SubIFD` Date/Time Original (+ Time Zone Original) | `XMP` exif:DateTimeOriginal | `XMP` xmp:CreateDate | |
| `image.thumbnail.width` / `height` | `EXIF:IFD1` Image Width/Height | | | |
| `location.latitude` / `longitude` | `EXIF:GPS`, a partir de GPS Latitude/Longitude e Ref | | | |

### PNG

| Chave | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| `image.width` / `image.height` | `PNG:IHDR` Image Width/Height | `EXIF:SubIFD` (chunk `eXIf`) | `EXIF:IFD0` | `XMP` exif:PixelXDimension/PixelYDimension |
| `image.bit_depth` | `PNG:IHDR` Bits Per Sample | | | |
| `image.color_type` | `PNG:IHDR` Color Type (descrição, por exemplo `True Color`) | | | |
| demais chaves de imagem | como no JPEG, vindas do `eXIf` e do XMP (`iTXt` `XML:com.adobe.xmp`) | | | |

Os chunks de texto (`tEXt`, `zTXt`, `iTXt`) viram um campo bruto por
palavra-chave, no namespace do chunk (`PNG:tEXt`, por exemplo), sem
chave normalizada.

O mapeamento de JPEG e PNG está em
`metacore-format-image/.../internal/KeyMappings.java`; estas tabelas e o
código precisam mudar juntos.

### PDF

Até a versão 1.7 o dicionário Info vale mais que o XMP; no PDF 2.0, que
deprecia o Info, a ordem se inverte.

| Chave | PDF ≤ 1.7: 1 | PDF ≤ 1.7: 2 |
|---|---|---|
| `document.title` | `PDF:Info` Title | `XMP` dc:title[1] |
| `document.author` | `PDF:Info` Author | `XMP` dc:creator[1] |
| `document.subject` | `PDF:Info` Subject | `XMP` dc:description[1] |
| `document.keywords` | `PDF:Info` Keywords (separadas por `,` ou `;`) | `XMP` pdf:Keywords |
| `document.creator` | `PDF:Info` Creator | `XMP` xmp:CreatorTool |
| `document.producer` | `PDF:Info` Producer | `XMP` pdf:Producer |
| `document.created` | `PDF:Info` CreationDate | `XMP` xmp:CreateDate |
| `document.modified` | `PDF:Info` ModDate | `XMP` xmp:ModifyDate |
| `document.page_count` | `PDF` Pages/Count | |
| `document.format_version` | `PDF` Version | |

O mapeamento está em `metacore-format-pdf/.../PdfParser.java`.
