package com.engperf.application.metrics;

/**
 * One structure in the AI-adoption ranking (a vertical or a team — never a person), with its AI
 * adoption (% of its people who used AI in the period).
 */
public record AdoptionRank(String nodeId, String label, double adoption) {}
