package com.engperf.application.ado;

import java.time.Instant;

/** Durable sync cursor: the high-water mark to fetch after, and a summary of the last run. */
public record SyncState(Instant watermark, Instant lastSyncedAt, long eventCount) {}
