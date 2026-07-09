package com.engperf.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

/** JPA mapping for a dated team membership, owned by {@link PersonEntity}. */
@Embeddable
public class MembershipEmbeddable {

  @Column(name = "team_id", nullable = false)
  private String teamId;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  protected MembershipEmbeddable() {}

  public MembershipEmbeddable(String teamId, LocalDate startDate, LocalDate endDate) {
    this.teamId = teamId;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public String getTeamId() {
    return teamId;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }
}
