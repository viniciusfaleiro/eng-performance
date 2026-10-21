## 1. Escopo

- [x] 1.1 `AccessScope`: direito próprio de ler o agregado da organização, separado de `orgWide` (que segue significando "qualquer nó"), com o javadoc dizendo a diferença.
- [x] 1.2 `AccessPolicy`: o membro recebe o time, **a vertical desse time** e o direito ao agregado da organização; gestor, admin e exec inalterados.
- [x] 1.3 Testes de domínio: membro vê próprio/time/vertical/organização; não vê time irmão, vertical irmã nem pessoa alheia; gestor de time e de vertical continuam exatamente como antes.

## 2. A lateralidade dentro das telas comparativas

- [x] 2.1 Conferir que heatmap e scatter continuam filtrando linhas pelo escopo e cobrir com teste o caso novo: membro abrindo a organização vê as próprias linhas, não as dos irmãos.

## 3. Fechamento

- [x] 3.1 Conferir no app com a conta de colaborador: próprio painel, time, vertical e organização respondem 200; outro time, outra vertical e outro dev respondem 403.
- [x] 3.2 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir **BUILD SUCCESSFUL**.
