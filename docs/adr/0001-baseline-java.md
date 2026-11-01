# 0001. Baseline do Java

- Estado: Aceito
- Data: 2026-11-01

## Contexto

O MetaCore é uma biblioteca, e o sucesso dela depende de ser fácil de
adicionar a projetos existentes. Boa parte desses projetos roda Java 17
ou 21. Os recursos de linguagem que o projeto pretende usar (records,
sealed interfaces, pattern matching para `switch`) já existem no Java
21. A CLI, por outro lado, é distribuída com runtime próprio.

## Decisão

- Os módulos da biblioteca compilam com `--release 21`.
- A CLI compila com `--release 25` e é distribuída com runtime Java 25
  embarcado.
- O build completo exige JDK 25. Em JDK 21 é possível verificar só a
  biblioteca (`-pl '!metacore-cli'`).
- A CI roda em Java 21 (biblioteca) e Java 25 (tudo).
- Subir a baseline da biblioteca é uma quebra de compatibilidade e exige
  novo ADR.

## Consequências

- Quem consome a biblioteca precisa de Java 21 ou superior.
- APIs posteriores ao Java 21 não podem ser usadas fora da CLI; o
  `--release 21` garante isso na compilação.
- Testes da CLI não rodam no job de Java 21.
