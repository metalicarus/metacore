# 0005. Valores tipados, chaves com vários valores e precedência

- Estado: Aceito
- Data: 2026-11-01

## Contexto

O diferencial pretendido do MetaCore (blueprint, seção 1.4) é devolver
valores tipados e com origem. Uma mesma chave pode vir de várias fontes
no mesmo arquivo (estrutura, EXIF, XMP), às vezes com valores
diferentes.

## Decisão

- `MetadataValue` é uma sealed interface com `TextValue`,
  `IntegerValue` (`long`), `DecimalValue` (`BigDecimal`),
  `RationalValue` (numerador e denominador, sem simplificar),
  `BooleanValue`, `LocalDateTimeValue`, `OffsetDateTimeValue`,
  `BinaryValue` e `ListValue`.
- `BinaryValue` sempre informa o tamanho; o conteúdo só é guardado até
  `maxBinaryValueBytes`.
- O parser emite `MetadataEntry` com uma `precedence` (menor vence). O
  core acrescenta o `source` (id do parser) e marca `primary` em
  exatamente um valor por chave: o de menor precedência, e no empate o
  primeiro emitido. Campos sem chave normalizada nunca são primários.
- Se valores da mesma chave divergem, o core gera um aviso
  `METADATA_CONFLICT` por chave. A mesma data e hora local com e sem fuso
  não conta como divergência.
- `MetadataResult.find` devolve o valor principal, `findAll` todos os
  valores da chave e `findRaw` busca por namespace e nome original.
- Chaves seguem `[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*`; as conhecidas
  ficam em `MetadataKeys`, com versão `MetadataKeys.VERSION`.

## Consequências

- A tabela de precedência de cada chave vive no parser do formato, que
  é quem conhece as fontes; o core só garante a regra.
- `switch` com pattern matching sobre `MetadataValue` é exaustivo; um
  tipo novo é uma mudança incompatível da API.
