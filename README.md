# MetaCore

Biblioteca Java para identificar formatos de arquivo e extrair metadados
com uma API uniforme e tipada, acompanhada de uma ferramenta de linha de
comando.

> **Estado:** versão 0.1.0. Suporta JPEG, PNG, PDF, DOCX, XLSX, PPTX e
> ZIP. O plano completo
> está em [docs/blueprint.md](docs/blueprint.md).

## Quando usar o MetaCore (e quando usar o Tika)

O MetaCore devolve metadados **tipados** (datas com fuso, frações,
números), diz **de onde veio cada valor** e informa **o que deu errado**
(`PARTIAL`, `TRUNCATED`, `ENCRYPTED`...) em vez de lançar exceção. Cobre
poucos formatos (JPEG, PNG, PDF, documentos Office e ZIP), com cerca de
5 MB de dependências.

O [Apache Tika](https://tika.apache.org) cobre centenas de formatos,
extrai texto e detecta mais de mil tipos MIME. Se você precisa disso, use
o Tika. A comparação detalhada, com os mesmos arquivos nos dois, está em
[docs/fase0-tika.md](docs/fase0-tika.md).

## Instalação

Disponível no Maven Central:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>br.com.genosha</groupId>
      <artifactId>metacore-bom</artifactId>
      <version>0.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- Escolha os formatos; cada módulo traz o core junto. -->
  <dependency>
    <groupId>br.com.genosha</groupId>
    <artifactId>metacore-format-image</artifactId>
  </dependency>
  <dependency>
    <groupId>br.com.genosha</groupId>
    <artifactId>metacore-format-pdf</artifactId>
  </dependency>
  <dependency>
    <groupId>br.com.genosha</groupId>
    <artifactId>metacore-format-office</artifactId>
  </dependency>
</dependencies>
```

Requer Java 21 ou superior. Os módulos de formato são encontrados
automaticamente por `ServiceLoader`.

## Uso

```java
MetadataAnalyzer analyzer = MetadataAnalyzer.createDefault();
MetadataResult result = analyzer.analyze(Path.of("foto.jpg"));

result.find(MetadataKeys.IMAGE_WIDTH).ifPresent(m -> System.out.println(m.value()));
```

`MetadataAnalyzer` é imutável e thread-safe. Exceções só aparecem quando
a análise não pode começar (arquivo inexistente ou acima do limite); o
resto vira `status` e `warnings` no resultado. Mais exemplos, inclusive
como adicionar um formato próprio, em
[metacore-examples](metacore-examples/src/main/java/br/com/genosha/metacore/examples).

### Arquivos hostis: análise isolada

Para arquivos de origem desconhecida, o módulo `metacore-isolation`
(mesmo `groupId`, versão pelo BOM) roda a análise em processos Java
separados, com prazo rígido e teto de memória. Um parser travado é morto e vira `FAILED` com aviso `TIMEOUT`;
falta de memória vira `FAILED` com `WORKER_FAILED`, sem derrubar a
aplicação.

```java
try (IsolatedAnalyzer analyzer =
    IsolatedAnalyzer.builder().timeout(Duration.ofSeconds(10)).workers(4).build()) {
  MetadataResult result = analyzer.analyze(Path.of("upload.pdf"));
}
```

Os workers são reaproveitados; a primeira análise de cada um paga a
subida da JVM (cerca de 0,2 s) e as seguintes, cerca de 0,5 ms a mais
que em processo. Detalhes e limitações no
[ADR 0016](docs/adr/0016-isolamento-em-processo.md).

## CLI

```bash
metacore foto.jpg                         # o mesmo que metacore inspect foto.jpg
metacore foto.jpg --verbose               # todos os campos, como vieram do arquivo
metacore ./fotos -r                       # uma pasta e suas subpastas
metacore ./fotos -r --include '*.jpg' -j 4
metacore ./fotos -r -o csv -f fotos.csv   # planilha, uma linha por arquivo
metacore doc.pdf -o json | jq .           # NDJSON, uma linha por arquivo
metacore foto.jpg -k 'location.*'         # só os campos pedidos
cat upload.bin | metacore -               # entrada padrão
metacore identify *                       # só o formato
metacore --isolate --timeout 10s upload.pdf
```

Sem argumentos, `metacore` mostra os usos mais comuns, e
`metacore inspect --help` lista as opções por seção. A saída em texto
tem cores no terminal (desligadas com `NO_COLOR` ou `--color never`) e
não é um contrato. Para automação, use `-q`, JSON ou CSV; `metacore
completion` gera o autocompletar para bash e zsh. Detalhes no
[ADR 0017](docs/adr/0017-cli-pastas-csv-e-saida-em-texto.md).

A saída JSON tem uma linha por arquivo e segue o contrato do
[ADR 0011](docs/adr/0011-cli-e-contrato-json.md). Códigos de saída: 0
quando tudo foi analisado, 1 quando algum arquivo falhou, 2 para uso
inválido e 3 com `--strict` quando algum arquivo não terminou em
`SUCCESS`. A saída pode conter dados pessoais dos arquivos (GPS, nomes,
números de série).

Para gerar o executável com runtime próprio (sem exigir Java instalado):

```bash
mvn -Pdist package -pl metacore-cli -am -DskipTests
metacore-cli/target/dist/metacore/bin/metacore --help
```

## Módulos

| Módulo | Conteúdo | Java |
|---|---|---|
| `metacore-bom` | Alinhamento de versões para quem consome a biblioteca | — |
| `metacore-core` | Modelos públicos, SPI e orquestração da análise | 21+ |
| `metacore-testkit` | Suíte de contrato para parsers (dependência de teste) | 21+ |
| `metacore-format-image` | JPEG e PNG (metadata-extractor) | 21+ |
| `metacore-format-pdf` | PDF (Apache PDFBox) | 21+ |
| `metacore-format-office` | DOCX, XLSX, PPTX e ZIP, sem dependências além do JDK | 21+ |
| `metacore-isolation` | Análise em processo separado, com prazo rígido e teto de memória | 21+ |
| `metacore-cli` | Ferramenta `metacore` (picocli) | 25 |
| `metacore-examples` | Exemplos de integração; não é publicado | 21+ |
| `metacore-benchmarks` | Benchmarks JMH; não é publicado | 21+ |

Coordenadas Maven: `groupId` `br.com.genosha`. Pacotes:
`br.com.genosha.metacore`.

## Build

Requisitos: Maven 3.9+ e JDK 25 (o JDK 25 compila a biblioteca com
`--release 21` e a CLI com `--release 25`).

```bash
mvn verify
```

Para verificar só a biblioteca em JDK 21:

```bash
mvn verify -pl '!metacore-cli'
```

Comandos úteis:

| Comando | O que faz |
|---|---|
| `mvn spotless:apply` | Formata o código (google-java-format) |
| `mvn -Psecurity verify` | Roda o OWASP Dependency-Check; defina `NVD_API_KEY` para acelerar |
| `JAZZER_FUZZ=1 mvn test -pl metacore-format-image -am -Dtest=JpegFuzzTest -Dsurefire.failIfNoSpecifiedTests=false` | Fuzzing do parser JPEG por 60 s (`PngFuzzTest` para PNG; `-pl metacore-format-pdf` e `PdfFuzzTest` para PDF; `-pl metacore-format-office` e `OfficeFuzzTest` para Office e ZIP) |
| `java -jar metacore-benchmarks/target/benchmarks.jar -prof gc` | Benchmarks (depois de `mvn package -pl metacore-benchmarks -am -DskipTests`) |

O relatório de cobertura do JaCoCo fica em
`<módulo>/target/site/jacoco/index.html`.

## Documentação

- [Blueprint e roadmap](docs/blueprint.md)
- [Comparação com o Apache Tika](docs/fase0-tika.md)
- [Formatos suportados e limitações](docs/formats.md)
- [Chaves normalizadas](docs/keys.md)
- [Desempenho e limites padrão](docs/performance.md)
- [Dependências e licenças](docs/dependencies.md)
- [Como publicar um release](docs/release.md)
- [Registros de decisão (ADRs)](docs/adr/README.md)
- [Como contribuir](CONTRIBUTING.md)
- [Segurança](SECURITY.md)
- [Changelog](CHANGELOG.md)

## Licença

[Apache License 2.0](LICENSE).
