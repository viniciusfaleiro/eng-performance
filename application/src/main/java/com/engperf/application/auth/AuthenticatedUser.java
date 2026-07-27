package com.engperf.application.auth;

import com.engperf.domain.access.AccessScope;
import com.engperf.domain.account.UserAccount;

/** The current user's account plus the access scope resolved for them. */
public record AuthenticatedUser(UserAccount account, AccessScope scope) {}
