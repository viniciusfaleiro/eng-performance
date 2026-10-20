## 1. O controle de paginação

- [ ] 1.1 Helper de renderização ao lado de `act-list` no `index.html`: recebe a lista e a página atual, mostra dez itens, e desenha a navegação (anterior/próxima, página X de Y, N itens no total) só quando há mais de uma página.
- [ ] 1.2 Navegação acionável por clique e por teclado, no mesmo padrão de afordância das linhas que já abrem detalhe.
- [ ] 1.3 A página corrente é estado da gaveta: fechar e reabrir volta à primeira página; trocar de filtro, período ou nó também.

## 2. Aplicar nas listas

- [ ] 2.1 `loadDrawerItems` (itens de uma métrica, incluindo a Espera).
- [ ] 2.2 `openWorkItemsDrawer` (itens de um tipo de trabalho no painel individual).
- [ ] 2.3 `openReviewsDrawer` (revisões).
- [ ] 2.4 `openActivityDrawer` (atividade recente).
- [ ] 2.5 Confirmar que `loadDrawerPeople` e a quebra por motivo **não** são paginados — não são listas de itens considerados.

## 3. Lista cortada

- [ ] 3.1 Onde a lista servida foi cortada (`WorkDistribution.ITEM_LIMIT`), a navegação diz que a lista é parcial e não oferece página além do que chegou.
- [ ] 3.2 Manter o total de itens declarado, para a lista continuar reconciliando com o número que ela abre.

## 4. Fechamento

- [ ] 4.1 Checagem no app: drawer de uma métrica com mais de dez itens abre na primeira página, navega, e a soma/contagem declarada não muda; lista curta não mostra navegação; lista cortada avisa.
- [ ] 4.2 Paridade visual: a navegação comparada ao design system do protótipo (mesmos tokens, mesma afordância).
- [ ] 4.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
