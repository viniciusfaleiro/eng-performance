package com.engperf.application.port.inbound;

import com.engperf.application.auth.LoginResult;

/** Inbound port: authenticate and manage the current user's own credentials. */
public interface AuthUseCase {

  LoginResult login(String email, String rawPassword);

  void changePassword(String accountId, String currentPassword, String newPassword);
}
