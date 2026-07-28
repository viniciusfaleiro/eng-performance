package com.engperf.application.metrics;

/**
 * A person's code-review contribution: comments made, approvals and rejections given (as the
 * reviewer), and reviews given vs received (received = reviews on the person's own PRs).
 */
public record ReviewStats(
    int commentsGiven,
    int approvalsGiven,
    int rejectionsGiven,
    int reviewsGiven,
    int reviewsReceived) {}
