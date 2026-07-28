package com.engperf.domain.config;

import java.time.Instant;

/**
 * Azure DevOps integration marker. There is no org URL and no PAT: teams span many organizations,
 * ingestion is driven by per-repository registration (each repo carries its own organization and
 * production-stage rule), and auth is interactive device-code. This only records whether a real
 * sync has run — so the dev seeder can stand down once ingestion is live.
 *
 * @param connected whether a real Azure DevOps sync has been run
 * @param lastValidatedAt when the last sync completed, or {@code null}
 */
public record AdoIntegration(boolean connected, Instant lastValidatedAt) {}
