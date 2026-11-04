# 0017. CLI: pastas, CSV e saída em texto

- Estado: Aceito
- Data: 2026-11-04

## Contexto

A CLI do 0.1.0 só aceitava arquivos, um por argumento, e a saída em
texto era uma lista crua de chaves. Para analisar uma pasta era preciso
montar a lista no shell, e não havia como levar o resultado para uma
planilha. O blueprint (seção 8.1) já previa `--recursive` para depois do
primeiro release.

## Decisão

- **`inspect` é o comando padrão.** `metacore foto.jpg` é o mesmo que
  `metacore inspect foto.jpg`: quando o primeiro argumento não é um
  comando nem `-h`/`-V`, a CLI insere `inspect`. Sem argumentos,
  `metacore` mostra o nome em ASCII e os usos mais comuns, e sai com 0.
- **Pastas.** Um argumento que é pasta tem seus arquivos analisados;
  `-r` desce nas subpastas, e a CLI avisa no stderr quantas ficaram de
  fora sem ele.
  - A ordem é alfabética, para que a saída seja reproduzível.
  - Links simbólicos dentro das pastas não são seguidos, o que evita
    ciclos.
  - Arquivos e pastas ocultos (nome começando com ponto) ficam de fora,
    a menos que se use `--hidden`.
  - `--include` e `--exclude` recebem globs sem diferenciar maiúsculas.
    Um glob sem barra vale para o nome do arquivo; com barra, para o
    caminho relativo à pasta. Arquivos dados diretamente na linha de
    comando não passam pelos filtros.
  - No texto e no CSV, o rótulo de um arquivo é o caminho relativo à
    pasta informada, nunca o absoluto (blueprint 8.2).
- **Entrada padrão.** `-` lê o stdin. No `inspect`, o stream é lido sob
  demanda (ADR 0015). No `identify`, é lido para a memória, até
  `--max-file-size`.
- **Paralelismo.** `-j N` analisa até N arquivos ao mesmo tempo
  (padrão 1), mas a saída sai na ordem das entradas, idêntica à
  sequencial. Com isolamento, cada job tem seu worker.
- **CSV** (`-o csv`) com uma linha por entrada, em RFC 4180 com CRLF.
  - No `inspect`, as colunas fixas são `file`, `format`, `mime_type`,
    `status`, `warnings` e `error`. Depois vêm as chaves normalizadas
    encontradas, em ordem alfabética. Como as colunas só se conhecem no
    fim, as linhas ficam em memória até lá.
  - No `identify`, as colunas são `file`, `format`, `mime_type`,
    `confidence`, `warnings` e `error`.
  - Uma entrada que falhou tem `status` igual a `ERROR` e a mensagem em
    `error`.
  - Datas saem em ISO 8601.
  - Textos que começam com `= + - @`, tabulação ou CR ganham um
    apóstrofo na frente, para não virar fórmula na planilha. Números
    ficam como estão.
- **Seleção de campos.** `-k` filtra as chaves normalizadas por glob
  (`location.*`, `*.width`) no texto, no JSON e no CSV. No JSON, só
  muda o conteúdo de `metadata`; o schema continua o mesmo
  (`schemaVersion` 1).
- **Texto para pessoas, sem contrato.**
  - Os campos aparecem agrupados pelo prefixo da chave, com rótulos
    legíveis, datas sem o `T`, unidades conhecidas (px, s) e o tamanho
    do arquivo. `-v` mostra a detecção e os campos brutos por namespace.
  - Com várias entradas, há um resumo no fim. Quando a saída vai para
    arquivo e há um terminal, uma linha de progresso aparece no stderr.
  - Cor só no terminal (`--color auto`), respeitando `NO_COLOR` e
    `TERM=dumb`. `--color always` e `never` forçam a escolha.
  - Caracteres de controle e de direção de texto vindos dos metadados
    são trocados por escapes visíveis, para que um arquivo hostil não
    controle o terminal.
  - `-q` mantém a linha `nome: STATUS (Formato)`, boa para `grep`.
- **Ajuda e erros.**
  - A ajuda do `inspect` agrupa as opções em seções: Input, Output,
    Fields, Analysis e Isolation.
  - Um erro de uso mostra só a mensagem, sugestões de grafia e a dica
    `--help`, e mantém o código 2.
  - O nome em ASCII aparece só no `--help`, no `--version` e sem
    argumentos, nunca no meio da análise.
- **Autocompletar.** `metacore completion` gera o script de bash/zsh do
  picocli.

## Consequências

- Os contratos do 0.1 continuam: o JSON (ADR 0011), os códigos de saída
  e a omissão de caminhos sem `--show-path`.
- Mudam duas coisas de comportamento:
  - `metacore` sem argumentos saía com 2 (uso inválido) e agora sai
    com 0.
  - A saída em texto mudou por completo. Ela já era documentada como
    não estável; quem a usava em scripts deve passar para `-q`, JSON ou
    CSV.
- O CSV vira contrato: novas colunas fixas só entram no fim, e as de
  metadados seguem o vocabulário de chaves (`keysVersion`).
- O CSV guarda todas as linhas até o fim. Em pastas muito grandes, o
  NDJSON continua sendo a saída de memória constante.
- O executável nativo com GraalVM fica para um ADR próprio: exige o
  GraalVM no build e na CI e configuração de reflexão para o picocli e
  o Jackson.
