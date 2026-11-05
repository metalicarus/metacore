# 0018. Office Open XML com leitor próprio, sem Apache POI

- Estado: Aceito
- Data: 2026-11-04

## Contexto

O blueprint previa DOCX e os demais formatos OOXML com o Apache POI, e a
hierarquia `zip` → `docx` já estava desenhada (ADR 0004).

Os metadados de um pacote OOXML ficam em partes XML pequenas dentro do
ZIP:
- `docProps/core.xml`: título, autor, datas;
- `docProps/app.xml`: páginas, aplicativo;
- `docProps/custom.xml`: propriedades definidas pelo usuário.

O POI resolve isso, mas traz cerca de 15 a 20 MB de dependências
(`poi`, `poi-ooxml`, `poi-ooxml-lite`, `xmlbeans`, `commons-compress`,
`commons-io`, `commons-math3`, `log4j-api`...). Isso é mais que todo o
resto do MetaCore junto, para ler três arquivos XML.

## Decisão

- **Novo módulo `metacore-format-office`**, só com o core como
  dependência. ZIP e XML vêm do JDK (`java.util.zip.Inflater` e StAX).
- **Formatos declarados:**
  - `zip`;
  - `docx`, que também cobre `docm`, `dotx` e `dotm`;
  - `xlsx`, que também cobre `xlsm`, `xltx` e `xltm`;
  - `pptx`, que também cobre `pptm`, `potx`, `potm`, `ppsx` e `ppsm`.

  Os três formatos OOXML têm `parent = zip`. As variantes com macros e
  os modelos não viram formatos próprios: o campo `OOXML` `Macros` diz
  se há macros, e `Content Type` traz o tipo exato da parte principal.
- **Detecção** (detector `zip-ooxml`), só com o início do arquivo, como
  manda o ADR 0004:
  - a assinatura `PK\3\4` identifica um ZIP; o detector percorre os
    cabeçalhos locais que couberem no início;
  - se `[Content_Types].xml` estiver ali (o Microsoft Office o grava
    primeiro), o content type da parte principal decide o formato, com
    confiança `EXACT`;
  - o LibreOffice grava `[Content_Types].xml` por último. Para ele, a
    confiança é `HIGH` quando aparece a parte principal
    (`word/document.xml`, `xl/workbook.xml`, `ppt/presentation.xml`) ou
    as relações dela;
  - nomes genéricos como `xl/` só contam junto de uma parte comum a todo
    pacote OPC (`_rels/.rels`, `docProps/`). Assim, uma pasta chamada
    `word` dentro de um ZIP qualquer não vira DOCX.
- **Leitura.**
  - O parser lê o diretório central sobre o canal da fonte, e cada parte
    é descompactada até `maxDecompressedTextBytes` (1 MiB). Acima disso,
    a parte é pulada com `LIMIT_REACHED`, o que neutraliza zip bombs.
  - Sem o diretório central, como num download interrompido, há aviso
    `TRUNCATED` e as partes são procuradas pelos cabeçalhos locais,
    inclusive com descritores de dados.
  - Só STORED e DEFLATED são lidos. ZIP64 é recusado, e a análise
    termina em `FAILED`; documentos Office acima de 4 GB ou com mais de
    65.535 partes são raros.
- **XML.** O parser do JDK roda sem DTD e sem entidades externas, e uma
  parte com `DOCTYPE` é recusada inteira, com `CORRUPTED_SEGMENT`. Partes
  OPC nunca têm `DOCTYPE` (ISO/IEC 29500-2), e é por ele que entram XXE e
  expansão de entidades.
- **Campos:**
  - `ZIP`: `Entries`, `Uncompressed Size` (soma dos tamanhos
    declarados), `Comment` e `Encrypted Entries`;
  - `OOXML`: `Content Type` e `Macros`;
  - `OOXML:Core`, com `rawName` no prefixo canônico (`dc:title`,
    `dcterms:created`, `cp:keywords`...);
  - `OOXML:App`: valores simples viram inteiro, booleano ou texto, e
    vetores (como os nomes das planilhas em `TitlesOfParts`) viram
    lista;
  - `OOXML:Custom`: o tipo VT (`vt:i4`, `vt:r8`, `vt:bool`,
    `vt:filetime`...) decide o tipo do valor.
- **Chaves.** O mapeamento está em `docs/keys.md`. Entra no vocabulário
  `document.last_modified_by` (`cp:lastModifiedBy`); a versão continua 1,
  porque acrescentar chaves não quebra quem já as usa.
- **ZIP genérico.** JAR, EPUB, ODF e qualquer outro ZIP passam a sair
  como `zip`, com status `SUCCESS` e os campos `ZIP`. Antes saíam como
  `UNKNOWN_FORMAT`.

## Consequências

- O suporte a Office não acrescenta dependências: o módulo tem cerca de
  40 KB, e o executável da CLI quase não muda de tamanho.
- O conteúdo dos documentos (texto, células, slides) não é lido. Se um
  dia for preciso, o POI volta a ser a opção natural, num módulo
  separado.
- Documentos protegidos por senha não são ZIP: o Office os grava como
  contêiner OLE2 (CFB), que nenhum detector reconhece hoje. O mesmo vale
  para os formatos binários antigos (`.doc`, `.xls`, `.ppt`).
- ODF (`.odt`, `.ods`, `.odp`) tem estrutura parecida (`meta.xml`) e
  pode entrar neste módulo depois, com o mesmo leitor de ZIP.
- Mudar a declaração de `zip` para outro módulo no futuro, como um de
  arquivos compactados, não muda o identificador `zip`.
