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
- A CLI troca caracteres de controle vindos dos metadados por escapes
  visíveis, para que um arquivo não controle o terminal, e protege o CSV
  contra injeção de fórmulas.
- Análise opcional em processo separado (`metacore-isolation`, ADR
  0016), com prazo rígido e teto de memória: um parser travado ou sem
  memória vira resultado `FAILED`, sem derrubar a aplicação.

## Limitações conhecidas

- Com `MetadataAnalyzer`, a análise roda no mesmo processo da
  aplicação, e código das bibliotecas de formato (metadata-extractor,
  PDFBox) não pode ser interrompido. Para arquivos hostis, use
  `IsolatedAnalyzer`.
- O processo separado protege contra travamento, falta de memória e
  queda da JVM, não contra execução de código: o worker roda com as
  permissões de quem o criou. Um sandbox do sistema operacional pode ser
  montado com `IsolatedAnalyzer.Builder.launcher`.
- Lista completa por formato em [docs/formats.md](docs/formats.md).
