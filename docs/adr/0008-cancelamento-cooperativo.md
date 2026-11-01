# 0008. Cancelamento cooperativo

- Estado: Aceito
- Data: 2026-11-01

## Contexto

Java não permite interromper com segurança uma thread presa dentro de
uma biblioteca de terceiros. Mesmo assim, análises longas precisam de
um limite.

## Decisão

- `AnalysisLimits.maxAnalysisTime` (opcional, desligado por padrão)
  define um prazo contado do início da análise. Este limite não estava
  na tabela do blueprint.
- `ParseContext.checkCancelled()` lança `AnalysisCancelledException`
  quando o prazo passou ou quando a thread foi interrompida. O sinal de
  interrupção é mantido.
- Parsers do MetaCore chamam `checkCancelled()` entre etapas. O core
  converte o cancelamento em `PARTIAL` com aviso `CANCELLED`.
- Não há timeout rígido: código dentro do metadata-extractor ou do
  PDFBox não é interrompido. Isolamento em processo separado fica para
  depois do MVP.

## Consequências

- O prazo é aproximado: vale a partir da próxima chamada de
  `checkCancelled()`.
- Quem precisa de garantia rígida deve isolar a análise em outro
  processo.
