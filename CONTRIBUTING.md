# Como contribuir

## Antes de começar

- Leia o [blueprint](docs/blueprint.md), principalmente as seções de
  princípios (2), modelo da API (6) e segurança (9).
- Mudanças de arquitetura ou de contrato público precisam de um ADR em
  [docs/adr](docs/adr/README.md).

## Fluxo

1. Crie uma branch a partir de `main`.
2. Rode `mvn spotless:apply` antes de commitar.
3. Garanta que `mvn verify` passa.
4. Abra um pull request descrevendo o que mudou e por quê.

## Regras do código

- A biblioteca compila com `--release 21`: não use APIs posteriores ao
  Java 21 fora do módulo `metacore-cli`.
- O `metacore-core` só depende da SLF4J API. Dependências de formato
  ficam nos módulos de formato.
- Todo parser novo precisa passar na suíte de testes de contrato e ter
  um alvo de fuzzing.
- Arquivos de teste (fixtures) devem ser gerados por script versionado
  sempre que possível. Fixtures de terceiros só com origem e licença
  registradas.

## Mensagens de commit

Frase curta no imperativo explicando a mudança, por exemplo
`Adiciona detector de PNG`. Detalhes no corpo da mensagem, quando
necessário.
