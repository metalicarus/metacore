# 0015. InputSource de stream sob demanda

- Estado: Aceito
- Data: 2026-11-03
- Substitui: [0007](0007-inputsource-e-spool.md)

## Contexto

A Fase 0 (ADR 0014, `docs/fase0-tika.md`) mostrou que o spool do ADR
0007 copiava o `InputStream` inteiro antes da análise. Com um JPEG de
4 MB, a análise levava 2,9 ms e alocava 13,8 MB, contra 170 µs do Tika,
embora o parser só precise dos primeiros quilobytes. O problema era
estrutural: o contrato exigia `InputSource.size()` conhecido de
antemão, e isso obrigava a ler tudo.

## Decisão

- `analyze(InputStream, String)` usa uma fonte que lê **sob demanda**:
  só os bytes que algum leitor pede saem do stream e ficam guardados,
  em blocos de 16 KiB na memória até `spoolMemoryThreshold` e num
  arquivo temporário acima disso.
- Cada `openStream()` começa do início, repetindo o que já foi guardado
  e continuando do stream original. Bytes pulados com `skip` também são
  guardados, porque outro leitor pode precisar deles.
- `openChannel()` precisa do conteúdo inteiro e lê o resto do stream
  (é o caso do PDF).
- **`InputSource.size()` e `FileInfo.size()` passam a ser
  `OptionalLong`.** Arquivos e arrays sempre informam o tamanho; um
  stream só informa quando foi lido até o fim. No JSON da CLI, `size`
  pode ser `null` (a CLI lê arquivos, então isso não acontece nela).
- **`maxFileSize`, para streams, limita quanto é lido**, e não o tamanho
  total. Passar do limite durante a leitura lança
  `InputLimitExceededException`, como antes. Um stream enorme cujo
  parser só lê o cabeçalho não é recusado, porque nunca é lido.
- O stream do chamador continua sem ser fechado.

## Consequências

| Entrada como stream | Antes | Depois | Tika 4.1 |
|---|---|---|---|
| JPEG 4000x3000 (~4 MB) | 2,9 ms, 13,8 MB | 18 µs, 58 KB | 202 µs, 551 KB |
| PNG 2000x1500 (~9 MB) | 36 ms, 16,1 MB | 2,6 ms, 8,1 MB | 22 ms, 563 KB |
| PDF com 500 páginas | 1,3 ms, 1,0 MB | 1,5 ms, 1,0 MB | 3,9 ms, 4,3 MB |

- O PNG percorre o arquivo inteiro (texto pode vir depois dos dados da
  imagem); como stream, os bytes ficam guardados até o limiar de
  memória. Servidores com muitos uploads simultâneos podem reduzir
  `spoolMemoryThreshold`.
- Mudança incompatível na API (`long` para `OptionalLong`), feita antes
  da primeira publicação.
- `Spooler` foi removido; a implementação nova é
  `internal.io.StreamInputSource`.
