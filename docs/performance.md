# Desempenho e limites padrão

Medições de referência feitas com os benchmarks JMH do módulo
`metacore-benchmarks`, usadas para calibrar os valores padrão de
`AnalysisLimits` no release 0.1.0.

## Como rodar

```bash
mvn package -pl metacore-benchmarks -am -DskipTests
java -jar metacore-benchmarks/target/benchmarks.jar -prof gc
```

## Ambiente

Linux 6.8, 12 núcleos, OpenJDK 25.0.4, SSD. Uma execução (`@Fork(1)`,
3 aquecimentos e 5 medições de 1 s). Os números servem para comparar
versões na mesma máquina, não como garantia.

## Resultados

| Benchmark | Entrada | Tempo médio | Alocação por análise |
|---|---|---|---|
| `identifyLargeJpeg` | JPEG 4000x3000 (~4 MB) | 17 µs | 20 KB |
| `analyzeSmallJpeg` | JPEG 64x48 com EXIF e GPS | 44 µs | 49 KB |
| `analyzeLargeJpeg` | JPEG 4000x3000 (~4 MB) | 32 µs | 44 KB |
| `analyzeLargePng` | PNG 2000x1500 (~9 MB) | 1,2 ms | 58 KB |
| `analyzeLargePngStream` | o mesmo PNG, como `InputStream` | 3,1 ms | 8,5 MB |
| `analyzePdf500Pages` | PDF com 500 páginas | 1,0 ms | 1,0 MB |

Leituras:

- **JPEG** só lê até o início dos dados da imagem: o custo não depende do
  tamanho do arquivo.
- **PNG** percorre o arquivo inteiro (texto pode vir depois dos dados da
  imagem), mas pula os dados com `skip`, sem copiá-los.
- **PDF** lê a estrutura e a árvore de páginas, não o conteúdo.
- **Streams** são lidos sob demanda (ADR 0015): só o que o parser pede
  sai do stream. Um JPEG como stream custa o mesmo que como arquivo. O
  PNG lê o arquivo inteiro, e num stream os bytes ficam guardados para
  poderem ser relidos: até 8 MiB em memória, o resto em arquivo
  temporário. Antes da correção, o PNG como stream levava 38 ms.

## Análise isolada

`IsolatedAnalyzer` (ADR 0016) soma a troca de mensagens com o worker e,
no primeiro uso de cada worker, a subida da JVM. Medido com um laço
simples (5.000 análises por arquivo depois de aquecer, um worker), na
mesma máquina:

| Entrada | Em processo | Isolada | Primeira análise isolada |
|---|---|---|---|
| JPEG 64x48 com EXIF e GPS | 75 µs | 0,66 ms | 0,2 s |
| PNG com texto | 47 µs | 0,49 ms | 0,15 s |
| PDF com Info, 3 páginas | 130 µs | 0,82 ms | 0,19 s |

Na CLI, `inspect --isolate` com os três arquivos levou 1,1 s, contra
0,9 s em processo. O custo fixo de cerca de 0,5 ms por análise só pesa
em arquivos pequenos; quem precisa de vazão usa `workers(n)`.

| Opção | Padrão | Motivo |
|---|---|---|
| `timeout` | 30 s | Duas ordens de grandeza acima da análise mais lenta medida; serve para matar travamentos, não para cortar arquivos lentos |
| `maxHeapSize` | 512 MiB | Cobre o PNG e o PDF lidos inteiros até o `maxFileSize` padrão sem que a JVM do worker fique grande |
| `workers` | 1 | Conservador: cada worker é uma JVM. Aumente para analisar em paralelo |
| `maxAnalysesPerWorker` | 1.000 | Limita vazamentos nas bibliotecas de formato; trocar um worker custa uma subida de JVM a cada 1.000 análises |

## Limites padrão

| Limite | Padrão | Motivo |
|---|---|---|
| `maxFileSize` | 512 MiB | Acima disso, só o PNG ainda custaria tempo proporcional ao tamanho (cerca de 0,15 ms por MB) |
| `maxDetectionBytes` | 8 KiB | Nenhum detector usa mais que 1 KiB (o do PDF procura o cabeçalho nos primeiros 1024 bytes). Era 64 KiB: reduzir cortou a alocação do `identify` de 135 KB para 20 KB |
| `spoolMemoryThreshold` | 8 MiB | Quanto de um stream fica em memória antes de ir para disco. Só o que o parser lê é guardado: um JPEG guarda alguns KB; um PNG ou PDF pode chegar ao limiar. Em servidores com muitos uploads simultâneos, reduza |
| `maxMetadataEntries` | 10.000 | JPEGs reais com MakerNotes ficam na casa das centenas de campos |
| `maxValueLength` | 64 Ki caracteres | Textos longos (comentários, XMP em campos) cabem; valores maiores são truncados com aviso |
| `maxBinaryValueBytes` | 64 KiB | Conteúdo binário só é guardado até esse tamanho; o tamanho real sempre é informado |
| `maxDecompressedTextBytes` | 1 MiB | Teto para texto compactado (PNG `zTXt`/`iTXt`), para o pacote XMP do PDF e para cada parte XML de um pacote Office. XMP com histórico extenso do Photoshop pode passar disso; aumente se precisar |
| `maxAnalysisTime` | sem limite | Verificação cooperativa (ADR 0008); defina em servidores |

## Melhorias feitas a partir das medições

- `maxDetectionBytes` de 64 KiB para 8 KiB.
- O scanner de PNG alocava um buffer novo para cada chunk de dados da
  imagem pulado; agora usa `skip`. O PNG grande passou de 4,6 ms e
  2,4 MB para 1,2 ms e 58 KB.
