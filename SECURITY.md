# Segurança

O MetaCore processa arquivos que devem ser tratados como entradas não
confiáveis. Os princípios e limites de segurança estão na seção 9 do
[blueprint](docs/blueprint.md).

## Versões suportadas

Até a versão 1.0, só a versão mais recente recebe correções de
segurança.

| Versão | Suporte |
|---|---|
| 0.1.x | sim |

## Como relatar uma vulnerabilidade

Não abra issue pública. Use o relato privado do GitHub: aba
**Security → Report a vulnerability** do repositório
<https://github.com/metalicarus/metacore>. Inclua, se possível, um
arquivo que reproduza o problema.

## Proteções

- Limites de tamanho, de campos, de texto e de descompactação, com
  valores padrão em [docs/performance.md](docs/performance.md).
- XMP (JPEG, PNG e PDF) é lido com o XMPCore da Adobe, que recusa
  DOCTYPE: entidades externas e expansão de entidades não são
  processadas. Testes provam isso nos três formatos.
- Texto compactado do PNG é medido antes de ser descompactado.
- Parsers passam por testes de robustez e fuzzing com Jazzer.

## Limitações conhecidas

- A análise roda no mesmo processo da aplicação. Código das bibliotecas
  de formato (metadata-extractor, PDFBox) não pode ser interrompido;
  a proteção principal são os limites de tamanho. Para processar
  arquivos hostis em larga escala, isole a análise em um processo
  separado.
- Lista completa por formato em [docs/formats.md](docs/formats.md).
