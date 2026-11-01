# 0004. Detecção e resolução de conflitos

- Estado: Aceito
- Data: 2026-11-01

## Contexto

Vários detectores podem reconhecer o mesmo conteúdo (por exemplo, ZIP e
DOCX), e um detector não deve conseguir consumir a fonte nem depender
só da extensão.

## Decisão

- O detector recebe um `DetectionRequest`: no máximo
  `maxDetectionBytes` do início do conteúdo, somente leitura, e o nome
  declarado. Ele não tem acesso ao restante da fonte.
- O detector devolve um `DetectionCandidate` (formato, confiança,
  evidências). O core monta o `DetectionResult` e preenche
  `detectorId`.
- `Confidence` é um enum ordenado (`LOW < MEDIUM < HIGH < EXACT`), e não
  um número.
- O `DetectionResolver` aplica, em ordem:
  1. evidência interna (`MAGIC`, `STRUCTURE`) descarta candidatos só
     com evidência externa;
  2. entre formatos da mesma linhagem, o mais específico vence;
  3. maior confiança vence;
  4. maior `priority()` do detector vence;
  5. empate restante: menor `detectorId` vence; se os empatados apontam
     formatos diferentes, aviso `AMBIGUOUS_DETECTION`.
- Se a extensão do nome pertence ao formato vencedor, `EXTENSION` entra
  nas evidências; se não pertence, aviso `EXTENSION_MISMATCH`.
- Detector que lança exceção ou aponta formato inexistente é ignorado,
  com aviso `DETECTOR_ERROR` (código novo, não previsto no blueprint).
- `MetadataAnalyzer.identify(...)` executa só a detecção.

## Consequências

- Detectores ficam simples e sem estado, e não conseguem travar lendo
  o arquivo inteiro.
- Formatos que só podem ser reconhecidos lendo além do início do
  arquivo precisam de `maxDetectionBytes` maior ou de detecção dentro
  do parser do supertipo.
