package com.engperf.bootstrap.config;

import com.engperf.domain.metrics.EventType;
import com.engperf.domain.metrics.RawEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The seeded work items: one completed task per person per day, plus a weekly User Story that only
 * groups two child Tasks.
 *
 * <p>Split out of {@link EventFixtures} when that file outgrew the length limit, along the seam
 * that actually grew: the work item is the only fixture that has to carry a hierarchy, an
 * in-progress interval and a completion, because the metrics read from it are the only ones that
 * distinguish leaf from container, interval from instant, and finished from running.
 *
 * <p>Two shapes on purpose, so the screen shows both populations side by side: the daily task is
 * completed with its phases — without any completed item, throughput, cycle time, lead time, the
 * phases and flow efficiency all read zero — and the most recent weekly trio is left open, so WIP
 * has work in progress to count. Older trios are completed, because leaving every one of them open
 * would pile up six months of "in progress" and report more than a hundred open items for one team.
 */
final class WorkItemFixtures {

  private WorkItemFixtures() {}

  private static final String[] WI_TITLES = {
    "Ajustar validação de CPF no checkout",
    "Retry no gateway de pagamento",
    "Extrair serviço de antifraude",
    "Cobrir cenários de timeout",
    "Atualizar dependências do core",
    "Reduzir N+1 na listagem"
  };

  static RawEvent workitem(String identity, LocalDate day) {
    String d = day.toString();
    double wip = 3 + EventFixtures.pick(identity + "|wip|" + d, 8);
    double hours = 2 + EventFixtures.pick(identity + "|whrs|" + d, 8); // 2..9h
    Map<String, String> detail = new java.util.HashMap<>();
    detail.put(
        "type",
        EventFixtures.WORK_TYPES[
            EventFixtures.pick(identity + "|wtype|" + d, EventFixtures.WORK_TYPES.length)]);
    detail.put("hours", Double.toString(hours));
    detail.put("ado_type", "Task");
    workItemChrome(detail, identity, day, 0, hours);
    // Concluído, com as fases: sem isso o fixture não produz nenhum item concluído, e throughput,
    // cycle time, lead time, as fases e a eficiência de fluxo — todas medidas sobre concluídos —
    // leem zero no protótipo. Um dashboard inteiro em zero não é demonstrável nem conferível.
    completion(detail, identity, day, hours);
    return new RawEvent(
        EventFixtures.id("wip", identity, day, 0),
        EventType.WORKITEM,
        EventFixtures.at(day),
        null,
        identity,
        wip,
        null,
        false,
        detail);
  }

  /**
   * Uma User Story aberta o dia inteiro com duas Tasks filhas dentro dela — a forma exata do
   * problema que a regra de trabalho folha resolve: sem a regra, a mãe disputa cada hora com as
   * filhas e reduz a fatia de cada uma; com ela, as horas ficam com as filhas.
   */
  static List<RawEvent> containerWithChildren(String identity, LocalDate day) {
    String parentId = EventFixtures.id("wip-us", identity, day, 0);
    String type =
        EventFixtures.WORK_TYPES[
            EventFixtures.pick(identity + "|ctype|" + day, EventFixtures.WORK_TYPES.length)];
    List<RawEvent> out = new java.util.ArrayList<>();
    Map<String, String> parent = new java.util.HashMap<>();
    parent.put("type", type);
    parent.put("ado_type", "User Story");
    parent.put("hours", "9.0");
    // Só a trinca das duas últimas semanas fica em aberto. Deixar toda trinca aberta acumularia
    // seis meses de trabalho "em curso" e o WIP passaria de uma centena de itens para um time — um
    // número que ninguém acredita, e um WIP inacreditável na demonstração não demonstra nada.
    boolean stillOpen = !day.isBefore(EventFixtures.TO.minusWeeks(2));
    workItemChrome(parent, identity, day, 90, 9.0);
    if (stillOpen) {
      parent.put("in_progress", "1");
    } else {
      completion(parent, identity, day, 9.0);
    }
    out.add(
        new RawEvent(
            parentId,
            EventType.WORKITEM,
            EventFixtures.at(day),
            null,
            identity,
            1.0,
            null,
            false,
            parent));
    for (int i = 0; i < 2; i++) {
      double hours = 3 + EventFixtures.pick(identity + "|chrs|" + day + i, 4); // 3..6h
      Map<String, String> child = new java.util.HashMap<>();
      child.put("type", type);
      child.put("ado_type", "Task");
      child.put("hours", Double.toString(hours));
      child.put("parent_event_id", parentId);
      workItemChrome(child, identity, day, 91 + i, hours);
      if (stillOpen) {
        child.put("in_progress", "1");
      } else {
        completion(child, identity, day, hours);
      }
      out.add(
          new RawEvent(
              EventFixtures.id("wip-task", identity, day, i),
              EventType.WORKITEM,
              EventFixtures.at(day),
              null,
              identity,
              1.0,
              null,
              false,
              child));
    }
    return out;
  }

  /**
   * Conclusão e fases. A espera é a fila antes de começar; a revisão é uma fatia do trabalho. A
   * eficiência de fluxo sai de {@code num}/{@code den}, e é por não existirem que um item sem
   * histórico caía nos defaults do motor e afundava a razão.
   */
  private static void completion(
      Map<String, String> detail, String identity, LocalDate day, double activeH) {
    double waitH = 1 + EventFixtures.pick(identity + "|wait|" + day, 20); // 1..20h de fila
    double reviewH = EventFixtures.pick(identity + "|rev|" + day, 5); // 0..4h de revisão
    detail.put("completed", "1");
    detail.put("active_h", Double.toString(activeH));
    detail.put("wait_h", Double.toString(waitH));
    detail.put("review_h", Double.toString(reviewH));
    detail.put("cycle_h", Double.toString(activeH + reviewH + waitH));
    detail.put("lead_h", Double.toString(activeH + reviewH + waitH + 24));
    detail.put("num", Double.toString(activeH + reviewH));
    detail.put("den", Double.toString(activeH + reviewH + waitH));
  }

  /**
   * Título, deep-link e a janela em andamento. Sem {@code spans} a distribuição cai no caminho
   * antigo, que não é prorrateado nem recortado pelo período; sem título e link, a lista de itens
   * abre com ids e links mortos, e uma lista que não se pode conferir não serve para conferir.
   */
  private static void workItemChrome(
      Map<String, String> detail, String identity, LocalDate day, int slot, double hours) {
    int num = 1000 + Math.abs((identity + day).hashCode() % 8000) + slot;
    detail.put(
        "summary", WI_TITLES[EventFixtures.pick(identity + "|wt|" + day + slot, WI_TITLES.length)]);
    detail.put("url", "https://dev.azure.com/minhaorg/Plataforma/_workitems/edit/" + num);
    long start = EventFixtures.at(day).toEpochMilli() + 9L * 3_600_000L; // começa às 9h
    detail.put("spans", start + ":" + (start + (long) (hours * 3_600_000L)));
  }
}
