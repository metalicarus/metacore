# 0012. Parsers PNG e PDF

- Estado: Aceito
- Data: 2026-11-01

## Contexto

Fase 5 do blueprint: completar os formatos do MVP com PNG
(metadata-extractor) e PDF (PDFBox), mantendo as garantias do JPEG
(ADR 0010): resultado parcial em arquivos truncados, limites de
recursos, proteção contra XXE, fixtures geradas por código, golden files
e fuzzing.

## Decisão

### PNG

- **`PngChunkScanner`** percorre os chunks antes do metadata-extractor:
  - descarta os dados da imagem (`IDAT`) sem guardá-los;
  - descarta chunks auxiliares acima de 16 MiB (`LIMIT_REACHED`);
  - mede o texto compactado (`zTXt`, `iTXt` compactado, `iCCP`) com
    um `Inflater` limitado e descarta o que passaria de
    `maxDecompressedTextBytes` (`LIMIT_REACHED`). Sem isso, o
    metadata-extractor descompactaria sem limite (zip bomb);
  - em arquivo truncado, entrega os chunks completos com um `IEND`
    sintético e aviso `TRUNCATED`. Sem nenhum chunk completo, o parser
    nem chama o metadata-extractor.
- Texto (`tEXt`, `zTXt`, `iTXt`) vira um campo bruto por palavra-chave,
  sem chave normalizada: as palavras-chave do PNG não correspondem ao
  vocabulário de imagem.
- `eXIf` e XMP (`iTXt` `XML:com.adobe.xmp`) reaproveitam o
  `DrewMetadataEmitter` e os mapeamentos do JPEG.

### PDF

- **Acesso aleatório** pelo `ChannelRandomAccessRead`, que adapta o
  canal do `InputSource` ao `RandomAccessRead` do PDFBox com uma janela
  de 64 KiB. Nada é carregado inteiro na memória.
- Lidos: versão, criptografia, número de páginas (árvore de páginas), o
  dicionário Info inteiro (chaves próprias como campos brutos) e o XMP
  do catálogo. O conteúdo das páginas nunca é lido.
- **Datas** do Info (`D:20260714163205-03'00'`) e do XMP (ISO 8601) são
  convertidas pelo `PdfDates`; sem fuso declarado, ficam sem fuso.
- **Precedência**: Info antes do XMP até o PDF 1.7; XMP antes do Info
  no PDF 2.0 (`docs/keys.md`).
- **XMP** é lido com o XMPCore da Adobe (dependência direta), que
  recusa DOCTYPE; o pacote é lido até `maxDecompressedTextBytes`.
- **Criptografia**: o documento é aberto com senha vazia. Se exigir
  senha de usuário, o resultado é `PARTIAL` com aviso `ENCRYPTED` e o
  campo `PDF/Encrypted`. Com só senha de proprietário, tudo é lido.
- **Truncamento**: sem `%%EOF` nos últimos 1024 bytes, aviso
  `TRUNCATED`. O PDFBox tenta reconstruir o arquivo; quando não
  consegue, o resultado é `FAILED`. Diferente de JPEG e PNG, o PDF não
  tem leitura parcial: sem a tabela de referências ou o catálogo, não há
  de onde ler o Info.
- A CLI silencia os logs do PDFBox, que registra cada reparo de arquivo
  malformado.

### Testes

- Fixtures geradas por código (`PngFixtures`, `PdfFixtures`), golden
  files, suíte de contrato, testes de XXE, testes de zip bomb, testes de
  robustez com mutações reproduzíveis e alvos do Jazzer.
- Sessões de 60 s: PNG com cerca de 1,6 milhão de execuções e PDF com
  cerca de 690 mil, sem falhas. O Jazzer 0.24 não instrumenta classes do
  JDK 25 (aviso `Unsupported class file major version 69`); as classes do
  MetaCore e das bibliotecas, compiladas para versões anteriores, são
  instrumentadas normalmente.
- `ResultRenderer` e `GoldenFiles` passaram para o `metacore-testkit`,
  para servir aos dois módulos de formato.

## Consequências

- Os três formatos do MVP funcionam pela API e pela CLI.
- PDFs truncados no meio costumam terminar em `FAILED`; o aviso
  `TRUNCATED` indica a causa.
- O fuzzing do PDF parte de bytes aleatórios e raramente monta um PDF
  válido; um corpus inicial com as fixtures aumentaria a cobertura.
