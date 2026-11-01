# 0007. InputSource e spool de streams

- Estado: Aceito
- Data: 2026-11-01

## Contexto

Alguns parsers leem em sequência (metadata-extractor); outros precisam
de acesso aleatório (o PDFBox começa pelo trailer, no fim do arquivo).
A API aceita tanto `Path` quanto `InputStream`.

## Decisão

- `InputSource` expõe `fileName()`, `size()`, `openStream()` e
  `openChannel()` (`SeekableByteChannel` somente leitura). Cada abertura
  começa do início, e quem abre fecha.
- `Path`: lido sob demanda, sem cópia.
- `InputStream`: até `spoolMemoryThreshold` fica em memória; acima
  disso, é copiado para um arquivo temporário (`metacore-*.spool`,
  criado com as permissões padrão do sistema), apagado ao fim da
  análise. A cópia para em `maxFileSize` com
  `InputLimitExceededException`.
- O MetaCore nunca fecha o `InputStream` do chamador.
- `InputSource.of(Path)` e `InputSource.of(byte[], String)` permitem
  montar fontes diretamente.

## Consequências

- O tamanho da entrada é sempre conhecido antes da extração.
- Analisar um stream grande gasta disco temporário do tamanho do
  conteúdo.
