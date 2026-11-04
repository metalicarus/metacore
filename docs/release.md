# Como publicar um release

O release tem duas partes: a biblioteca no Maven Central e o executável da
CLI no GitHub. Nada é publicado automaticamente: o Central Portal e o
release do GitHub ficam esperando a sua confirmação.

## Preparação (uma vez)

1. **Conta no Central Portal**: crie em <https://central.sonatype.com> e
   registre o namespace `br.com.genosha`. O portal mostra um código para
   publicar como registro TXT no DNS de `genosha.com.br`; depois da
   verificação, o registro pode ser removido, mas o domínio precisa
   continuar ativo.
2. **Token de publicação**: em *View Account → Generate User Token*, e
   no `~/.m2/settings.xml` (o modelo que o portal mostra vem com
   `<id>${server}</id>`; troque por `central`, o `publishingServerId` do
   perfil `release`):

   ```xml
   <servers>
     <server>
       <id>central</id>
       <username>TOKEN_USERNAME</username>
       <password>TOKEN_PASSWORD</password>
     </server>
   </servers>
   ```

   Se o seu `settings.xml` tiver um `<mirror>` corporativo com
   `mirrorOf` `*`, use um arquivo separado para o MetaCore
   (`mvn -s ~/.m2/settings-metacore.xml ...`): o mirror não conhece os
   módulos do projeto. Como o arquivo guarda o token, deixe-o com
   `chmod 600`.
3. **Chave GPG** para assinar os artefatos:

   ```bash
   gpg --full-generate-key
   gpg --keyserver keyserver.ubuntu.com --send-keys <ID_DA_CHAVE>
   gpg --keyserver keys.openpgp.org --send-keys <ID_DA_CHAVE>
   ```

   O keys.openpgp.org guarda a chave sem identidade até o e-mail ser
   confirmado, e o `--send-keys` não dispara a confirmação: peça em
   <https://keys.openpgp.org/manage>. O Central aceita a chave do
   keyserver.ubuntu.com, que já publica a identidade.
4. **Java 25** como Java padrão (`update-alternatives`) ou no `JAVA_HOME`:
   o `.mvn/jvm.config` usa `--add-exports`, que o Java 8 recusa.

## Checklist antes do release

- [x] Spool de streams sob demanda (ADR 0015), exigido pela Fase 0.
- [ ] CI verde em Java 21 e 25.
- [ ] `mvn -Psecurity verify` sem vulnerabilidades com CVSS 7 ou mais.
- [ ] Fuzzing de 10 min por alvo sem falhas
      (`JAZZER_FUZZ=1 mvn test -pl <módulo> -am -Dtest=<Alvo>FuzzTest
      -Dsurefire.failIfNoSpecifiedTests=false -Djazzer.max_duration=10m`).
- [ ] `CHANGELOG.md` com a data do release.

## Passo a passo

```bash
# 1. Versão de release em todos os módulos (inclusive o BOM e o
#    metacore-comparison, que só entra no reator com -Pcomparison)
mvn versions:set -Pcomparison -DnewVersion=0.1.0 -DprocessAllModules=true -DgenerateBackupPoms=false

# 2. Build, assinatura e envio ao Central Portal (não publica ainda);
#    termina quando o portal valida o pacote
mvn -s ~/.m2/settings-metacore.xml -Prelease clean deploy

# 3. Commit e tag; a tag dispara o workflow que gera os executáveis
git commit -am "Lança 0.1.0"
git tag -a v0.1.0 -m "MetaCore 0.1.0"
git push origin main v0.1.0
```

4. No Central Portal, em *Deployments*, confira o pacote validado e
   clique em *Publish*. Não tem volta: a versão publicada não pode ser
   substituída nem apagada. Ela aparece no Maven Central em até algumas
   horas.
5. No GitHub, o workflow `release` cria um release em rascunho com os
   pacotes da CLI para Linux, macOS e Windows. Revise as notas (copie a
   seção do `CHANGELOG.md`) e publique.
6. Volte para a próxima versão de desenvolvimento:

   ```bash
   mvn versions:set -Pcomparison -DnewVersion=0.2.0-SNAPSHOT -DprocessAllModules=true -DgenerateBackupPoms=false
   git commit -am "Inicia 0.2.0-SNAPSHOT"
   git push
   ```

## O que é publicado

| Artefato | Central | GitHub |
|---|---|---|
| `metacore-parent` (POM pai, exigido pelos POMs dos módulos) | sim | |
| `metacore-bom` | sim | |
| `metacore-core` | sim | |
| `metacore-testkit` | sim | |
| `metacore-format-image` (e o `test-jar` com as fixtures) | sim | |
| `metacore-format-pdf` (e o `test-jar`) | sim | |
| `metacore-cli` (JAR) | sim | |
| Executável da CLI (jpackage) | | sim |
| `metacore-examples`, `metacore-benchmarks` | não | |

## Observações

- O jpackage no macOS recusa versões que começam com zero; o pacote do
  macOS usa `1.0.0` no `Info.plist`. A versão real aparece em
  `metacore --version`.
- Na 0.1.0, o job do macOS falhou duas vezes (na primeira, o GitHub não
  conseguiu um runner macOS em 15 minutos) e o release saiu só com os
  pacotes de Linux e Windows. O perfil `dist-mac` ainda não foi testado.
- A versão do jpackage (`dist.version` no `metacore-cli/pom.xml`) é fixa:
  atualize-a a cada release.
- O push da tag dispara um workflow; o token do GitHub usado no push
  precisa do escopo `workflow`.
