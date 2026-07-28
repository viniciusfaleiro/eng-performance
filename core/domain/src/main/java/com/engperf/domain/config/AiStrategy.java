package com.engperf.domain.config;

/** How AI-assisted commits are detected: a commit trailer or an inline tag. */
public enum AiStrategy {
  TRAILER,
  TAG
}
