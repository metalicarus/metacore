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
| [0004](0004-deteccao-e-resolucao.md) | Detecção e resolução de conflitos | Aceito |
| [0005](0005-valores-tipados-e-precedencia.md) | Valores tipados, chaves com vários valores e precedência | Aceito |
| [0006](0006-fronteira-excecao-resultado.md) | Fronteira entre exceção e resultado | Aceito |
| [0007](0007-inputsource-e-spool.md) | InputSource e spool de streams | Substituído por 0015 |
| [0008](0008-cancelamento-cooperativo.md) | Cancelamento cooperativo | Aceito |
| [0009](0009-modulo-testkit.md) | Módulo testkit | Aceito |
| [0010](0010-parser-jpeg.md) | Parser JPEG | Aceito |
| [0011](0011-cli-e-contrato-json.md) | CLI e contrato JSON | Aceito |
| [0012](0012-parsers-png-e-pdf.md) | Parsers PNG e PDF | Aceito |
| [0013](0013-release-0-1.md) | Release 0.1: publicação, distribuição e calibração | Aceito |
| [0014](0014-relacao-com-o-tika.md) | Relação com o Apache Tika (resultado da Fase 0) | Aceito |
| [0015](0015-stream-sob-demanda.md) | InputSource de stream sob demanda | Aceito |

## Modelo

```markdown
# NNNN. Título

- Estado: Proposto | Aceito | Substituído por NNNN
- Data: AAAA-MM-DD

## Contexto

## Decisão

## Consequências
```
