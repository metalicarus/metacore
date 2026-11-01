# 0009. Módulo testkit

- Estado: Aceito
- Data: 2026-11-01

## Contexto

O blueprint (seção 10.4) prevê uma suíte de contrato que todo parser
precisa passar, reutilizável por módulos de terceiros. Um `test-jar`
seria difícil de publicar e de consumir.

## Decisão

- Novo módulo publicado `metacore-testkit`, com a classe abstrata
  `MetadataParserContractTest`. O módulo de formato estende a classe nos
  próprios testes e informa o provedor e os arquivos de teste.
- A suíte verifica: provedor consistente, ids no padrão, extração com
  `SUCCESS` ou `PARTIAL`, origem preenchida, um valor principal por
  chave, fonte inalterada (SHA-256), determinismo e término em entradas
  truncadas.
- O testkit depende de `junit-jupiter-api` e `assertj-core` em escopo
  `compile`; quem usa declara o testkit com escopo `test`.

## Consequências

- Os módulos de imagem e PDF passam a ter uma barreira mínima comum.
- O testkit entra no BOM e segue a versão dos demais módulos.
