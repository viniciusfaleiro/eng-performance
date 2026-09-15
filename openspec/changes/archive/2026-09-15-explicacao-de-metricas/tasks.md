## 1. Domínio — a explicação entra no modelo

- [x] 1.1 Criar o record `MetricExplanation` (`rule`, `source`, `included`, `excluded`, `example`)
      em `domain/metrics`, validando que nenhum campo é vazio.
- [x] 1.2 Adicionar a explicação como **um** componente de `MetricDefinition`, com construtor de
      conveniência que a dispensa — as fixtures de teste existentes não devem precisar reescrita.
- [x] 1.3 Testes de domínio: campo vazio é rejeitado; a definição sem explicação continua
      construível.

## 2. Conteúdo — escrever as explicações

- [x] 2.1 DORA (4): deploy_freq, lead_time, cfr, mttr.
- [x] 2.2 Fluxo (7): cycle_time, throughput, flow_lead_time, wip, flow_efficiency, pr_review_time,
      pr_size.
- [x] 2.3 Volume (2): commit_count, pr_count — deixando explícito que contam tudo, sem filtro de
      conclusão, e por isso não são comparáveis a throughput.
- [x] 2.4 IA (3): ai_share, ai_adoption, ai_impact.
- [x] 2.5 Visualizações sem métrica: tendência, heatmap comparativo, distribuição por tipo,
      cobertura, painel individual e estatísticas do ADO.
- [ ] 2.6 Revisar todos os textos com alguém que não conheça o código — o critério é a pessoa
      conseguir dizer, com as próprias palavras, o que entra e o que fica de fora.

## 3. Aplicação

- [x] 3.1 Preencher as explicações no `MetricCatalog`, cada uma adjacente à definição que descreve.
- [x] 3.2 Registro das explicações de visualização, por chave, no mesmo formato.
- [x] 3.3 Teste de completude: toda métrica do catálogo tem explicação completa, e nenhuma
      explicação órfã — a falha deve nomear a chave culpada, não só falhar.

## 4. Web — contrato

- [x] 4.1 Expor a explicação em `CatalogItemDto` (`/api/metrics/catalog`).
- [x] 4.2 Endpoint ou campo que sirva as explicações de visualização.
- [x] 4.3 Documentar em `docs/api/openapi.yaml`.

## 5. UI

- [x] 5.1 Componente de ícone "i": ao lado do título, cor secundária, contraste no hover,
      `aria-label` com o nome da métrica, acionável por teclado.
- [x] 5.2 Modal de explicação com hierarquia visual entre regra, fonte, inclusões/exclusões e
      exemplo — reusando o design system do protótipo, sem CSS novo inventado.
- [x] 5.3 Colocar o ícone em: cards dos três dashboards, gráficos de tendência, heatmap,
      distribuição por tipo, cobertura e painel individual.
- [x] 5.4 Fazer o drawer ler a explicação do catálogo e **remover o mapa `DEFS`** do `index.html`.
- [x] 5.5 Garantir que o clique no card continua abrindo o drawer e que o clique no "i" não o
      dispara.

## 6. Verificação

- [x] 6.1 Com Chrome headless: abrir o modal em uma métrica de cada grupo (DORA, Fluxo, volume, IA)
      e conferir que regra, fonte e exemplo aparecem preenchidos.
- [x] 6.2 Conferir que drawer e modal mostram o mesmo texto para a mesma métrica.
- [x] 6.3 Navegar por teclado até o ícone e abrir o modal sem mouse.
- [x] 6.4 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
