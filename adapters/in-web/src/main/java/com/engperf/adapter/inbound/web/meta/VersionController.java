package com.engperf.adapter.inbound.web.meta;

import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What build is actually running. Exists so a deploy can be confirmed from the UI instead of by
 * guessing whether the browser is showing a cached page — the SPA is served from the jar with a
 * stable filename, so a stale {@code index.html} looks exactly like a missing feature.
 *
 * <p>Public on purpose: the login screen is the first place you want to read it, and the answer
 * carries nothing an unauthenticated caller could exploit.
 */
@RestController
public class VersionController {

  /**
   * Absent whenever the app runs without {@code build-info.properties} — tests and IDE runs. The
   * endpoint still answers, marked {@code dev}, rather than failing to start.
   */
  private final ObjectProvider<BuildProperties> build;

  public VersionController(ObjectProvider<BuildProperties> build) {
    this.build = build;
  }

  @GetMapping("/api/version")
  public VersionView version() {
    BuildProperties props = build.getIfAvailable();
    if (props == null) {
      return new VersionView("dev", "dev", null);
    }
    Instant time = props.getTime();
    return new VersionView(
        props.getVersion(),
        props.get("commit") == null ? "unknown" : props.get("commit"),
        time == null ? null : time.toString());
  }

  /** The running build: artifact version, git commit it was built from, and when. */
  public record VersionView(String version, String commit, String builtAt) {}
}
