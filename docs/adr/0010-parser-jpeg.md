# 0010. Parser JPEG

- Estado: Aceito
- Data: 2026-11-01

## Contexto

Primeiro formato real do MetaCore (Fase 3). O blueprint pede
metadata-extractor, preservação de todos os campos, resultado parcial
em arquivos truncados, proteção contra XXE no XMP, fixtures geradas por
código, golden files e fuzzing.

## Decisão

- **Leitura tolerante.** O `JpegMetadataReader.readMetadata` do
  metadata-extractor lança `EOFException` em arquivos truncados e não
  devolve nada. O `JpegSegmentScanner` lê os segmentos até o SOS, para
  no primeiro problema com aviso `TRUNCATED` ou `CORRUPTED_SEGMENT` e
  entrega só os segmentos completos ao metadata-extractor
  (`processJpegSegmentData`). Truncamento depois do SOS, nos dados da
  imagem, não é detectado.
- **Campos brutos tipados.** Cada tag vira um campo com o valor
  convertido do objeto guardado pelo metadata-extractor (texto,
  inteiro, decimal, fração, binário, lista). Quando não há conversão
  fiel (por exemplo, fração 0/0), o campo usa a descrição em texto.
- **Chaves normalizadas** são atribuídas ao próprio campo bruto quando a
  conversão para o tipo da chave funciona; as coordenadas GPS em graus
  decimais são campos derivados, ao lado dos brutos. Tabela e
  precedência em `docs/keys.md`.
- **Namespaces** estáveis derivados do nome do diretório
  (`EXIF:SubIFD`, `EXIF:GPS`, `XMP`...).
- **Erros do metadata-extractor** registrados num diretório viram
  `CORRUPTED_SEGMENT` com o namespace como localização.
- **XXE.** O XMPCore (usado pelo metadata-extractor) proíbe DOCTYPE. Um
  XMP com DOCTYPE é descartado com `CORRUPTED_SEGMENT`. Testes com
  entidade externa e com expansão exponencial garantem isso.
- **Fixtures** geradas por código (`JpegFixtures`, `TiffBuilder`): a
  imagem base vem do codificador JPEG do JDK, e os segmentos EXIF e XMP
  são montados nos testes. Nenhum binário de terceiros no repositório.
- **Golden files** em `src/test/resources/golden/jpeg`, com a saída
  completa de cada fixture. Atualização intencional com
  `-Dmetacore.updateGolden=true`.
- **Robustez.** `JpegRobustnessTest` aplica 300 mutações reproduzíveis
  em toda build. `JpegFuzzTest` é o alvo do Jazzer: em build normal roda
  só a regressão; `JAZZER_FUZZ=1` faz fuzzing guiado por cobertura. Uma
  sessão de 60 s (cerca de 660 mil execuções) não encontrou falhas.

## Consequências

- Arquivos truncados devolvem tudo o que foi lido até o corte, com
  estado `PARTIAL`.
- Se o codificador JPEG do JDK mudar entre versões, os golden files
  podem precisar de atualização (só os campos de estrutura, como
  componentes e tabelas Huffman, dependem dele).
- O mesmo `DrewMetadataEmitter` serve para o PNG na Fase 5.
