# 0006. Fronteira entre exceção e resultado

- Estado: Aceito
- Data: 2026-11-01

## Contexto

Arquivos podem ser parcialmente legíveis. Quem chama precisa distinguir
"não deu para começar" de "deu para extrair uma parte".

## Decisão

| Situação | Comportamento |
|---|---|
| Arquivo inexistente, diretório ou sem permissão de leitura | `InvalidInputException` |
| Entrada acima de `maxFileSize` | `InputLimitExceededException` |
| Falha de I/O ao ler o início do conteúdo para detecção | `InvalidInputException` |
| Nenhum detector reconhece | `UNKNOWN_FORMAT` |
| Sem parser para o formato nem para seus supertipos | `UNSUPPORTED` + `NO_PARSER` |
| Parser lança `IOException`, `RuntimeException` ou `StackOverflowError` | `FAILED` + `PARSER_ERROR`, mantendo o que já foi emitido |
| Parser é cancelado (`AnalysisCancelledException`) | `PARTIAL` + `CANCELLED` |
| `OutOfMemoryError` e demais `Error` | Propagam |

- `WarningCode` carrega `dataLoss`. Se o parser terminou e algum aviso
  tem `dataLoss = true`, o estado é `PARTIAL`; senão, `SUCCESS`.
- `WarningCode` é um record, e não um enum, para que módulos de
  terceiros criem códigos próprios.
- `UnsupportedFormatException`, prevista na v0.1 do blueprint, não
  existe: o estado `UNSUPPORTED` cobre o caso.

## Consequências

- Depois que a detecção começa, `analyze` nunca lança exceção por causa
  do conteúdo.
- Parsers não precisam capturar as próprias falhas; basta deixá-las
  propagar.
