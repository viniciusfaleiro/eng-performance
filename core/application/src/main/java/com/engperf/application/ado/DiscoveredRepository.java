package com.engperf.application.ado;

/**
 * A repository found in an Azure DevOps project during discovery, with a best-effort guess at its
 * production-stage rule ({@code null} when none or more than one candidate stage name was found —
 * see {@code openspec/specs/repo-discovery}).
 */
public record DiscoveredRepository(String key, String suggestedProductionStage) {}
