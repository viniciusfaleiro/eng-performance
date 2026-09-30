## 1. Domínio

- [x] 1.1 `Period` passa a representar um intervalo (início, fim exclusivo, frequência como
      granularidade), mantendo as fábricas de balde e a validação de fim antes do início e de
      futuro.
- [x] 1.2 `isBucket()` — as bordas coincidem com o balde da frequência. É essa pergunta, não o tipo,
      que decide o que se bifurca.
- [x] 1.3 `previous()` passa a devolver o intervalo anterior de mesma duração (para balde, dá o
      balde anterior).
- [x] 1.4 `days()` para as métricas por dia.
- [x] 1.5 Fábrica de janela móvel: últimos N dias terminando numa âncora — é um intervalo comum,
      sem conceito novo no motor.
- [x] 1.6 Testes: balde continua se comportando como hoje; intervalo livre atravessa meses; anterior
      de mesma duração; intervalo invertido e futuro recusados.

## 2. Motor

- [x] 2.1 A série passa a ser fatiada pela frequência **dentro** do intervalo, em vez de N baldes
      terminando nele.
- [x] 2.2 A fatia decorrida passa a exigir `isBucket()` além de conter hoje.
- [x] 2.3 A janela de busca de eventos passa a derivar do que a série desenha mais o intervalo
      anterior.
- [x] 2.4 `bucketDays` do DORA passa a ser a duração do intervalo.
- [x] 2.5 Testes: mediana sobre intervalo é recalculada (não é média de baldes); card de balde não
      muda; taxa por dia divide pelos dias do intervalo.

## 3. Web

- [x] 3.1 Os endpoints aceitam início e fim, além do parâmetro de período já existente; omitidos,
      nada muda.
- [x] 3.2 O endpoint de resolução de período devolve o intervalo resolvido, seu anterior e os dias —
      para o frontend parar de duplicar o divisor por frequência.
- [x] 3.3 Intervalo inválido responde 400 com detalhe legível.
- [x] 3.4 Documentar em `docs/api/openapi.yaml`.
- [x] 3.5 Testes de API: intervalo válido, invertido, futuro, e ausência de parâmetro.

## 4. UI

- [x] 4.1 O controle de período ganha o modo intervalo: dois campos de data e volta ao período de
      calendário.
- [x] 4.2 Alternador calendário/móvel para Semanal e Mensal, oculto no Diário; o rótulo do período
      diz qual leitura está ativa.
- [x] 4.3 As setas andam um balde no calendário e a duração da janela no modo móvel.
- [x] 4.4 Com intervalo ativo, o rótulo mostra as duas datas e a frequência é anunciada como
      granularidade do gráfico.
- [x] 4.5 O rótulo da comparação diz contra qual intervalo está comparando.
- [x] 4.6 O intervalo e a leitura ativa viajam na URL e é restaurado ao abrir.
- [x] 4.7 Remover o divisor por frequência duplicado no frontend, passando a usar o do servidor.

## 5. Verificação

- [x] 5.1 Com Chrome headless: escolher um intervalo que atravessa meses e conferir que cards,
      tendências, heatmap e painel individual respondem a ele.
- [x] 5.2 Conferir que a troca de frequência com intervalo ativo muda os pontos do gráfico e mantém
      o intervalo.
- [x] 5.3 Conferir o alternador: mês de calendário no dia 3 mostra 3 dias; últimos 30 dias mostram
      30; as setas andam corretamente em cada modo.
- [x] 5.4 Conferir que dia/semana/mês continuam idênticos ao de hoje, incluindo a fatia decorrida no
      período em curso.
- [x] 5.5 Copiar a URL com intervalo e restaurar em aba nova.
- [x] 5.6 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
