# Formatos suportados

| Formato | Módulo | Identificação | O que é extraído |
|---|---|---|---|
| JPEG | `metacore-format-image` | `FF D8 FF` (+ marcador válido) | Estrutura (SOF), JFIF, EXIF (IFD0, SubIFD, IFD1, GPS), XMP, IPTC, ICC, Photoshop, Huffman e demais segmentos do metadata-extractor |
| PNG | `metacore-format-image` | Assinatura de 8 bytes (+ IHDR) | IHDR, texto (`tEXt`, `zTXt`, `iTXt`), EXIF (`eXIf`), XMP, ICC (`iCCP`), `tIME`, `pHYs`, `gAMA` e demais chunks auxiliares |
| PDF | `metacore-format-pdf` | `%PDF-` no início ou nos primeiros 1024 bytes | Versão, número de páginas, criptografia, dicionário Info completo e XMP do catálogo |
| DOCX, XLSX, PPTX | `metacore-format-office` | Assinatura ZIP + content type da parte principal (ou nome dela) | Propriedades `core`, `app` e `custom`, content type, presença de macros |
| ZIP | `metacore-format-office` | `PK\3\4` ou `PK\5\6` | Número de entradas, tamanho declarado, comentário, entradas criptografadas |

As chaves normalizadas e a precedência entre fontes estão em
[keys.md](keys.md).

## Limitações conhecidas

### Gerais

- Com `MetadataAnalyzer`, a análise roda no mesmo processo da
  aplicação. Código dentro do metadata-extractor e do PDFBox não pode ser
  interrompido; `maxAnalysisTime` só vale entre etapas (ADR 0008). Para
  arquivos hostis, use `IsolatedAnalyzer` (módulo `metacore-isolation`,
  ADR 0016), que mata a análise no prazo e sobrevive à falta de memória.
- `InputStream` é lido sob demanda (ADR 0015). PNG e PDF leem o
  conteúdo inteiro; num stream, ele fica guardado até
  `spoolMemoryThreshold` em memória e o resto em arquivo temporário.
  Para streams, o tamanho (`FileInfo.size`) só é conhecido se o parser
  leu até o fim, e `maxFileSize` limita quanto é lido.
- Formatos sem parser (reconhecidos por um detector de terceiros, por
  exemplo) terminam em `UNSUPPORTED`; formatos desconhecidos, em
  `UNKNOWN_FORMAT`.
- Os módulos publicam `Automatic-Module-Name`, mas ainda não têm
  `module-info.java` (ADR 0003).

### JPEG

- Truncamento nos dados da imagem, depois do SOS, não é detectado: os
  metadados já foram lidos inteiros nesse ponto.
- MakerNotes são interpretadas só para os fabricantes que o
  metadata-extractor conhece; os campos saem sem chave normalizada.

### PNG

- As palavras-chave de texto (Title, Author, Comment...) saem como
  campos brutos, sem chave normalizada.
- Chunks auxiliares acima de 16 MiB e texto compactado acima de
  `maxDecompressedTextBytes` são descartados com aviso `LIMIT_REACHED`.

### PDF

- Arquivos truncados no meio costumam terminar em `FAILED` (com aviso
  `TRUNCATED`): sem a tabela de referências ou o catálogo, não há de
  onde ler o Info.
- Documentos com senha de usuário não são lidos (`PARTIAL`, aviso
  `ENCRYPTED`); nenhuma senha é testada.
- Metadados de páginas, anexos, formulários e assinaturas digitais não
  são extraídos.

### Office e ZIP

- O conteúdo dos documentos (texto, células, slides) não é lido, só as
  propriedades.
- Documentos protegidos por senha e os formatos binários antigos
  (`.doc`, `.xls`, `.ppt`) são contêineres OLE2 e ainda não são
  reconhecidos.
- ZIP64 não é suportado: a análise termina em `FAILED`.
- Partes acima de `maxDecompressedTextBytes` são puladas com aviso
  `LIMIT_REACHED`.
- Quando `[Content_Types].xml` não está no início do arquivo, como nos
  documentos grandes do LibreOffice, a detecção usa o nome da parte
  principal e tem confiança `HIGH` em vez de `EXACT`.
- JAR, EPUB e ODF saem como `zip`.

### CLI

- O executável tem cerca de 67 MB e leva perto de 0,8 s por execução,
  quase tudo de inicialização da JVM.
