package com.engperf.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** Os intervalos em que um work item ficou em progresso — e onde eles têm buracos. */
class InProgressSpansTest {

  private static final Instant READ = Instant.parse("2026-06-30T12:00:00Z");

  /**
   * O caso que proíbe colapsar tudo em "primeiro começo até último fim": trabalhado de 1 a 3 e
   * retomado de 20 a 22, o item não estava em progresso no dia 15. Pelos limites externos ele
   * estaria, e um período de 10 a 15 contaria um item que ninguém tocou.
   */
  @Test
  void aGapBetweenIntervalsIsNotInProgress() {
    InProgressSpans s = spans(item(span(1, 3) + "," + span(20, 22), false));

    assertThat(s.overlaps(day(1), day(4))).isTrue();
    assertThat(s.overlaps(day(20), day(23))).isTrue();
    assertThat(s.overlaps(day(10), day(16))).isFalse();
    assertThat(s.within(day(10), day(16))).isEmpty();
  }

  @Test
  void overlapIsHalfOpenAtBothEnds() {
    InProgressSpans s = spans(item(span(10, 20), false));

    assertThat(s.overlaps(day(20), day(30))).isFalse(); // começa quando o intervalo termina
    assertThat(s.overlaps(day(0), day(10))).isFalse(); // termina quando o intervalo começa
    assertThat(s.overlaps(day(19), day(21))).isTrue();
  }

  /** Sem isto, a leitura diária relata a frescura do sincronizador, não o estado do board. */
  @Test
  void anOpenItemReachesTheClockOfTheReading() {
    Instant ingested = Instant.parse("2026-06-28T09:00:00Z");
    RawEvent open =
        item(ingested.minusSeconds(3600).toEpochMilli() + ":" + ingested.toEpochMilli(), true);

    InProgressSpans s = InProgressSpans.of(open, READ);
    assertThat(s.overlaps(READ.minusSeconds(60), READ)).isTrue();
    // 1 h antes da ingestão + as ~51 h até a leitura
    assertThat(s.hoursWithin(Instant.parse("2026-06-01T00:00:00Z"), READ))
        .isCloseTo(52.0, Offset.offset(0.1));
  }

  /** O item concluído fica onde a ingestão o deixou: esticá-lo inventaria trabalho. */
  @Test
  void aClosedItemIsNotExtended() {
    RawEvent closed = item("10:20", false);

    assertThat(InProgressSpans.of(closed, READ).intervals())
        .containsExactly(new InProgressSpans.Interval(10, 20));
  }

  /** Um item já esticado além da leitura (relógio fixado para trás) não encurta. */
  @Test
  void anOpenItemAlreadyPastTheReadingIsLeftAlone() {
    long after = READ.plusSeconds(7200).toEpochMilli();
    RawEvent open = item((after - 3600_000) + ":" + after, true);

    assertThat(InProgressSpans.of(open, READ).intervals())
        .containsExactly(new InProgressSpans.Interval(after - 3600_000, after));
  }

  /**
   * Sem histórico aproveitável não há intervalo — é "sem dado", e quem chama não pode contar zero.
   */
  @Test
  void anItemWithoutHistoryHasNoIntervals() {
    assertThat(InProgressSpans.of(item(null, false), READ).isEmpty()).isTrue();
    assertThat(InProgressSpans.of(item("", true), READ).isEmpty()).isTrue();
    assertThat(InProgressSpans.of(item("   ", false), READ).isEmpty()).isTrue();
  }

  /** Uma parte ilegível não invalida as outras: a leitura da tela não cai por um dado torto. */
  @Test
  void anUnreadablePartIsSkippedWithoutLosingTheRest() {
    InProgressSpans s = spans(item("10:20,lixo,30:xx,,40:50", false));

    assertThat(s.intervals())
        .containsExactly(
            new InProgressSpans.Interval(10, 20), new InProgressSpans.Interval(40, 50));
  }

  @Test
  void invertedAndEmptyIntervalsAreDropped() {
    assertThat(spans(item("20:10,30:30,40:50", false)).intervals())
        .containsExactly(new InProgressSpans.Interval(40, 50));
  }

  @Test
  void hoursAreClippedToTheWindow() {
    InProgressSpans s = spans(item(span(5, 15), false));

    assertThat(s.hoursWithin(day(10), day(12))).isCloseTo(48.0, Offset.offset(1e-9));
    assertThat(s.within(day(10), day(12)))
        .containsExactly(
            new InProgressSpans.Interval(day(10).toEpochMilli(), day(12).toEpochMilli()));
  }

  private static InProgressSpans spans(RawEvent e) {
    return InProgressSpans.of(e, READ);
  }

  /** O trecho `início:fim` do formato, em millis de época, para os dias 1..30 de junho de 2026. */
  private static String span(int fromDay, int toDay) {
    return day(fromDay).toEpochMilli() + ":" + day(toDay).toEpochMilli();
  }

  private static Instant day(int d) {
    return Instant.parse("2026-06-01T00:00:00Z").plusSeconds(d * 86_400L);
  }

  private static RawEvent item(String spans, boolean open) {
    Map<String, String> detail = new HashMap<>();
    if (spans != null) {
      detail.put("spans", spans);
    }
    if (open) {
      detail.put("in_progress", "1");
    }
    return new RawEvent(
        "wi:1",
        EventType.WORKITEM,
        Instant.parse("2026-06-10T10:00:00Z"),
        null,
        "id-ana",
        null,
        null,
        false,
        Map.copyOf(detail));
  }
}
