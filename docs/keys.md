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
| `XMP` | Propriedades XMP; `rawName` é o caminho, por exemplo `tiff:Make` |
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

O mapeamento está em
`metacore-format-image/.../internal/KeyMappings.java`; esta tabela e o
código precisam mudar juntos.
