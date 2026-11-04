# 0016. Análise isolada em processo separado

- Estado: Aceito
- Data: 2026-11-04

## Contexto

O cancelamento do ADR 0008 é cooperativo: um laço infinito dentro do
metadata-extractor ou do PDFBox não é interrompido, e um
`OutOfMemoryError` derruba a aplicação inteira. A Fase 0 (ADR 0014)
apontou essa lacuna como a mais relevante frente ao `tika-pipes`, que
roda a análise em outro processo. Quem analisa arquivos hostis em
escala precisa de três garantias que uma thread não dá: prazo rígido,
teto de memória e sobrevivência a uma falha fatal do parser.

## Decisão

- **Novo módulo `metacore-isolation`**, só com o core como dependência.
  `IsolatedAnalyzer` analisa `Path` e `InputStream` em processos Java
  filhos (workers), com a mesma assinatura de `MetadataAnalyzer` e o
  mesmo `MetadataResult`.
- **Workers reaproveitados.** Subir uma JVM custa centenas de
  milissegundos, então cada worker atende várias análises, uma por vez.
  `workers(n)` limita quantos existem ao mesmo tempo; um worker é
  descartado depois de falhar, de estourar o prazo ou de
  `maxAnalysesPerWorker` análises.
- **Prazo rígido.** `timeout` (30 s por padrão) conta do envio do pedido
  até a resposta; ao estourar, o worker é morto. O resultado é `FAILED`
  com aviso `TIMEOUT`. Subir o worker não entra na conta.
- **Teto de memória.** O worker roda com `-Xmx` (`maxHeapSize`, 512 MiB
  por padrão) e `-XX:+ExitOnOutOfMemoryError`. Falta de memória, queda
  da JVM ou `System.exit` dentro de um parser viram `FAILED` com aviso
  `WORKER_FAILED`, em vez de derrubar quem chamou.
- **Os avisos novos ficam no módulo** (`IsolationWarnings`), como
  códigos de terceiros (ADR 0006): o vocabulário do core não muda.
  Os dois têm `dataLoss = true`.
- **Fronteira exceção/resultado preservada.** `InvalidInputException` e
  `InputLimitExceededException` lançadas no worker são relançadas em
  quem chamou. Falhar ao subir o worker lança `IsolationException`
  (subclasse de `MetaCoreException`): é um problema de ambiente, não do
  conteúdo.
- **Protocolo binário próprio** sobre stdin/stdout do worker, com
  versão no handshake. Nada de serialização Java. O decodificador
  valida tamanhos e quantidades contra os `AnalysisLimits` e reconstrói
  os objetos pelos construtores públicos, que validam o resto. Saída
  inesperada do worker vira `WORKER_FAILED`.
- **Stream sob demanda entre processos.** Para `InputStream`, o worker
  pede blocos a quem chamou à medida que o parser lê, e o ADR 0015
  continua valendo: um stream enorme cujo parser só lê o cabeçalho não
  é copiado.
- **O stdout do worker é só do protocolo.** O worker redireciona
  `System.out` para o stderr antes de carregar qualquer parser. O stderr
  é drenado, e a última linha entra na mensagem do `WORKER_FAILED`.
- **Comando do worker configurável.** O padrão é o `java` de
  `java.home` com o `java.class.path` atual; quem usa um classpath que
  não é de arquivos (Spring Boot, servidores de aplicação) informa o
  classpath ou o executável. Os provedores do worker vêm do
  `ServiceLoader`; os registrados com `addProvider` não atravessam o
  processo.
- **CLI:** `--isolate`, `--timeout` e `--max-memory` em `inspect`. O
  runtime do jpackage passa a incluir `bin/java` (sai o
  `--strip-native-commands`).

## Fora do escopo

O worker roda com o mesmo usuário e as mesmas permissões de quem o
criou. O isolamento protege contra travamento, consumo de memória e
queda do processo, não contra execução de código: uma falha do parser
que permita executar código no worker continua com acesso ao que a
aplicação acessa. Para isso, o worker precisa de um sandbox do sistema
operacional (contêiner, seccomp, usuário próprio), que o comando
configurável permite montar.

## Consequências

- Cada análise isolada paga a troca de mensagens entre processos, e o
  primeiro uso de cada worker paga a subida da JVM (medidas em
  `docs/performance.md`).
- A duração no resultado é a medida no worker, como na análise em
  processo; nos casos de `TIMEOUT` e `WORKER_FAILED`, é a vista por
  quem chamou.
- `IsolatedAnalyzer` segura processos e é `AutoCloseable`, ao contrário
  de `MetadataAnalyzer`.
- Resultados de `TIMEOUT` e `WORKER_FAILED` não têm detecção nem
  metadados: o que o worker extraiu antes de morrer se perde.
