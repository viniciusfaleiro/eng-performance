package com.engperf.application.auth;

/** Outcome of a successful login: the session token and the authenticated identity. */
public record LoginResult(String token, AuthPrincipal principal) {}
