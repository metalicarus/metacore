# Registros de decisão de arquitetura (ADRs)

Cada decisão de arquitetura ou de contrato público fica registrada num
arquivo próprio, numerado em ordem de criação. Um ADR aceito não é
editado: se a decisão mudar, um novo ADR substitui o anterior e os dois
apontam um para o outro.

| ADR | Título | Estado |
|---|---|---|
| [0001](0001-baseline-java.md) | Baseline do Java | Aceito |
| [0002](0002-coordenadas-maven.md) | Coordenadas Maven e prefixo de pacotes | Aceito |
| [0003](0003-descoberta-de-provedores.md) | Descoberta de provedores e JPMS | Aceito |

ADRs previstos (blueprint, apêndice A): relação com o Apache Tika,
modelo de valores tipados, chaves com vários valores e precedência,
fronteira entre exceção e resultado, `InputSource` e spool, contrato
JSON, cancelamento cooperativo.

## Modelo

```markdown
# NNNN. Título

- Estado: Proposto | Aceito | Substituído por NNNN
- Data: AAAA-MM-DD

## Contexto

## Decisão

## Consequências
```
