# Formatos suportados

| Formato | Módulo | Identificação | O que é extraído |
|---|---|---|---|
| JPEG | `metacore-format-image` | `FF D8 FF` (+ marcador válido) | Estrutura (SOF), JFIF, EXIF (IFD0, SubIFD, IFD1, GPS), XMP, IPTC, ICC, Photoshop, Huffman e demais segmentos do metadata-extractor |
| PNG | `metacore-format-image` | Assinatura de 8 bytes (+ IHDR) | IHDR, texto (`tEXt`, `zTXt`, `iTXt`), EXIF (`eXIf`), XMP, ICC (`iCCP`), `tIME`, `pHYs`, `gAMA` e demais chunks auxiliares |
| PDF | `metacore-format-pdf` | `%PDF-` no início ou nos primeiros 1024 bytes | Versão, número de páginas, criptografia, dicionário Info completo e XMP do catálogo |

As chaves normalizadas e a precedência entre fontes estão em
[keys.md](keys.md).

## Limitações conhecidas

### Gerais

- A análise roda no mesmo processo da aplicação. Código dentro do
  metadata-extractor e do PDFBox não pode ser interrompido;
  `maxAnalysisTime` só vale entre etapas (ADR 0008). Para arquivos
  hostis em larga escala, isole a análise em outro processo.
- `InputStream` é copiado por inteiro antes da análise (até
  `spoolMemoryThreshold` em memória, o resto em arquivo temporário),
  mesmo quando o parser só precisa do começo. Com arquivos grandes,
  prefira passar o `Path`. A correção está prevista para antes do
  0.1.0 (ADR 0014).
- Formatos sem parser (por exemplo ZIP, se um detector de terceiros o
  reconhecer) terminam em `UNSUPPORTED`; formatos desconhecidos, em
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

### CLI

- Diretórios não são percorridos (sem `--recursive` por enquanto).
- O executável tem cerca de 67 MB e leva perto de 0,8 s por execução,
  quase tudo de inicialização da JVM.
