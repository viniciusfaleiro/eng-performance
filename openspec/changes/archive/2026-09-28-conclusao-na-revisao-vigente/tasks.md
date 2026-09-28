## 1. Correção

- [x] 1.1 `AdoMapper.workItem` lê `System.ChangedDate` e o passa a `WorkItemFlow.of`.
- [x] 1.2 `WorkItemFlow.collectStates` data a transição da revisão vigente pelo `ChangedDate`, em vez
      de descartá-la; mantém o descarte para `revisedDate` ilegível e para `ChangedDate` ausente ou
      futuro.

## 2. Testes

- [x] 2.1 Novo `WorkItemFlowTest` reproduzindo o caso real (transição terminal na revisão vigente).
- [x] 2.2 Casos de guarda: sem `ChangedDate`, `ChangedDate` futuro, `revisedDate` ilegível, e
      transição terminal já substituída (mantém a própria data).
- [x] 2.3 Rodar `./gradlew spotlessApply && ./gradlew build` e garantir BUILD SUCCESSFUL.
