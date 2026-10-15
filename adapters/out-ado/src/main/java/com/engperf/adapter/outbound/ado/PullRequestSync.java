package com.engperf.adapter.outbound.ado;

import com.engperf.domain.metrics.RawEvent;
import com.engperf.domain.metrics.WorkType;
import com.engperf.domain.structure.Repository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The pull-request pass of one repository's sync, lifted out of {@link AdoEventSource}. */
final class PullRequestSync {

  private static final String API = "api-version=7.1";

  private PullRequestSync() {}

  /**
   * The repository's completed pull requests since the watermark: one PR event each, its review
   * events, and the work types its commits inherit (filled into {@code typesByCommit}).
   *
   * @return how many pull requests were emitted
   */
  static int fetch(
      AdoRestClient client,
      Repository repo,
      String base,
      Instant since,
      String token,
      CommitComments comments,
      List<RawEvent> events,
      Map<String, Set<WorkType>> typesByCommit) {
    List<JsonNode> recent = new ArrayList<>();
    for (JsonNode pr :
        AdoEventSource.arr(
            client.get(
                base + "/pullrequests?searchCriteria.status=completed&$top=200&" + API, token))) {
      if (!AdoEventSource.before(pr.path("closedDate").asText(""), since)) {
        recent.add(pr);
      }
    }
    // Os vínculos com o board, resolvidos de uma vez para o repositório inteiro: é o que permite
    // ler PR, commit e review por tipo de trabalho. Ver PullRequestWorkTypes para por que os ids
    // vêm por PR e os tipos vêm em lote.
    PullRequestWorkTypes workTypes =
        PullRequestWorkTypes.resolve(
            client,
            AdoEventSource.normOrg(repo.organization()),
            base,
            recent.stream().map(p -> p.path("pullRequestId").asLong()).toList(),
            token,
            AdoEventSource.WORKITEM_ID_BATCH);
    for (JsonNode pr : recent) {
      // Duas chamadas por PR (não há endpoint em lote): commits, para tempo e tamanho, e o
      // histórico de votos, porque o objeto do PR guarda só o voto atual de cada revisor.
      long prId = pr.path("pullRequestId").asLong();
      JsonNode prCommits = client.get(base + "/pullrequests/" + prId + "/commits?" + API, token);
      VoteHistory votes = VoteHistory.fetch(client, base, prId, token);
      Set<WorkType> types = workTypes.of(prId);
      // O commit herda o tipo do PR que o carregou. Sem este índice, a leitura por tipo pararia no
      // PR e as métricas de IA — que vivem no commit — ficariam de fora do filtro.
      if (!types.isEmpty()) {
        for (JsonNode c : AdoEventSource.arr(prCommits)) {
          typesByCommit.put(c.path("commitId").asText(""), types);
        }
      }
      events.add(
          AdoMapper.pullRequest(
              pr,
              prCommits,
              comments.anyAi(base, prCommits, token),
              votes,
              // Memoizado por commit: a varredura de IA acima e esta contagem leem a mesma
              // resposta, então o commit é buscado uma vez e não duas.
              comments.changedFiles(base, prCommits, token),
              types));
      events.addAll(AdoMapper.reviews(pr, votes, types));
    }
    return recent.size();
  }
}
