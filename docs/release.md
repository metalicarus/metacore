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
   no `~/.m2/settings.xml`:

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
   módulos do projeto.
3. **Chave GPG** para assinar os artefatos:

   ```bash
   gpg --full-generate-key
   gpg --keyserver keys.openpgp.org --send-keys <ID_DA_CHAVE>
   ```

## Checklist antes do release

- [ ] Spool de streams sob demanda (ADR 0014): obrigatório para o 0.1.0.
- [ ] CI verde em Java 21 e 25.
- [ ] `mvn -Psecurity verify` sem vulnerabilidades com CVSS 7 ou mais.
- [ ] Fuzzing de 10 min por alvo sem falhas
      (`JAZZER_FUZZ=1 mvn test -pl <módulo> -am -Dtest=<Alvo>FuzzTest
      -Dsurefire.failIfNoSpecifiedTests=false -Djazzer.max_duration=10m`).
- [ ] `CHANGELOG.md` com a data do release.

## Passo a passo

```bash
# 1. Versão de release em todos os módulos (inclusive o BOM)
mvn versions:set -DnewVersion=0.1.0 -DprocessAllModules=true -DgenerateBackupPoms=false

# 2. Build, assinatura e envio ao Central Portal (não publica ainda)
mvn -Prelease clean deploy

# 3. Commit e tag; a tag dispara o workflow que gera os executáveis
git commit -am "Lança 0.1.0"
git tag -a v0.1.0 -m "MetaCore 0.1.0"
git push origin main v0.1.0
```

4. No Central Portal, em *Deployments*, confira o pacote validado e
   clique em *Publish*. Ele aparece no Maven Central em até algumas horas.
5. No GitHub, o workflow `release` cria um release em rascunho com os
   pacotes da CLI para Linux, macOS e Windows. Revise as notas (copie a
   seção do `CHANGELOG.md`) e publique.
6. Volte para a próxima versão de desenvolvimento:

   ```bash
   mvn versions:set -DnewVersion=0.2.0-SNAPSHOT -DprocessAllModules=true -DgenerateBackupPoms=false
   git commit -am "Inicia 0.2.0-SNAPSHOT"
   git push
   ```

## O que é publicado

| Artefato | Central | GitHub |
|---|---|---|
| `metacore-bom` | sim | |
| `metacore-core` | sim | |
| `metacore-testkit` | sim | |
| `metacore-format-image` (e o `test-jar` com as fixtures) | sim | |
| `metacore-format-pdf` | sim | |
| `metacore-cli` (JAR) | sim | |
| Executável da CLI (jpackage) | | sim |
| `metacore-examples`, `metacore-benchmarks` | não | |

## Observações

- O jpackage no macOS recusa versões que começam com zero; o pacote do
  macOS usa `1.0.0` no `Info.plist`. A versão real aparece em
  `metacore --version`.
- O workflow de release ainda não foi executado; a primeira tag é também
  o primeiro teste dele.
