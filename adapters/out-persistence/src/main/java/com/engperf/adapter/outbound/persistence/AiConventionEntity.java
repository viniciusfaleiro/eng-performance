package com.engperf.adapter.outbound.persistence;

import com.engperf.domain.config.AiStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping for the singleton AI-detection convention (id = "default"). */
@Entity
@Table(name = "ai_convention")
public class AiConventionEntity {

  @Id private String id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AiStrategy strategy;

  private String trailer;
  private String tag;
  private String regex;

  @Column(name = "case_sensitive", nullable = false)
  private boolean caseSensitive;

  protected AiConventionEntity() {}

  public AiConventionEntity(
      String id,
      AiStrategy strategy,
      String trailer,
      String tag,
      String regex,
      boolean caseSensitive) {
    this.id = id;
    this.strategy = strategy;
    this.trailer = trailer;
    this.tag = tag;
    this.regex = regex;
    this.caseSensitive = caseSensitive;
  }

  public AiStrategy getStrategy() {
    return strategy;
  }

  public String getTrailer() {
    return trailer;
  }

  public String getTag() {
    return tag;
  }

  public String getRegex() {
    return regex;
  }

  public boolean isCaseSensitive() {
    return caseSensitive;
  }
}
