# Segurança

O MetaCore processa arquivos que devem ser tratados como entradas não
confiáveis. Os princípios e limites de segurança estão na seção 9 do
[blueprint](docs/blueprint.md).

## Versões suportadas

O projeto ainda não tem release. Até a versão 1.0, só a versão mais
recente recebe correções de segurança.

## Como relatar uma vulnerabilidade

Não abra issue pública para vulnerabilidades. O canal privado de
contato será definido antes do primeiro release e publicado aqui.

## Limitações conhecidas

- A análise roda no mesmo processo da aplicação. Código das bibliotecas
  de formato (metadata-extractor, PDFBox) não pode ser interrompido;
  a proteção principal são os limites de tamanho. Para processar
  arquivos hostis em larga escala, isole a análise em um processo
  separado.
