# 0002. Coordenadas Maven e prefixo de pacotes

- Estado: Aceito
- Data: 2026-11-01

## Contexto

O Maven Central só aceita `groupId` cujo namespace foi verificado, em
geral por um domínio controlado pelo autor. O autor controla o domínio
`genosha.com.br`.

## Decisão

- `groupId`: `br.com.genosha`.
- `artifactId`: `metacore-bom`, `metacore-core`,
  `metacore-format-image`, `metacore-format-pdf`, `metacore-cli` e
  `metacore-examples` (este último não é publicado). O POM agregador é
  `metacore-parent`.
- Prefixo de pacotes: `br.com.genosha.metacore`.
- A verificação do namespace no Maven Central (registro TXT no DNS de
  `genosha.com.br`) fica para a Fase 6, antes da primeira publicação.

## Consequências

- O domínio `genosha.com.br` precisa continuar ativo enquanto o projeto
  publicar versões.
- O `metacore-bom` não herda do `metacore-parent`, para que importar o
  BOM não traga o `dependencyManagement` de bibliotecas de terceiros e
  de teste.
