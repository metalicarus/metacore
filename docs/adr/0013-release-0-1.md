# 0013. Release 0.1: publicação, distribuição e calibração

- Estado: Aceito
- Data: 2026-11-03

## Contexto

Fase 6 do blueprint: revisar a API, calibrar limites, revisar
dependências, medir desempenho, documentar limitações e preparar a
publicação.

## Decisão

### Publicação

- **Maven Central** pelo Central Portal, com o
  `central-publishing-maven-plugin` no perfil `release`. Fontes, javadoc
  e assinatura GPG são gerados só nesse perfil. `autoPublish` fica
  desligado: o envio é validado e a publicação é confirmada no portal.
- O BOM, que não herda do POM pai, tem o próprio perfil `release`.
- `metacore-examples` e `metacore-benchmarks` não são publicados.
- **Executáveis da CLI** são gerados pelo workflow `release` a cada tag
  `v*`, em Linux, macOS e Windows, e anexados a um release em rascunho
  no GitHub. No Windows o launcher usa `--win-console`; no macOS o
  `Info.plist` recebe `1.0.0`, porque o jpackage recusa versões que
  começam com zero.
- Passo a passo em `docs/release.md`.

### Revisão da API

- A API pública fica como está; os pacotes `internal` saem do javadoc.
- O `package-info` vazio do pacote raiz foi removido (o javadoc o trata
  como erro).
- Os valores tipados passam a ter `toString()` com o próprio valor; o
  formato de record (`IntegerValue[value=64]`) deixava a API difícil de
  usar em logs e mensagens.

### Calibração

- `maxDetectionBytes` de 64 KiB para 8 KiB, com base nos benchmarks
  (`docs/performance.md`).
- O scanner de PNG pula os dados da imagem com `skip`, sem buffers
  novos por chunk.

### Dependências

- Atualizadas para as versões estáveis mais recentes (novembro de 2026),
  com duas exceções fixadas de propósito:
  - Surefire na linha 3.5: a 3.6.0 impede o Jazzer de se anexar à JVM do
    teste no modo de fuzzing.
  - google-java-format 1.36.1: a 1.37.0 falha dentro do Spotless
    3.10.4.
- Jazzer 0.30, que instrumenta classes do JDK 25. Com o corpus inicial
  gerado das fixtures, a cobertura do PNG em 10 s passou de 357 para 705
  arestas. Sessões longas: JPEG 10 min (4,4 milhões de execuções) e PNG
  4 min (2,6 milhões), sem falhas; a sessão longa do PDF fica para antes
  da publicação (checklist em `docs/release.md`).
- Licenças e vulnerabilidades conhecidas verificadas
  (`docs/dependencies.md`); `NOTICE` com os avisos das dependências
  Apache.

## Consequências

- O release depende de passos manuais (namespace no Central, token, chave
  GPG), descritos em `docs/release.md`.
- O workflow de release só é exercitado na primeira tag.
- A Fase 0 (comparação com o Apache Tika) segue pendente; o resultado
  pode mudar a direção do projeto depois do 0.1.
