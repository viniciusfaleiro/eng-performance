## 1. Aplicação — detalhamento por pessoa

- [x] 1.1 Serviço que devolve, para um nó e período, as pessoas com commit no período e, para cada
      uma, commits com IA, commits totais e a proporção — ordenado por proporção.
- [x] 1.2 Reusar a mesma população e atribuição do motor (as-of-event), para a soma bater com o card.
- [x] 1.3 Testes: pessoa sem commit fica de fora; pessoa com commits e nenhum com IA aparece com
      proporção zero; a razão entre quem tem IA e o total bate com o valor de `ai_adoption`.

## 2. Web

- [x] 2.1 Endpoint do detalhamento, com nó, frequência e período, filtrado por escopo individual.
- [x] 2.2 Teste de API: gestor recebe só os próprios liderados; admin recebe todos.

## 3. UI

- [x] 3.1 O drawer escolhe o tipo de detalhamento pela agregação da métrica (contagem de entidades
      distintas → lista de pessoas), sem lista paralela de chaves.
- [x] 3.2 Renderizar as duas pontas — maior e menor uso — com commits com IA / commits totais por
      pessoa.
- [x] 3.3 Estado vazio honesto: sem ninguém com commit no período, dizer isso em vez de listas
      vazias.

## 4. Verificação

- [x] 4.1 Com Chrome headless: abrir o drawer de adoção na visão geral, numa vertical e num time, e
      conferir que a população muda com o nó.
- [x] 4.2 Conferir que a soma das pessoas com IA sobre o total bate com o percentual do card.
- [x] 4.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
