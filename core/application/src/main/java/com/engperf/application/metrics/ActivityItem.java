package com.engperf.application.metrics;

/** One recent activity entry (a commit or PR) for the drawer, with its Azure DevOps deep-link. */
public record ActivityItem(String kind, String summary, String repo, String date, String url) {}
