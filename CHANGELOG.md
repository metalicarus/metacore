# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
O projeto segue [versionamento semântico](https://semver.org/lang/pt-BR/).

## [Não lançado]

### Adicionado

- Estrutura Maven multi-módulo: `metacore-bom`, `metacore-core`,
  `metacore-format-image`, `metacore-format-pdf`, `metacore-cli` e
  `metacore-examples`.
- Build com `--release 21` para a biblioteca e `--release 25` para a
  CLI.
- JUnit 5, AssertJ, Mockito, JaCoCo, Spotless (google-java-format) e
  perfil `security` com OWASP Dependency-Check.
- CI no GitHub Actions com Java 21 e 25.
- Documentação inicial e ADRs 0001 a 0003.
