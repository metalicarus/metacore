# MetaCore --- Especificação do Produto e Plano de Desenvolvimento

**Documento:** Blueprint técnico, MVP e roadmap\
**Versão:** 0.2.1 --- revisão do rascunho inicial\
**Data:** 1º de novembro de 2026\
**Linguagem-alvo:** Java 21 como baseline da biblioteca; CI com Java 21 e
25; CLI distribuída com runtime Java 25\
**Build:** Maven\
**Licença sugerida:** Apache License 2.0 (a confirmar antes da
publicação)

------------------------------------------------------------------------

## 0. Histórico de alterações

### v0.2.1 (01/11/2026)

-   `groupId` definido como `br.com.genosha`, a partir do domínio
    `genosha.com.br`; prefixo de pacotes `br.com.genosha.metacore`
    (seções 5.3, 13 e 15).
-   Nova pendência: registrar e verificar o namespace `br.com.genosha`
    no Maven Central, na Fase 6, antes da primeira publicação (seções
    11 e 15).

### v0.2 (31/10/2026)

| Área | Mudança |
|---|---|
| Estratégia | Nova seção 1.4 com a tese de diferenciação frente ao Apache Tika, escrita como hipóteses verificáveis. A Fase 0 termina com uma decisão de seguir ou parar. |
| Java | Baseline da biblioteca passa de Java 25 para Java 21 (`--release 21`), com CI em 21 e 25. A CLI pode exigir 25 porque leva o próprio runtime. |
| Detecção | Confiança sai de `FileFormat` e fica só em `DetectionResult`. `FileFormat` ganha `parent` (hierarquia de tipos). Regras de resolução de conflito entre detectores foram definidas. |
| Entrada | `InputSource` passa a ter acesso aleatório, com spool controlado para `InputStream`. |
| Modelo | Chaves podem ter vários valores, com flag `primary` e regras de precedência. `MetadataValue` vira sealed interface com tipos para racional, datas com e sem fuso e binário. Campos sem chave normalizada também são preservados. |
| Resultado | Novo estado `UNKNOWN_FORMAT`. Regra explícita entre lançar exceção e devolver `FAILED`. Avisos ganham códigos estáveis. |
| API | `MetadataAnalyzer` deixa de ser `AutoCloseable`, passa a ser imutável e thread-safe. Descoberta de módulos por `ServiceLoader`. |
| Segurança | XXE no XMP, cancelamento cooperativo (sem promessa de timeout rígido) e fuzzing com Jazzer entram no MVP. |
| CLI/JSON | O exemplo de JSON agora traz `rawName` e `source`. Regras de serialização dos tipos definidas. Vários arquivos saem em NDJSON. Tabela de códigos de saída. Distribuição via jlink/jpackage. |
| Ferramentas | Spotless com google-java-format escolhido (sem Checkstyle). Fixtures de teste geradas por código sempre que possível. |
| Roadmap | Estimativas em ordem de grandeza por fase. Fase 0 com protótipo comparativo e critérios de decisão. |

------------------------------------------------------------------------

## 1. Visão do produto

O **MetaCore** será uma biblioteca Java para identificar arquivos e
extrair seus metadados por meio de uma API uniforme e tipada,
independentemente do formato de origem.

A ideia central é permitir que uma aplicação faça uma chamada simples e
receba um resultado estruturado contendo:

-   formato identificado, com a evidência usada e a confiança;
-   tipo MIME, quando conhecido;
-   extensão provável;
-   propriedades técnicas e descritivas, com valores tipados;
-   origem de cada propriedade (nome original, namespace e parser);
-   estado da análise, avisos e limitações encontrados.

Além da biblioteca, o projeto oferecerá uma ferramenta de linha de
comando (CLI) para inspeção de arquivos e exportação dos resultados em
JSON.

### 1.1 Problema que o MetaCore pretende resolver

Cada formato armazena metadados de maneira diferente. Imagens podem usar
EXIF, XMP ou IPTC; PDFs possuem propriedades próprias; arquivos Office
usam estruturas OOXML; arquivos de áudio e vídeo têm tags e fluxos
técnicos específicos.

O desenvolvedor acaba precisando conhecer e integrar bibliotecas
diferentes, lidar com modelos incompatíveis e escrever código de
conversão.

O MetaCore pretende oferecer uma camada de abstração consistente, sem
esconder informações específicas que possam ser importantes.

### 1.2 Proposta de valor

1.  **API uniforme e tipada:** uma maneira previsível de analisar
    diferentes formatos, com datas, números e racionais como tipos de
    verdade.
2.  **Modularidade:** suporte a formatos organizado em módulos ou
    provedores, com dependências isoladas.
3.  **Transparência:** cada valor diz de onde veio.
4.  **Uso simples:** poucas chamadas para executar uma análise.
5.  **CLI útil:** inspeção rápida sem necessidade de escrever código.
6.  **Extensibilidade:** terceiros podem adicionar detectores e parsers.

### 1.3 O que o MetaCore não pretende ser no MVP

-   Um editor de arquivos.
-   Um conversor de formatos.
-   Um extrator de texto ou conteúdo.
-   Um indexador ou mecanismo de busca.
-   Um antivírus ou ferramenta de perícia digital.
-   Um substituto integral de ferramentas especializadas como ExifTool,
    FFmpeg ou Apache Tika.
-   Uma interface gráfica ou serviço REST.

### 1.4 Tese de diferenciação

O Apache Tika já oferece detecção por assinatura com hierarquia de
tipos, parsers modulares, metadados com namespace, uma CLI com saída
JSON, e usa por dentro as mesmas bibliotecas previstas aqui
(metadata-extractor e PDFBox). Por isso o MetaCore só se justifica se
entregar algo que o Tika não entrega bem. As hipóteses abaixo são o
diferencial pretendido e precisam ser confirmadas na Fase 0.

| # | Hipótese | Como verificar na Fase 0 |
|---|---|---|
| H1 | **Valores tipados.** O `Metadata` do Tika é essencialmente baseado em `String`; o consumidor converte datas, números e racionais por conta própria. | Extrair as mesmas fixtures com o Tika e listar quantos campos exigem conversão manual e quantos perdem informação (fuso, fração). |
| H2 | **Rastreio por campo.** O Tika mapeia campos para chaves próprias e nem sempre preserva o nome original, o diretório EXIF ou o parser. | Comparar, campo a campo, o que o Tika devolve com o que o metadata-extractor expõe diretamente. |
| H3 | **Estado explícito.** O Tika sinaliza falhas parciais e formatos sem parser de forma pouco uniforme (exceções, metadados vazios, chaves de aviso). | Rodar arquivos truncados, corrompidos e sem parser e registrar o comportamento observado. |
| H4 | **Leveza.** Um consumidor que só quer metadados de JPEG não deveria carregar dezenas de MB de dependências. | Medir o tamanho do classpath e o tempo de inicialização de `tika-core` + parsers mínimos versus o protótipo. |

**Critério de decisão ao fim da Fase 0:**

-   **Seguir como projeto independente** se pelo menos H1 e H2 se
    confirmarem como lacunas reais e relevantes.
-   **Construir como camada sobre o Tika** (por exemplo, reaproveitar
    `tika-core` para detecção e oferecer o modelo tipado por cima) se a
    lacuna for principalmente de modelo de dados, e não de detecção ou
    extração.
-   **Parar ou contribuir com o Tika** se nenhuma hipótese se
    confirmar.

------------------------------------------------------------------------

## 2. Princípios de arquitetura

### 2.1 Separar identificação de extração

Identificar um arquivo e extrair seus metadados são tarefas diferentes.

O detector deve responder: "Que formato provavelmente é este arquivo, e
com base em quê?"

O parser deve responder: "Quais metadados consigo extrair deste
conteúdo?"

Essa separação permite identificar formatos mesmo quando ainda não
existe parser implementado.

### 2.2 Não confiar apenas na extensão

A extensão é uma pista, não uma prova. A identificação deve priorizar
assinaturas binárias (magic numbers) e outras evidências internas.

A extensão pode ser usada como evidência complementar, mas nunca
prevalece sobre uma assinatura incompatível. Toda divergência entre
extensão e conteúdo gera o aviso `EXTENSION_MISMATCH`.

### 2.3 Preservar dados específicos

A normalização não pode destruir a informação original. Todo campo
extraído aparece no resultado, tenha ou não uma chave normalizada. Cada
propriedade mantém:

-   chave normalizada, quando houver;
-   valor tipado;
-   nome original do campo;
-   grupo ou namespace de origem;
-   parser responsável;
-   indicação de valor principal (`primary`) quando a mesma chave tem
    mais de um valor.

### 2.4 Tolerância a falhas

Um arquivo pode ser parcialmente legível. A biblioteca devolve os
metadados extraídos com sucesso e registra avisos sobre as partes que
não puderam ser processadas.

A fronteira entre exceção e resultado é definida na seção 6.10: falhas
antes de a análise começar viram exceção; tudo o que acontece depois
vira estado e avisos no resultado.

### 2.5 Segurança por padrão

Arquivos são entradas não confiáveis. O projeto impõe limites de
tamanho, evita extrações ilimitadas, não executa conteúdo incorporado,
não resolve entidades externas em XML e documenta os riscos dos formatos
compactados ou complexos.

### 2.6 Determinismo

A mesma entrada, com a mesma versão da biblioteca e a mesma
configuração, produz o mesmo resultado, na mesma ordem. Dados que variam
entre execuções (como duração da análise) ficam fora do JSON por padrão.

------------------------------------------------------------------------

## 3. Stack tecnológica

| Componente | Tecnologia | Uso |
|---|---|---|
| Linguagem (biblioteca) | Java 21 (`--release 21`) | Baseline de compilação dos módulos publicados |
| Linguagem (CLI) | Java 25 | Runtime embarcado na distribuição da CLI |
| Build | Apache Maven | Dependências, build e publicação |
| Testes | JUnit 5 | Testes unitários e de integração |
| Asserções | AssertJ | Asserções legíveis |
| Mocks | Mockito | Isolamento de componentes; configurar o agente do Mockito no Surefire (exigência do JDK 21+) |
| Fuzzing | Jazzer (integração JUnit 5) | Testes de robustez dos parsers |
| CLI | picocli | Comandos e argumentos |
| JSON | Jackson | Serialização da saída |
| Logging | SLF4J | Fachada de logging; o core não traz implementação |
| Logging de referência | Logback | Somente na CLI e nos testes |
| EXIF e imagens | metadata-extractor | Extração de metadados de JPEG e PNG |
| PDF | Apache PDFBox 3.x | Metadados de PDF |
| Formatação | Spotless + google-java-format | Padronização do código, verificada na CI |
| Cobertura | JaCoCo | Métricas de cobertura |
| CI | GitHub Actions | Build e testes em Java 21 e 25 |
| Segurança | OWASP Dependency-Check | Verificação de dependências |
| Distribuição da CLI | jlink + jpackage | Executável com runtime próprio |

### 3.1 Política de versão do Java

-   Os módulos da biblioteca compilam com `--release 21` e são testados
    em Java 21 e 25 na CI. Nenhuma compatibilidade é anunciada sem estar
    na matriz da CI.
-   Records, sealed interfaces e pattern matching para `switch` já estão
    disponíveis no Java 21 e cobrem o que o projeto precisa.
-   A baseline só sobe quando um recurso posterior for necessário de
    verdade, e a mudança é tratada como quebra de compatibilidade
    (versão major, ou minor enquanto o projeto estiver em 0.x, com
    aviso no changelog).
-   A CLI é distribuída com runtime Java 25 embarcado, então o usuário
    final não precisa ter Java instalado.

### 3.2 Bibliotecas que merecem avaliação

-   **Apache Tika:** referência principal e possível base (ver 1.4).
    Estudar API, hierarquia de tipos, `TikaInputStream` e o isolamento
    de processos do `tika-pipes`.
-   **metadata-extractor:** candidato para EXIF, XMP, IPTC e PNG.
-   **Apache PDFBox 3.x:** propriedades documentais, XMP e contagem de
    páginas.
-   **Apache POI:** possível suporte futuro a documentos Microsoft
    Office.
-   **Jackson:** JSON da CLI e, eventualmente, integração de aplicações.
-   **FFprobe:** integração opcional futura para áudio e vídeo. Não pode
    ser dependência obrigatória do core.
-   **GraalVM native-image:** avaliar depois do MVP como alternativa ao
    jlink para reduzir o tempo de inicialização da CLI.

Antes de adotar qualquer dependência, verificar licença, atividade de
manutenção, vulnerabilidades conhecidas, compatibilidade com Java 21 e
25 e cobertura real dos formatos pretendidos.

------------------------------------------------------------------------

## 4. Escopo do MVP

O MVP demonstra o fluxo completo de ponta a ponta sem tentar suportar
dezenas de formatos.

### 4.1 Formatos iniciais

**JPEG**

-   Identificação por assinatura (`FF D8 FF`).
-   MIME `image/jpeg`.
-   Dimensões pelo marcador SOF (fonte estrutural).
-   EXIF: data/hora original (com fuso quando `OffsetTimeOriginal`
    existir), fabricante e modelo da câmera, orientação, dimensões,
    tempo de exposição (racional) e GPS em graus decimais.
-   Dimensões da miniatura (IFD1) mantidas em chaves próprias
    (`image.thumbnail.*`), nunca misturadas com as da imagem principal.
-   Preservação de todos os campos retornados pelo metadata-extractor,
    inclusive os sem chave normalizada.

**PNG**

-   Identificação por assinatura (`89 50 4E 47 0D 0A 1A 0A`).
-   MIME `image/png`.
-   Largura, altura, profundidade de bits e tipo de cor (chunk IHDR).
-   Campos textuais (`tEXt`, `iTXt`, `zTXt`), com limite de tamanho para
    o conteúdo descompactado de `zTXt`/`iTXt`.

**PDF**

-   Identificação por assinatura (`%PDF-`) e validação básica.
-   MIME `application/pdf`.
-   Título, autor, assunto, palavras-chave, criador, produtor, datas de
    criação e modificação e versão do PDF.
-   Fontes: Info dictionary e XMP. Precedência na seção 6.6.
-   Número de páginas pela árvore de páginas, sem processar o conteúdo.
-   PDF criptografado: o estado é `PARTIAL` com aviso `ENCRYPTED` quando
    os metadados não puderem ser lidos sem senha. Nenhuma tentativa de
    senha é feita.

A ordem continua sendo JPEG, depois PNG, depois PDF. JPEG fecha o fluxo
inicial e põe à prova o modelo de metadados com EXIF, que é o caso mais
rico (vários diretórios, racionais, datas sem fuso, GPS).

### 4.2 Funcionalidades obrigatórias

-   API Java pública para analisar um `Path` ou um `InputStream`.
-   Identificação de formato independente da extração.
-   Resultado imutável, tipado e serializável em JSON.
-   Parsers selecionados pelo formato detectado.
-   Estados e avisos com códigos estáveis.
-   Limites de recursos configuráveis.
-   CLI com comandos `identify` e `inspect`.
-   Testes com arquivos válidos, inválidos, truncados, maliciosos e com
    metadados ausentes, além de fuzzing.
-   Documentação de instalação, API, chaves normalizadas e exemplos de
    uso.

### 4.3 Fora do MVP

-   GUI JavaFX.
-   Aplicação web ou REST.
-   Processamento distribuído.
-   Edição ou remoção de metadados.
-   Extração de texto e OCR.
-   Análise de conteúdo semântico.
-   Extração completa de vídeo e áudio.
-   Plugins carregados dinamicamente de fontes não confiáveis.
-   Banco de dados ou indexação persistente.
-   Isolamento da análise em processo separado.
-   Descritores `module-info.java` completos (ver 5.3).

------------------------------------------------------------------------

## 5. Arquitetura proposta

Projeto Maven multi-módulo:

``` text
metacore/
├── pom.xml
├── README.md
├── LICENSE
├── CONTRIBUTING.md
├── CHANGELOG.md
├── SECURITY.md
├── docs/
│   ├── adr/
│   ├── architecture.md
│   ├── formats.md
│   ├── keys.md
│   ├── roadmap.md
│   └── security.md
├── metacore-bom/
├── metacore-core/
├── metacore-format-image/
├── metacore-format-pdf/
├── metacore-cli/
└── metacore-examples/
```

### 5.1 Responsabilidade dos módulos

**metacore-bom**

-   Bill of materials para o consumidor alinhar as versões dos módulos.

**metacore-core**

-   Modelos públicos, contratos de detector e parser (SPI).
-   Orquestração da análise, resolução de detecção e limites.
-   Resultado, avisos e exceções.
-   Descoberta de provedores.
-   Única dependência de runtime: SLF4J API.

**metacore-format-image**

-   Detectores e parsers de JPEG e PNG.
-   Dependência do metadata-extractor.

**metacore-format-pdf**

-   Detector e parser de PDF.
-   Dependência do PDFBox.

**metacore-cli**

-   Comandos picocli, formatação de saída e exportação JSON.
-   Depende de todos os módulos de formato.
-   Empacotada com jlink/jpackage.

**metacore-examples**

-   Pequenos exemplos de integração. Não é publicado no Maven Central.

### 5.2 Fluxo de execução

``` text
Arquivo ou InputStream
   |
   v
InputSource  (amostra limitada para detecção; acesso aleatório para parsers)
   |
   v
FormatDetector (todos os registrados)
   |
   v
DetectionResolver  ->  DetectionResult (formato, confiança, evidências)
   |                      |
   |                      +--> nenhum formato: UNKNOWN_FORMAT
   v
ParserRegistry  (busca pelo formato; sobe na hierarquia se preciso)
   |                      |
   |                      +--> nenhum parser: UNSUPPORTED
   v
MetadataParser  (com ParseContext: limites, cancelamento, coletor de avisos)
   |
   v
MetadataResult
   |
   +----> API Java
   |
   +----> CLI / JSON
```

O core coordena o fluxo. Os módulos de formato fornecem implementações
especializadas.

### 5.3 Descoberta de módulos e JPMS

-   Os módulos de formato se registram por `ServiceLoader`, com
    arquivos em `META-INF/services` apontando para implementações de
    `MetadataProvider`.
-   `MetadataAnalyzer.createDefault()` carrega todos os provedores
    visíveis. O builder permite registrar provedores explicitamente e
    desligar a descoberta automática.
-   No MVP cada JAR publica `Automatic-Module-Name` no manifesto (por
    exemplo, `br.com.genosha.metacore.core`).
    Descritores `module-info.java` completos ficam para depois, quando
    estiver confirmado que as dependências (metadata-extractor, PDFBox)
    convivem bem com o module path.
-   O pacote `internal` não faz parte da API pública e pode mudar sem
    aviso.

------------------------------------------------------------------------

## 6. Modelo da API pública

Os nomes abaixo orientam a implementação. As assinaturas definitivas
são validadas na Fase 2 e registradas em ADR.

### 6.1 FileFormat

Representa a identidade de um formato. Não carrega confiança.

| Campo | Descrição |
|---|---|
| `id` | Identificador estável, por exemplo `jpeg`, `png`, `pdf`, `zip` |
| `mimeType` | Tipo MIME principal |
| `displayName` | Nome legível |
| `extensions` | Extensões comuns, sem ponto |
| `parent` | `Optional<String>` com o `id` do supertipo (por exemplo, `docx` tem `parent = zip`) |

Os formatos ficam num `FormatRegistry` aberto: módulos externos podem
adicionar formatos. Não há enumeração fechada.

### 6.2 DetectionResult

Resultado de uma detecção.

| Campo | Descrição |
|---|---|
| `format` | `FileFormat` identificado |
| `confidence` | Enum ordenado: `EXACT`, `HIGH`, `MEDIUM`, `LOW` |
| `evidence` | Conjunto de `Evidence`: `MAGIC`, `STRUCTURE`, `EXTENSION`, `DECLARED_TYPE` |
| `detectorId` | Detector que produziu o resultado |

A confiança é um enum, e não um número, para evitar uma precisão que não
existe.

### 6.3 Resolução de conflitos entre detectores

Todos os detectores registrados avaliam a amostra. O `DetectionResolver`
escolhe o vencedor aplicando, em ordem:

1.  Evidência interna (`MAGIC`, `STRUCTURE`) vence evidência só de
    `EXTENSION`.
2.  Entre candidatos compatíveis na hierarquia, o mais específico vence
    (`docx` vence `zip`).
3.  Maior confiança vence.
4.  Maior prioridade declarada pelo detector vence.
5.  Empate persistente: desempate determinístico pelo `detectorId` e
    aviso `AMBIGUOUS_DETECTION`.

Se a extensão do arquivo não pertencer ao formato vencedor, o resultado
recebe o aviso `EXTENSION_MISMATCH`.

### 6.4 InputSource

Abstração de leitura usada por detectores e parsers.

-   **Amostra para detecção:** `peek(n)` devolve até `maxDetectionBytes`
    do início, sem consumir a fonte.
-   **Fluxo sequencial:** `openStream()` para parsers que leem em
    sequência (metadata-extractor).
-   **Acesso aleatório:** `randomAccess()` para parsers que precisam
    pular pelo arquivo (PDFBox lê o trailer no fim do arquivo).

Comportamento por tipo de entrada:

-   `Path`: acesso aleatório nativo, sem cópia.
-   `InputStream`: abaixo de `spoolMemoryThreshold`, o conteúdo fica em
    memória; acima disso, é copiado para um arquivo temporário apagado
    ao fim da análise. A cópia respeita `maxFileSize`.

**Propriedade do stream:** a biblioteca nunca fecha um `InputStream`
fornecido pelo chamador. Ela lê o que precisa e devolve o controle.

### 6.5 Metadata e MetadataValue

`Metadata` representa um campo extraído.

| Campo | Descrição |
|---|---|
| `key` | `Optional<String>` com a chave normalizada, por exemplo `image.width`; vazio para campos sem mapeamento |
| `value` | `MetadataValue` tipado |
| `rawName` | Nome original no formato ou na biblioteca subjacente |
| `namespace` | Grupo de origem, por exemplo `EXIF:IFD0`, `EXIF:SubIFD`, `XMP:dc`, `PDF:Info` |
| `source` | Parser que extraiu o dado |
| `primary` | `true` no valor principal de uma chave normalizada (ver 6.6) |

`MetadataValue` é uma sealed interface:

| Tipo | Representação Java | Uso típico |
|---|---|---|
| `TextValue` | `String` | Fabricante, título |
| `IntegerValue` | `long` | Largura, número de páginas |
| `DecimalValue` | `BigDecimal` | Latitude, longitude |
| `RationalValue` | numerador e denominador `long` | Tempo de exposição `1/125`, abertura |
| `BooleanValue` | `boolean` | Flags |
| `LocalDateTimeValue` | `LocalDateTime` | Data EXIF sem fuso conhecido |
| `OffsetDateTimeValue` | `OffsetDateTime` | Data com fuso (EXIF com `OffsetTime*`, PDF, XMP) |
| `BinaryValue` | tamanho em bytes; conteúdo opcional e limitado | Miniatura, MakerNote |
| `ListValue` | lista de `MetadataValue` | Palavras-chave |

Regras de conversão:

-   Datas sem fuso nunca recebem fuso inventado.
-   GPS é convertido de graus/minutos/segundos para graus decimais na
    chave normalizada; o valor original continua presente como campo
    bruto.
-   Uma conversão que falha não descarta o campo: ele volta como
    `TextValue` com o texto original e gera o aviso
    `VALUE_CONVERSION_FAILED`.
-   O conteúdo de `BinaryValue` só é guardado até `maxBinaryValueBytes`;
    acima disso, só o tamanho.

### 6.6 Chaves com vários valores e precedência

A mesma chave normalizada pode aparecer mais de uma vez. Exatamente um
valor por chave recebe `primary = true`, escolhido pela tabela de
precedência documentada em `docs/keys.md`. Exemplos iniciais:

| Chave | Precedência |
|---|---|
| `image.width`, `image.height` | 1. Estrutura do arquivo (SOF do JPEG, IHDR do PNG); 2. EXIF SubIFD `ExifImageWidth/Height`; 3. EXIF IFD0 `ImageWidth/Length` |
| `image.datetime_original` | 1. EXIF SubIFD `DateTimeOriginal` (+ `OffsetTimeOriginal`, se houver); 2. XMP `exif:DateTimeOriginal`; 3. XMP `xmp:CreateDate` |
| `document.title`, `document.author` e similares | PDF anterior ao 2.0: 1. Info dictionary; 2. XMP. PDF 2.0: 1. XMP; 2. Info dictionary |

Quando os valores de fontes diferentes divergem, o resultado recebe o
aviso `METADATA_CONFLICT` com a chave envolvida.

Exemplos de chaves normalizadas: `file.mime_type`, `file.extension`,
`image.width`, `image.height`, `image.bit_depth`, `image.color_type`,
`image.orientation`, `image.thumbnail.width`, `image.camera.make`,
`image.camera.model`, `image.camera.exposure_time`,
`image.datetime_original`, `location.latitude`, `location.longitude`,
`document.title`, `document.author`, `document.subject`,
`document.keywords`, `document.creator`, `document.producer`,
`document.created`, `document.modified`, `document.page_count`,
`document.format_version`.

O vocabulário de chaves tem versão própria (`keysVersion`),
independente da versão do schema JSON.

### 6.7 MetadataResult

Objeto imutável com o resultado de uma análise.

| Campo | Descrição |
|---|---|
| `file` | `FileInfo`: nome, tamanho e extensão; sem caminho absoluto |
| `detection` | `Optional<DetectionResult>` |
| `status` | `ExtractionStatus` |
| `metadata` | Lista ordenada de `Metadata` |
| `warnings` | Lista de `Warning` |
| `duration` | Duração da análise; fora do JSON por padrão (ver 2.6) |

Métodos de consulta:

``` java
Optional<Metadata> find(String key);            // valor principal da chave
List<Metadata> findAll(String key);             // todos os valores da chave
List<Metadata> findRaw(String namespace, String rawName);
```

### 6.8 ExtractionStatus

| Estado | Significado |
|---|---|
| `SUCCESS` | Formato identificado, parser executado sem avisos de perda de dados |
| `PARTIAL` | Parser executado; parte dos metadados não pôde ser lida (truncamento, segmento corrompido, criptografia, limite atingido) |
| `UNSUPPORTED` | Formato identificado, mas nenhum parser registrado para ele nem para seus supertipos |
| `UNKNOWN_FORMAT` | Nenhum detector reconheceu o conteúdo |
| `FAILED` | Formato identificado e parser disponível, mas a extração falhou por completo |

### 6.9 Avisos

`Warning` tem um código estável, uma mensagem legível e, opcionalmente,
a localização (offset, segmento ou chave). O código é o contrato; a
mensagem pode mudar.

Códigos iniciais: `EXTENSION_MISMATCH`, `AMBIGUOUS_DETECTION`,
`NO_PARSER`, `TRUNCATED`, `CORRUPTED_SEGMENT`, `LIMIT_REACHED`,
`ENCRYPTED`, `VALUE_CONVERSION_FAILED`, `METADATA_CONFLICT`,
`PARSER_ERROR`, `CANCELLED`.

### 6.10 Exceções e a fronteira com o resultado

**Regra:** exceção apenas quando a análise não pôde nem começar. Depois
que a detecção começa, todo problema vira estado e avisos no resultado.

| Situação | Comportamento |
|---|---|
| Arquivo inexistente ou sem permissão de leitura | `InvalidInputException` |
| Arquivo maior que `maxFileSize` | `InputLimitExceededException` (subclasse de `InvalidInputException`) |
| Falha de I/O ao ler a amostra inicial | `InvalidInputException` |
| Formato desconhecido | Resultado `UNKNOWN_FORMAT` |
| Sem parser | Resultado `UNSUPPORTED` com aviso `NO_PARSER` |
| Parser lança `Exception` ou `StackOverflowError` | Resultado `FAILED` com aviso `PARSER_ERROR` e os metadados já coletados |
| `OutOfMemoryError` e demais `Error` | Propagam; não são capturados |

Hierarquia:

-   `MetaCoreException`: exceção-base, não verificada.
-   `InvalidInputException`: entrada inválida ou inacessível.
-   `InputLimitExceededException`: entrada acima de um limite.
-   `MetadataExtractionException`: usada pelos parsers (SPI) para
    sinalizar falha irrecuperável; o core a converte em `FAILED`.

A `UnsupportedFormatException` da v0.1 foi removida: o estado
`UNSUPPORTED` cobre o caso.

### 6.11 MetadataAnalyzer e limites

Fachada principal. Imutável e thread-safe depois de construída: uma
instância pode ser compartilhada pela aplicação inteira. Não é
`AutoCloseable`, porque não segura recursos entre análises; arquivos
temporários de spool são apagados ao fim de cada análise.

``` java
MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();

MetadataAnalyzer custom = MetadataAnalyzer.builder()
    .limits(AnalysisLimits.defaults().withMaxFileSize(DataSize.ofMegabytes(200)))
    .discoverProviders(false)
    .addProvider(new ImageMetadataProvider())
    .build();
```

`AnalysisLimits`, com valores padrão a calibrar na Fase 6:

| Limite | Função |
|---|---|
| `maxFileSize` | Tamanho máximo aceito |
| `maxDetectionBytes` | Bytes lidos do início para detecção |
| `spoolMemoryThreshold` | Até quanto um `InputStream` fica em memória |
| `maxMetadataEntries` | Número máximo de campos por resultado |
| `maxValueLength` | Tamanho máximo de um valor de texto |
| `maxBinaryValueBytes` | Tamanho máximo guardado de um valor binário |
| `maxDecompressedTextBytes` | Limite para texto compactado (PNG `zTXt`/`iTXt`) |

Atingir um limite durante a extração gera `PARTIAL` com aviso
`LIMIT_REACHED`; os dados lidos até ali são mantidos.

------------------------------------------------------------------------

## 7. API de uso: exemplos desejados

### 7.1 Análise de arquivo

``` java
MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();
MetadataResult result = analyzer.analyze(Path.of("foto.jpg"));

result.detection().ifPresent(d ->
    System.out.println("Formato: " + d.format().displayName()));

result.metadata().forEach(m ->
    System.out.printf("%s [%s/%s] = %s%n",
        m.key().orElse("-"), m.namespace(), m.rawName(), m.value()));
```

### 7.2 Consultar uma propriedade

``` java
long width = result.find("image.width")
    .map(Metadata::value)
    .map(v -> ((IntegerValue) v).value())
    .orElseThrow();

// Com pattern matching:
if (result.find("image.datetime_original").map(Metadata::value).orElse(null)
        instanceof OffsetDateTimeValue(var dateTime)) {
    System.out.println("Capturada em " + dateTime);
}

// Todas as larguras encontradas, de todas as fontes:
result.findAll("image.width").forEach(System.out::println);
```

Atalhos tipados (`findLong`, `findText`, `findDateTime`) são avaliados
na Fase 2, depois de escrever os exemplos reais.

### 7.3 InputStream

``` java
try (InputStream in = request.getInputStream()) {
    MetadataResult result = analyzer.analyze(in, "upload.pdf");
    // o MetaCore não fecha 'in'; o try-with-resources do chamador fecha
}
```

O segundo argumento é o nome declarado, usado só como evidência
`EXTENSION`.

### 7.4 Análise sem parser disponível

``` text
Format: ZIP (application/zip)
Extraction status: UNSUPPORTED
Warning [NO_PARSER]: No metadata parser is registered for this format.
```

------------------------------------------------------------------------

## 8. CLI

A CLI é construída com picocli e distribuída como executável
independente (jlink + jpackage), com runtime Java 25 embarcado.

Nome do executável: `metacore`.

### 8.1 Comandos

**identify** --- identifica o formato sem executar extração.

``` bash
metacore identify foto.jpg
```

**inspect** --- identifica e extrai os metadados.

``` bash
metacore inspect foto.jpg
```

**inspect com JSON**

``` bash
metacore inspect foto.jpg --output json
```

**Direcionar saída para arquivo**

``` bash
metacore inspect foto.jpg --output json --file resultado.json
```

**Vários arquivos** (NDJSON: um objeto JSON por linha, por arquivo)

``` bash
metacore inspect a.jpg b.png c.pdf --output json
```

**Diretório recursivo** (pode ficar para depois do primeiro release)

``` bash
metacore inspect ./imagens --recursive --output json
```

Opções comuns: `--max-file-size`, `--verbose`/`--quiet`,
`--include-binary` (inclui conteúdo binário em base64, até o limite),
`--show-path` (inclui o caminho informado; desligado por padrão),
`--strict` (ver 8.3).

### 8.2 Requisitos da CLI

-   Mensagens de erro claras, em stderr.
-   Saída humana legível por padrão.
-   JSON estável para automação, só em stdout.
-   Logs sempre em stderr, nunca misturados ao JSON.
-   Não incluir caminho absoluto por padrão.
-   Aviso no `--help` e na documentação: a saída pode conter dados
    pessoais presentes no arquivo (GPS, nomes, números de série).

### 8.3 Códigos de saída

| Código | Significado |
|---|---|
| `0` | Todos os arquivos foram analisados. Inclui `SUCCESS`, `PARTIAL`, `UNSUPPORTED` e `UNKNOWN_FORMAT`, que são resultados válidos |
| `1` | Pelo menos um arquivo terminou em `FAILED` ou não pôde ser lido |
| `2` | Erro de uso (argumentos inválidos); padrão do picocli |
| `3` | Com `--strict`: pelo menos um arquivo terminou em estado diferente de `SUCCESS` |

Quando há vários arquivos, vale o pior código encontrado.

### 8.4 Contrato JSON

``` json
{
  "schemaVersion": "1",
  "keysVersion": "1",
  "file": {
    "name": "foto.jpg",
    "size": 245810,
    "extension": "jpg"
  },
  "detection": {
    "format": {
      "id": "jpeg",
      "mimeType": "image/jpeg",
      "displayName": "JPEG",
      "parent": null
    },
    "confidence": "EXACT",
    "evidence": ["MAGIC", "EXTENSION"]
  },
  "status": "SUCCESS",
  "metadata": [
    {
      "key": "image.width",
      "value": 4000,
      "valueType": "INTEGER",
      "rawName": "Image Width",
      "namespace": "JPEG",
      "source": "jpeg",
      "primary": true
    },
    {
      "key": "image.width",
      "value": 4000,
      "valueType": "INTEGER",
      "rawName": "Exif Image Width",
      "namespace": "EXIF:SubIFD",
      "source": "jpeg",
      "primary": false
    },
    {
      "key": "image.camera.exposure_time",
      "value": { "numerator": 1, "denominator": 125 },
      "valueType": "RATIONAL",
      "rawName": "Exposure Time",
      "namespace": "EXIF:SubIFD",
      "source": "jpeg",
      "primary": true
    },
    {
      "key": "image.datetime_original",
      "value": "2026-07-14T16:32:05",
      "valueType": "LOCAL_DATE_TIME",
      "rawName": "Date/Time Original",
      "namespace": "EXIF:SubIFD",
      "source": "jpeg",
      "primary": true
    },
    {
      "key": null,
      "value": "EF24-70mm f/2.8L II USM",
      "valueType": "TEXT",
      "rawName": "Lens Model",
      "namespace": "EXIF:SubIFD",
      "source": "jpeg",
      "primary": false
    }
  ],
  "warnings": []
}
```

Regras de serialização:

| `valueType` | JSON |
|---|---|
| `TEXT` | string |
| `INTEGER` | número inteiro |
| `DECIMAL` | número, sem notação exponencial |
| `RATIONAL` | objeto `{ "numerator", "denominator" }` |
| `BOOLEAN` | booleano |
| `LOCAL_DATE_TIME` | string ISO-8601 sem fuso (`2026-07-14T16:32:05`) |
| `OFFSET_DATE_TIME` | string ISO-8601 com fuso (`2026-07-14T16:32:05-03:00`) |
| `BINARY` | objeto `{ "size" }`; com `--include-binary`, também `"base64"` |
| `LIST` | array de objetos `{ "value", "valueType" }` |

Avisos são serializados como `{ "code", "message", "location" }`.

A ordem dos campos e dos itens é determinística; testes de golden file
garantem isso (ver 10.5).

------------------------------------------------------------------------

## 9. Segurança, robustez e privacidade

Este tópico faz parte do MVP.

### 9.1 Limites de recursos

-   Limites configuráveis listados em 6.11, todos com valor padrão.
-   Nenhum arquivo é carregado inteiro na memória sem necessidade; o
    spool de `InputStream` vai para disco acima do limite.
-   PDFBox configurado para leitura com buffer em arquivo e uso de
    memória limitado; somente Info dictionary, XMP e árvore de páginas
    são lidos, nunca o conteúdo das páginas.

### 9.2 Cancelamento e tempo de análise

Java não permite interromper com segurança uma thread presa dentro de
uma biblioteca de terceiros. Por isso o MVP **não promete timeout
rígido**:

-   O `ParseContext` oferece cancelamento cooperativo
    (`checkCancelled()`), chamado pelos parsers do MetaCore entre etapas.
    Análises canceladas terminam em `PARTIAL` com aviso `CANCELLED`.
-   Código dentro do metadata-extractor e do PDFBox não é interrompível;
    os limites de tamanho são a proteção principal.
-   Isolamento em processo separado (como faz o `tika-pipes`) fica no
    roadmap pós-MVP e é a resposta recomendada para quem processa
    arquivos hostis em larga escala. A documentação de segurança diz
    isso explicitamente.

### 9.3 XML e XMP

XMP é XML e aparece em JPEG, PNG e PDF. Todo parser de XML usado direta
ou indiretamente precisa ter DTD e entidades externas desativados.

-   Verificar a configuração do XMPCore (usado pelo metadata-extractor)
    e do XmpBox (usado pelo PDFBox).
-   Ter fixtures maliciosas com XXE e expansão de entidades ("billion
    laughs") nos três formatos, e testes que provam que nenhuma
    entidade externa é resolvida e que a expansão é limitada.

### 9.4 Entradas malformadas

Testar arquivos:

-   vazios;
-   truncados em pontos diferentes;
-   com assinatura falsa;
-   com dimensões absurdas;
-   com segmentos inconsistentes ou com tamanho declarado maior que o
    arquivo;
-   com metadados corrompidos;
-   com conteúdo que não corresponde à extensão;
-   com texto compactado que expande demais (PNG `zTXt`).

A biblioteca falha de forma controlada, conforme a tabela de 6.10, e
não derruba o processo.

### 9.5 Fuzzing

Cada parser tem um alvo de fuzzing com Jazzer. A CI roda uma sessão
curta a cada build; sessões longas rodam periodicamente. Toda entrada
que gerar exceção não tratada vira fixture de regressão.

### 9.6 Privacidade

Metadados podem conter GPS, nomes, datas, números de série e
informações de dispositivos. A biblioteca apenas extrai e devolve esses
dados, sem enviá-los a serviços externos, e não grava nada além dos
arquivos temporários de spool.

A CLI avisa que a saída pode conter informações pessoais e não inclui
caminhos absolutos por padrão.

### 9.7 Dependências

-   Fixar versões de dependências.
-   Verificar licenças e vulnerabilidades na CI.
-   Atualizar dependências regularmente.
-   O core não tem dependências de runtime além da SLF4J API.

------------------------------------------------------------------------

## 10. Estratégia de testes

### 10.1 Testes unitários

-   Reconhecimento de assinaturas.
-   Resolução de conflitos entre detectores (regras de 6.3).
-   Conversões de tipo e regras de precedência.
-   Seleção de parser, inclusive subindo na hierarquia de formatos.
-   Descoberta e registro de provedores.
-   Fronteira entre exceção e resultado (tabela de 6.10).
-   Serialização JSON de cada `valueType`.

### 10.2 Testes de integração

Coleção pequena e versionada de fixtures. Sempre que possível, gerar as
fixtures por código (ImageIO, PDFBox, ou ExifTool para gravar tags
específicas) a partir de scripts versionados no repositório. Isso evita
problemas de licença e documenta como cada arquivo foi feito. Fixtures
de terceiros só com origem e licença registradas.

Para JPEG:

-   imagem sem EXIF;
-   imagem com EXIF básico;
-   imagem com GPS;
-   imagem com orientação;
-   imagem com miniatura de dimensões diferentes;
-   imagem com `OffsetTimeOriginal` e sem ele;
-   imagem com EXIF e XMP divergentes;
-   imagem truncada;
-   arquivo não JPEG com extensão `.jpg`.

Para PNG:

-   imagem simples;
-   imagem com campos `tEXt`, `iTXt` e `zTXt`;
-   `zTXt` que expande acima do limite;
-   arquivo truncado;
-   assinatura inválida.

Para PDF:

-   PDF simples;
-   PDF com propriedades documentais;
-   PDF 2.0 só com XMP;
-   PDF com Info e XMP divergentes;
-   PDF sem metadados;
-   arquivo truncado (sem trailer);
-   PDF criptografado.

### 10.3 Testes de segurança

-   Fixtures com XXE e expansão de entidades em XMP (9.3).
-   Fuzzing com Jazzer (9.5).
-   Arquivos acima de cada limite de 6.11.

### 10.4 Testes de contrato

Uma suíte abstrata que todo parser precisa passar:

-   chaves dentro das convenções documentadas em `docs/keys.md`;
-   no máximo um valor `primary` por chave;
-   a fonte não é alterada (checksum antes e depois);
-   limites respeitados;
-   avisos produzidos em falhas parciais;
-   valores compatíveis com o tipo declarado;
-   resultado determinístico em execuções repetidas.

Módulos de terceiros podem reutilizar essa suíte.

### 10.5 Golden files

A saída JSON de cada fixture é comparada com um arquivo esperado
versionado. Qualquer mudança no JSON aparece no diff do pull request.

### 10.6 Testes de desempenho

No MVP, medir:

-   tempo de identificação;
-   tempo de extração;
-   memória aproximada em arquivos de tamanhos diferentes;
-   tempo de inicialização da CLI.

Usar JMH para os microbenchmarks. Não estabelecer metas antes de ter uma
baseline reproduzível.

### 10.7 Matriz da CI

Build e testes completos em Java 21 e 25. Formatação (Spotless),
cobertura (JaCoCo), Dependency-Check e uma sessão curta de fuzzing em
toda execução.

------------------------------------------------------------------------

## 11. Roadmap

As estimativas abaixo são ordem de grandeza para **uma pessoa em tempo
integral** e servem para planejamento inicial. Serão recalibradas ao fim
da Fase 2.

### Fase 0 --- Descoberta e decisão (≈ 1 semana)

-   Rodar Tika, ExifTool e metadata-extractor sobre um conjunto inicial
    de fixtures e comparar os resultados campo a campo.
-   Verificar as hipóteses H1 a H4 (seção 1.4).
-   Verificar a disponibilidade do nome MetaCore (Maven Central, GitHub,
    marcas).
-   Escrever os primeiros ADRs (ver apêndice A).
-   Criar issues iniciais.

**Entrega:** relatório comparativo, ADRs e decisão registrada: seguir
independente, construir sobre o Tika, ou parar.

### Fase 1 --- Fundação do repositório (≈ 2 a 3 dias)

-   Criar repositório Git.
-   Configurar Maven multi-módulo com BOM.
-   Compilação com `--release 21`.
-   Configurar JUnit, AssertJ, Mockito (agente), JaCoCo e Spotless.
-   GitHub Actions com matriz Java 21 e 25.
-   README, LICENSE, CONTRIBUTING, SECURITY e estrutura de documentação.

**Critério de conclusão:** `mvn verify` executa localmente e na CI nas
duas versões do Java.

### Fase 2 --- Contratos do core (≈ 1 a 2 semanas)

-   `FileFormat`, `FormatRegistry` e hierarquia de tipos.
-   `DetectionResult`, `Confidence`, `Evidence` e `DetectionResolver`.
-   `InputSource` com amostra, fluxo e acesso aleatório; spool de
    `InputStream`.
-   `Metadata`, `MetadataValue` (sealed) e conversões.
-   `MetadataResult`, `ExtractionStatus`, `Warning`.
-   `AnalysisLimits` e `ParseContext` com cancelamento cooperativo.
-   SPI (`FormatDetector`, `MetadataParser`, `MetadataProvider`) e
    descoberta por `ServiceLoader`.
-   `MetadataAnalyzer` e builder.
-   Suíte de testes de contrato.

**Critério de conclusão:** testes demonstram o fluxo completo com
detectores e parsers falsos, incluindo conflito de detecção, falta de
parser, falha de parser e limite atingido.

### Fase 3 --- Primeiro formato: JPEG (≈ 1 a 2 semanas)

-   Detector JPEG.
-   Parser com metadata-extractor, incluindo XMP.
-   Mapeamento para chaves normalizadas e regras de precedência.
-   Preservação de todos os campos brutos.
-   Proteção contra XXE comprovada por teste.
-   Fixtures geradas por script, golden files e alvo de fuzzing.
-   Primeira versão de `docs/keys.md`.

**Critério de conclusão:** uma aplicação Java analisa JPEG e obtém
resultado tipado, consistente e determinístico.

### Fase 4 --- CLI inicial (≈ 1 semana)

-   Integrar picocli.
-   `identify` e `inspect`.
-   Saída legível e JSON com schema versionado; NDJSON para vários
    arquivos.
-   Códigos de saída da seção 8.3.
-   Empacotamento com jlink/jpackage.

**Critério de conclusão:** usuário inspeciona JPEG pelo terminal, sem
Java instalado, e automatiza a saída JSON.

### Fase 5 --- PNG e PDF (≈ 2 a 3 semanas)

-   Suporte a PNG.
-   Suporte a PDF com PDFBox, incluindo precedência Info/XMP e PDF
    criptografado.
-   Fixtures, golden files, fuzzing e documentação de cobertura.
-   Validação com arquivos incompletos, malformados e maliciosos.

**Critério de conclusão:** os três formatos do MVP funcionam pela API e
pela CLI, e todos passam na suíte de contrato.

### Fase 6 --- Qualidade e release 0.1 (≈ 1 a 2 semanas)

-   Revisar a API pública e mover para `internal` o que não deve ser
    público.
-   Calibrar os valores padrão dos limites com base nas medições.
-   Revisar licenças e dependências.
-   Sessão longa de fuzzing; testes de desempenho básicos.
-   Documentar limitações, inclusive a ausência de timeout rígido.
-   Exemplos de integração.
-   Registrar e verificar o namespace `br.com.genosha` no Maven Central
    (registro TXT no DNS de `genosha.com.br`).
-   Publicar no Maven Central, com assinatura dos artefatos.
-   Release, changelog e binários da CLI.

**Critério de conclusão:** um desenvolvedor externo instala, integra e
usa o MetaCore seguindo o README.

**Total estimado:** cerca de 8 a 11 semanas.

### Depois do MVP

Priorizadas conforme interesse real:

-   análise em processo separado para entradas hostis;
-   DOCX e outros formatos OOXML com Apache POI (já previstos pela
    hierarquia `zip` → `docx`);
-   MP3 e outros formatos de áudio;
-   vídeo por integração opcional com FFprobe;
-   análise recursiva de diretórios, se não entrar no MVP;
-   descritores `module-info.java` completos;
-   CLI com GraalVM native-image;
-   saída CSV e relatório HTML;
-   plugins externos;
-   benchmarks e otimizações.

------------------------------------------------------------------------

## 12. Critérios de aceite do MVP

O MVP estará pronto quando:

1.  Os módulos da biblioteca compilarem com `--release 21` e os testes
    passarem em Java 21 e 25 na CI.
2.  A API analisar JPEG, PNG e PDF a partir de `Path` e de
    `InputStream`.
3.  A identificação não depender exclusivamente da extensão, e
    divergências gerarem `EXTENSION_MISMATCH`.
4.  A extração devolver valores tipados, chaves normalizadas, dados de
    origem e um valor principal por chave.
5.  Os cinco estados (`SUCCESS`, `PARTIAL`, `UNSUPPORTED`,
    `UNKNOWN_FORMAT`, `FAILED`) forem produzidos nos casos previstos e
    cobertos por teste.
6.  A fronteira entre exceção e resultado seguir a tabela de 6.10.
7.  A CLI executar `identify` e `inspect`, exportar JSON versionado e
    respeitar os códigos de saída.
8.  Arquivos inválidos e maliciosos não derrubarem o processo, e os
    testes de XXE passarem nos três formatos.
9.  Os alvos de fuzzing rodarem na CI sem falhas não tratadas.
10. A saída JSON for determinística e protegida por golden files.
11. Houver documentação de instalação, API, chaves normalizadas,
    formatos suportados, limites e limitações de segurança.
12. Houver ao menos um exemplo de integração Java funcional.
13. As licenças das dependências e das fixtures estiverem verificadas.

------------------------------------------------------------------------

## 13. Organização inicial de pacotes

Coordenadas Maven: `groupId` `br.com.genosha` (domínio
`genosha.com.br`); `artifactId` `metacore-bom`, `metacore-core`,
`metacore-format-image`, `metacore-format-pdf` e `metacore-cli`.
Prefixo de pacotes: `br.com.genosha.metacore` (abaixo, `<base>`).

``` text
<base>
├── api
│   ├── MetadataAnalyzer
│   ├── AnalyzerBuilder
│   ├── AnalysisLimits
│   ├── MetadataResult
│   ├── ExtractionStatus
│   └── FileInfo
├── format
│   ├── FileFormat
│   ├── FormatRegistry
│   ├── DetectionResult
│   ├── Confidence
│   └── Evidence
├── metadata
│   ├── Metadata
│   ├── MetadataValue (sealed) e implementações
│   ├── Warning
│   └── WarningCode
├── io
│   └── InputSource
├── spi
│   ├── FormatDetector
│   ├── MetadataParser
│   ├── MetadataProvider
│   └── ParseContext
├── exception
│   ├── MetaCoreException
│   ├── InvalidInputException
│   ├── InputLimitExceededException
│   └── MetadataExtractionException
└── internal            (não faz parte da API pública)
    ├── ParserRegistry
    ├── DetectionResolver
    └── Spooler
```

Pacotes específicos de formato ficam nos respectivos módulos, por
exemplo `<base>.format.image.jpeg` e `<base>.format.pdf`.

------------------------------------------------------------------------

## 14. Primeiras tarefas concretas

1.  Montar o conjunto inicial de fixtures e rodar Tika, ExifTool e
    metadata-extractor sobre ele.
2.  Escrever o relatório comparativo e decidir com base na seção 1.4.
3.  Confirmar nome e licença.
4.  Criar o repositório, o `pom.xml` pai, o BOM e os módulos.
5.  Configurar `--release 21`, JUnit, Spotless e a CI com Java 21 e 25.
6.  Escrever `FileFormat`, `DetectionResult` e o `DetectionResolver`,
    com testes das regras de conflito.
7.  Escrever `Metadata`, `MetadataValue` e `MetadataResult` como modelos
    imutáveis.
8.  Escrever `InputSource` e o spool de `InputStream`.
9.  Definir a SPI e a descoberta por `ServiceLoader`.
10. Escrever a suíte de contrato e o fluxo com detectores e parsers
    falsos.
11. Implementar detector e parser JPEG, com teste de XXE.
12. Implementar `metacore inspect` com saída JSON.

**Não começar pela CLI nem por todos os formatos ao mesmo tempo.** O
primeiro marco técnico continua sendo uma chamada Java simples que
recebe um JPEG e devolve um `MetadataResult` correto, agora tipado e
com origem em cada campo.

------------------------------------------------------------------------

## 15. Riscos e decisões

### Risco: reinventar o Apache Tika

Tratado pela seção 1.4 e pela decisão ao fim da Fase 0. Se as hipóteses
não se confirmarem, o projeto muda de forma (camada sobre o Tika) ou
para.

### Risco: normalização excessivamente genérica

Mitigado por preservar todos os campos brutos, por chaves com vários
valores e por regras de precedência documentadas.

### Risco: escopo excessivo

O MVP permanece limitado a JPEG, PNG e PDF. Novos formatos só depois
do release 0.1.

### Risco: dependências pesadas

Cada módulo de formato isola suas dependências. O core só depende da
SLF4J API.

### Risco: parsers de terceiros sem cancelamento

Código dentro do metadata-extractor e do PDFBox não pode ser
interrompido. Mitigado por limites de tamanho e fuzzing; a solução
completa (processo separado) está no roadmap pós-MVP e é documentada
como limitação.

### Risco: colisão de nome

"MetaCore" pode já existir no Maven Central, no GitHub ou como marca.
Verificar na Fase 0.

### Decisões tomadas nesta versão

| Decisão | Escolha |
|---|---|
| Baseline do Java | 21 para a biblioteca; 25 embarcado na CLI |
| Formatação | Spotless + google-java-format |
| Descoberta de módulos | `ServiceLoader`; `Automatic-Module-Name` no MVP |
| Confiança da detecção | Enum ordenado em `DetectionResult` |
| Saída com vários arquivos | NDJSON |
| Distribuição da CLI | jlink + jpackage |
| `MetadataAnalyzer` | Imutável, thread-safe, não `AutoCloseable` |
| Coordenadas Maven | `groupId` `br.com.genosha`; pacotes `br.com.genosha.metacore` |

### Decisões que precisam ser confirmadas antes do primeiro release

-   Resultado da Fase 0 (seguir, construir sobre o Tika ou parar).
-   Nome definitivo e disponibilidade.
-   Verificação do namespace `br.com.genosha` no Maven Central: o
    Central Portal gera um código que deve ser publicado como registro
    TXT no DNS de `genosha.com.br`. O domínio precisa continuar ativo
    enquanto o projeto publicar versões.
-   Licença definitiva.
-   Estratégia de versionamento semântico durante a fase 0.x.
-   Política de estabilidade da API pública e do vocabulário de chaves.
-   Valores padrão dos limites.
-   Processo de publicação e assinatura dos artefatos.

------------------------------------------------------------------------

## 16. Definição de sucesso

O sucesso do primeiro release não será medido pela quantidade de
formatos suportados. Será medido pela capacidade de um desenvolvedor:

1.  adicionar a dependência ao projeto, em Java 21 ou superior;
2.  chamar uma API simples;
3.  receber metadados previsíveis e tipados, sem converter strings;
4.  entender de onde cada informação veio;
5.  tratar arquivos não suportados ou desconhecidos sem gambiarras;
6.  usar a CLI para investigar um arquivo rapidamente, sem ter Java
    instalado.

**Princípio orientador:** fazer poucas coisas, mas fazê-las de maneira
consistente, extensível e agradável para quem desenvolve.

------------------------------------------------------------------------

## Apêndice A --- ADRs iniciais

Registrar em `docs/adr/`, um arquivo por decisão:

1.  Resultado da Fase 0 e relação com o Apache Tika.
2.  Baseline do Java e política de atualização.
3.  Modelo de valores tipados (`MetadataValue` sealed).
4.  Chaves com vários valores, flag `primary` e precedência.
5.  Fronteira entre exceção e resultado.
6.  `InputSource`, acesso aleatório e spool.
7.  Descoberta de provedores e JPMS.
8.  Contrato JSON, versionamento do schema e do vocabulário de chaves.
9.  Cancelamento cooperativo e ausência de timeout rígido no MVP.

------------------------------------------------------------------------

*Fim do blueprint v0.2.1. Os contratos ainda devem ser validados por
protótipos e testes antes de serem considerados API estável.*
