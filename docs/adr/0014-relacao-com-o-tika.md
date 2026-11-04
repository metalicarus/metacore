# 0014. Relação com o Apache Tika (resultado da Fase 0)

- Estado: Aceito
- Data: 2026-11-03

## Contexto

O blueprint (seção 1.4) definiu quatro hipóteses de diferenciação em
relação ao Apache Tika e um critério: seguir como projeto independente
se ao menos H1 (valores tipados) e H2 (origem por campo) se
confirmassem. A Fase 0 foi feita depois do MVP, com o MetaCore pronto
para comparar. O relatório completo está em `docs/fase0-tika.md`.

## Decisão

- **O MetaCore segue como projeto independente.** H1 e H3 se
  confirmaram com folga, H2 em parte e H4 para arquivos.
- **Posicionamento**: metadados tipados, com origem e estado explícito,
  para poucos formatos e com poucas dependências. O MetaCore não se
  apresenta como substituto do Tika; o README diz quando usar cada um.
- **O spool de streams será refeito antes da publicação do 0.1.0.**
  Hoje ele copia o stream inteiro antes da análise, o que torna o
  MetaCore de 1,8 a 17 vezes mais lento que o Tika em streams grandes,
  enquanto é de 2 a 8 vezes mais rápido com arquivos. A correção mexe
  no contrato de tamanho de `InputSource` e `FileInfo`, então precisa
  entrar antes de a API ser publicada.
- **O módulo `metacore-comparison` fica no repositório**, fora da build
  padrão (perfil `comparison`), para repetir a comparação a cada versão
  relevante de qualquer um dos dois projetos.
- Avaliar depois do 0.1 um módulo opcional de detecção baseado no
  `tika-core`.

## Consequências

- A publicação do 0.1.0 fica condicionada à correção do spool.
- A comparação é reproduzível e versionada; mudanças no Tika que
  afetem as conclusões aparecem ao rodá-la de novo.
