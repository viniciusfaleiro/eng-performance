package com.engperf.adapter.outbound.ado;

import static org.assertj.core.api.Assertions.assertThat;

import com.engperf.domain.config.ImpedimentConvention;
import com.engperf.domain.metrics.Interval;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * O board do time não tem coluna de bloqueio: o sinal é o campo e/ou a tag. Estes testes fixam o
 * que conta como impedido, de quando a quando, e de quem é o motivo — tudo lido do mesmo histórico
 * de {@code updates} que as transições de estado já usam, sem nenhuma chamada a mais.
 */
class ImpedimentTimelineTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
  private static final ImpedimentConvention CONVENTION =
      new ImpedimentConvention("Impediment", "Impedimento:");
  private static final List<Interval> EVERYTHING =
      List.of(new Interval(T0.toEpochMilli(), NOW.toEpochMilli()));

  @Test
  void theFieldMarksTimeAsImpededUntilItIsCleared() {
    ImpedimentTimeline t =
        timeline(field(h(0), "", "Yes"), field(h(10), "Yes", "None"), CONVENTION);

    assertThat(t.isEmpty()).isFalse();
    assertThat(t.overlapMs(T0, NOW)).isEqualTo(10 * 3_600_000L);
    assertThat(t.hoursByReason(EVERYTHING))
        .containsExactly(Map.entry(ImpedimentTimeline.NO_REASON, 10.0));
  }

  @Test
  void theTagMarksTimeAndNamesTheReason() {
    ImpedimentTimeline t =
        timeline(
            tags(h(2), "", "Impedimento:infra"), tags(h(6), "Impedimento:infra", ""), CONVENTION);

    assertThat(t.overlapMs(T0, NOW)).isEqualTo(4 * 3_600_000L);
    assertThat(t.hoursByReason(EVERYTHING)).containsExactly(Map.entry("infra", 4.0));
  }

  /**
   * Dois sinais ao mesmo tempo não impedem duas vezes: a união não pode somar mais que o relógio.
   */
  @Test
  void bothSignalsOverlappingCountOnce() {
    ImpedimentTimeline t =
        timeline(
            both(h(0), "", "Yes", "", "Impedimento:infra"),
            both(h(5), "Yes", "None", "Impedimento:infra", ""),
            CONVENTION);

    assertThat(t.overlapMs(T0, NOW)).isEqualTo(5 * 3_600_000L);
    assertThat(t.hoursByReason(EVERYTHING)).containsExactly(Map.entry("infra", 5.0));
  }

  @Test
  void severalBlocksOfTheSameReasonAddUp() {
    ImpedimentTimeline t =
        timeline(
            tags(h(0), "", "Impedimento:infra"),
            tags(h(2), "Impedimento:infra", ""),
            tags(h(20), "", "Impedimento:infra"),
            tags(h(23), "Impedimento:infra", ""),
            CONVENTION);

    assertThat(t.hoursByReason(EVERYTHING)).containsExactly(Map.entry("infra", 5.0));
    assertThat(t.blocked()).hasSize(2);
  }

  @Test
  void twoDifferentReasonsAreReportedApart() {
    ImpedimentTimeline t =
        timeline(
            tags(h(0), "", "Impedimento:infra"),
            tags(h(4), "Impedimento:infra", "Impedimento:aprovação"),
            tags(h(6), "Impedimento:aprovação", ""),
            CONVENTION);

    assertThat(t.hoursByReason(EVERYTHING))
        .containsOnly(Map.entry("infra", 4.0), Map.entry("aprovação", 2.0));
  }

  @Test
  void theSameReasonWrittenDifferentlyIsOneReason() {
    ImpedimentTimeline t =
        timeline(
            tags(h(0), "", "Impedimento:Infra"),
            tags(h(1), "Impedimento:Infra", "impedimento: infra"),
            tags(h(3), "impedimento: infra", ""),
            CONVENTION);

    assertThat(t.hoursByReason(EVERYTHING)).containsExactly(Map.entry("infra", 3.0));
    assertThat(t.label("infra")).isEqualTo("Infra"); // a primeira grafia vista
  }

  @Test
  void aTagWithOnlyThePrefixMarksTimeWithoutNamingAReason() {
    ImpedimentTimeline t =
        timeline(tags(h(0), "", "Impedimento:"), tags(h(3), "Impedimento:", ""), CONVENTION);

    assertThat(t.hoursByReason(EVERYTHING))
        .containsExactly(Map.entry(ImpedimentTimeline.NO_REASON, 3.0));
  }

  @Test
  void otherTagsAreIgnored() {
    ImpedimentTimeline t =
        timeline(
            tags(h(0), "", "urgente; squad-a"), tags(h(3), "urgente; squad-a", ""), CONVENTION);

    assertThat(t.isEmpty()).isTrue();
  }

  @Test
  void aFieldSetToNoneOrBlankIsNotImpeded() {
    assertThat(timeline(field(h(0), "", "None"), field(h(3), "None", ""), CONVENTION).isEmpty())
        .isTrue();
    assertThat(ImpedimentTimeline.set("none")).isFalse();
    assertThat(ImpedimentTimeline.set("  ")).isFalse();
    assertThat(ImpedimentTimeline.set("Dependência externa")).isTrue();
  }

  /**
   * O sinal já ligado quando o histórico começa foi ligado antes de alguém editar: ler só o {@code
   * newValue} dataria o impedimento no instante em que ele foi <em>retirado</em>.
   */
  @Test
  void aSignalAlreadyInForceCountsFromTheStartOfTheHistory() {
    ImpedimentTimeline t =
        timeline(
            tags(h(0), "Impedimento:infra", "Impedimento:infra"),
            tags(h(8), "Impedimento:infra", ""),
            CONVENTION);

    assertThat(t.hoursByReason(EVERYTHING)).containsExactly(Map.entry("infra", 8.0));
  }

  /** Sinal ainda em vigor na última revisão corre até agora — a mesma ponta aberta do estado. */
  @Test
  void aSignalStillInForceRunsToNow() {
    ImpedimentTimeline t = timeline(tags(h(0), "", "Impedimento:infra"), CONVENTION);

    assertThat(t.overlapMs(T0, NOW)).isEqualTo(9 * 24 * 3_600_000L);
  }

  /** A revisão vigente vem com o sentinela {@code 9999-01-01} e é datada pelo ChangedDate. */
  @Test
  void theOpenRevisionIsDatedByChangedDate() {
    JsonNode updates =
        updates(
            tags(h(0), "", "Impedimento:infra"),
            raw(
                "9999-01-01T00:00:00Z",
                "\"System.Tags\":{\"oldValue\":\"Impedimento:infra\",\"newValue\":\"\"}"));

    ImpedimentTimeline t =
        ImpedimentTimeline.of("x", updates, CONVENTION, Instant.parse("2026-09-01T05:00:00Z"), NOW);

    assertThat(t.overlapMs(T0, NOW)).isEqualTo(5 * 3_600_000L);
  }

  @Test
  void anEmptyConventionRecognisesNothing() {
    ImpedimentTimeline t =
        timeline(
            both(h(0), "", "Yes", "", "Impedimento:infra"),
            both(h(5), "Yes", "None", "Impedimento:infra", ""),
            ImpedimentConvention.off());

    assertThat(t.isEmpty()).isTrue();
    assertThat(t.overlapMs(T0, NOW)).isZero();
    assertThat(t.hoursByReason(EVERYTHING)).isEmpty();
  }

  /** Só a convenção da tag: o campo deixa de ser lido, e o tempo marcado só por ele desaparece. */
  @Test
  void onlyTheConfiguredSignalIsRead() {
    ImpedimentTimeline t =
        timeline(
            field(h(0), "", "Yes"),
            field(h(5), "Yes", "None"),
            new ImpedimentConvention(null, "Impedimento:"));

    assertThat(t.isEmpty()).isTrue();
  }

  /** Impedimento fora das janelas contadas (backlog, por exemplo) não entra na quebra. */
  @Test
  void onlyImpedimentInsideTheCountedWindowsIsAttributed() {
    ImpedimentTimeline t =
        timeline(
            tags(h(0), "", "Impedimento:infra"), tags(h(10), "Impedimento:infra", ""), CONVENTION);

    List<Interval> window = List.of(new Interval(h(6).toEpochMilli(), h(8).toEpochMilli()));

    assertThat(t.hoursByReason(window)).containsExactly(Map.entry("infra", 2.0));
  }

  // ---- helpers ----

  private static Instant h(int hours) {
    return T0.plusSeconds(hours * 3600L);
  }

  private static ImpedimentTimeline timeline(JsonNode a, ImpedimentConvention c) {
    return ImpedimentTimeline.of("x", updates(a), c, null, NOW);
  }

  private static ImpedimentTimeline timeline(JsonNode a, JsonNode b, ImpedimentConvention c) {
    return ImpedimentTimeline.of("x", updates(a, b), c, null, NOW);
  }

  private static ImpedimentTimeline timeline(
      JsonNode a, JsonNode b, JsonNode d, ImpedimentConvention c) {
    return ImpedimentTimeline.of("x", updates(a, b, d), c, null, NOW);
  }

  private static ImpedimentTimeline timeline(
      JsonNode a, JsonNode b, JsonNode d, JsonNode e, ImpedimentConvention c) {
    return ImpedimentTimeline.of("x", updates(a, b, d, e), c, null, NOW);
  }

  private static JsonNode field(Instant at, String from, String to) {
    return raw(at.toString(), kv("Impediment", from, to));
  }

  private static JsonNode tags(Instant at, String from, String to) {
    return raw(at.toString(), kv("System.Tags", from, to));
  }

  private static JsonNode both(
      Instant at, String fieldFrom, String fieldTo, String tagsFrom, String tagsTo) {
    return raw(
        at.toString(),
        kv("Impediment", fieldFrom, fieldTo) + "," + kv("System.Tags", tagsFrom, tagsTo));
  }

  private static String kv(String field, String from, String to) {
    return "\"" + field + "\":{\"oldValue\":\"" + from + "\",\"newValue\":\"" + to + "\"}";
  }

  private static JsonNode raw(String revisedDate, String fields) {
    return json("{\"revisedDate\":\"" + revisedDate + "\",\"fields\":{" + fields + "}}");
  }

  private static JsonNode updates(JsonNode... entries) {
    StringBuilder sb = new StringBuilder("{\"value\":[");
    for (int i = 0; i < entries.length; i++) {
      sb.append(i > 0 ? "," : "").append(entries[i]);
    }
    return json(sb.append("]}").toString());
  }

  private static JsonNode json(String raw) {
    try {
      return JSON.readTree(raw);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * E5: dez horas sob duas tags são cinco para cada, não dez para cada. Somar cada motivo inteiro
   * fazia os motivos passarem da espera do item, e o excedente sumia no truncamento do residual — a
   * quebra parecia fechar enquanto estava errada.
   */
  @Test
  void twoReasonsAtOnceSplitTheStretch() {
    ImpedimentTimeline t =
        timeline(
            tags(h(0), "", "Impedimento:infra; Impedimento:negócio"),
            tags(h(10), "Impedimento:infra; Impedimento:negócio", ""),
            CONVENTION);

    assertThat(t.hoursByReason(EVERYTHING))
        .containsOnly(Map.entry("infra", 5.0), Map.entry("negócio", 5.0));
  }

  /** Sobreposição parcial: só o trecho comum se divide. */
  @Test
  void partlyOverlappingReasonsSplitOnlyTheOverlap() {
    ImpedimentTimeline t =
        timeline(
            tags(h(0), "", "Impedimento:infra"),
            tags(h(6), "Impedimento:infra", "Impedimento:infra; Impedimento:negócio"),
            tags(h(10), "Impedimento:infra; Impedimento:negócio", ""),
            CONVENTION);

    assertThat(t.hoursByReason(EVERYTHING))
        .containsOnly(Map.entry("infra", 8.0), Map.entry("negócio", 2.0));
  }

  /** A invariante: a soma dos motivos é exatamente o tempo impedido dentro das janelas. */
  @Test
  void theReasonsOfAnItemSumToItsImpededTime() {
    ImpedimentTimeline t =
        timeline(
            both(h(0), "", "Yes", "", "Impedimento:infra"),
            tags(h(4), "Impedimento:infra", "Impedimento:infra; Impedimento:negócio"),
            both(h(9), "Yes", "None", "Impedimento:infra; Impedimento:negócio", ""),
            CONVENTION);

    double sum =
        t.hoursByReason(EVERYTHING).values().stream().mapToDouble(Double::doubleValue).sum();

    assertThat(sum).isEqualTo(t.overlapMs(T0, NOW) / 3_600_000.0);
  }
}
