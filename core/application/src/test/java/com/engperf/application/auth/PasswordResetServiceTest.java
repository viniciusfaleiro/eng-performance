package com.engperf.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.PasswordResetTokenPort;
import com.engperf.application.port.outbound.PlatformConfigPort;
import com.engperf.application.port.outbound.SecureTokenGenerator;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.PasswordResetToken;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.config.AdoIntegration;
import com.engperf.domain.config.AiConvention;
import com.engperf.domain.config.AiStrategy;
import com.engperf.domain.config.MailTransport;
import com.engperf.domain.config.SmtpSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordResetServiceTest {

  private FakeAccounts accounts;
  private FakeTokens tokens;
  private FakeEmail email;
  private MutableClock clock;
  private PasswordResetService service;

  private static final PasswordHasher HASHER =
      new PasswordHasher() {
        @Override
        public String hash(String raw) {
          return "h:" + raw;
        }

        @Override
        public boolean matches(String raw, String hash) {
          return hash != null && hash.equals("h:" + raw);
        }
      };

  private static final class SeqTokenGenerator implements SecureTokenGenerator {
    private int seq;

    @Override
    public String generate() {
      return "raw-" + (++seq);
    }

    @Override
    public String hash(String rawToken) {
      return "hash:" + rawToken;
    }
  }

  @BeforeEach
  void setUp() {
    accounts = new FakeAccounts();
    tokens = new FakeTokens();
    email = new FakeEmail();
    clock = new MutableClock(Instant.parse("2026-06-30T12:00:00Z"));
    service =
        new PasswordResetService(
            accounts, tokens, HASHER, new SeqTokenGenerator(), email, new FakeConfig(), clock);
    accounts.save(
        new UserAccount(
            "u:ana", "Ana", "ana@x.com", Role.MANAGER, AccountStatus.ACTIVE, "p:ana", "h:secret"));
    accounts.save(
        new UserAccount(
            "u:ex", "Ex", "ex@x.com", Role.CONTRIBUTOR, AccountStatus.DISABLED, null, "h:secret"));
  }

  @Test
  void requestResetSendsEmailWithLinkForActiveAccount() {
    service.requestReset("ANA@x.com");
    assertThat(email.to).isEqualTo("ana@x.com");
    assertThat(email.body).contains("https://app.example.com/?reset=raw-1");
    assertThat(tokens.all()).hasSize(1);
    assertThat(tokens.all().get(0).tokenHash()).isEqualTo("hash:raw-1");
  }

  @Test
  void requestResetIsANoOpForUnknownAndDisabledAccounts() {
    service.requestReset("nobody@x.com");
    service.requestReset("ex@x.com");
    assertThat(email.to).isNull();
    assertThat(tokens.all()).isEmpty();
  }

  @Test
  void newRequestSupersedesPendingToken() {
    service.requestReset("ana@x.com");
    String firstHash = tokens.all().get(0).tokenHash();
    service.requestReset("ana@x.com");

    assertThatExceptionOfType(InvalidResetTokenException.class)
        .isThrownBy(
            () -> service.confirmReset(firstHash.substring("hash:".length()), "novaSenha1"));
  }

  @Test
  void requestResetStopsAfterRateLimit() {
    for (int i = 0; i < 5; i++) {
      service.requestReset("ana@x.com");
    }
    assertThat(tokens.issuedCountFor("u:ana")).isEqualTo(PasswordResetService.RATE_LIMIT);
  }

  @Test
  void confirmResetChangesPasswordAndConsumesToken() {
    service.requestReset("ana@x.com");
    service.confirmReset("raw-1", "novaSenha1");

    assertThat(accounts.findById("u:ana"))
        .get()
        .extracting(UserAccount::passwordHash)
        .isEqualTo("h:novaSenha1");
    assertThat(tokens.all().get(0).isConsumed()).isTrue();
  }

  @Test
  void confirmResetRejectsReplay() {
    service.requestReset("ana@x.com");
    service.confirmReset("raw-1", "novaSenha1");

    assertThatExceptionOfType(InvalidResetTokenException.class)
        .isThrownBy(() -> service.confirmReset("raw-1", "outraSenha"));
  }

  @Test
  void confirmResetRejectsExpiredToken() {
    service.requestReset("ana@x.com");
    clock.advance(PasswordResetService.TOKEN_TTL.plusMinutes(1));

    assertThatExceptionOfType(InvalidResetTokenException.class)
        .isThrownBy(() -> service.confirmReset("raw-1", "novaSenha1"));
  }

  @Test
  void confirmResetRejectsUnknownToken() {
    assertThatExceptionOfType(InvalidResetTokenException.class)
        .isThrownBy(() -> service.confirmReset("never-issued", "novaSenha1"));
  }

  @Test
  void confirmResetRejectsBlankPassword() {
    service.requestReset("ana@x.com");
    assertThatIllegalArgumentException().isThrownBy(() -> service.confirmReset("raw-1", " "));
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(java.time.Duration by) {
      now = now.plus(by);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private static final class FakeAccounts implements UserAccountRepositoryPort {
    private final Map<String, UserAccount> byId = new LinkedHashMap<>();

    @Override
    public UserAccount save(UserAccount a) {
      byId.put(a.id(), a);
      return a;
    }

    @Override
    public List<UserAccount> findAll() {
      return new ArrayList<>(byId.values());
    }

    @Override
    public Optional<UserAccount> findById(String id) {
      return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
      return byId.values().stream().filter(a -> a.email().equals(email)).findFirst();
    }

    @Override
    public void deleteById(String id) {
      byId.remove(id);
    }
  }

  private static final class FakeTokens implements PasswordResetTokenPort {
    private final Map<String, PasswordResetToken> byId = new LinkedHashMap<>();

    List<PasswordResetToken> all() {
      return new ArrayList<>(byId.values());
    }

    long issuedCountFor(String accountId) {
      return byId.values().stream().filter(t -> t.accountId().equals(accountId)).count();
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
      byId.put(token.id(), token);
      return token;
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
      return byId.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
    }

    @Override
    public List<PasswordResetToken> findPendingByAccount(String accountId) {
      return byId.values().stream()
          .filter(t -> t.accountId().equals(accountId) && !t.isConsumed())
          .toList();
    }

    @Override
    public void consumeAllPending(String accountId, Instant at) {
      for (PasswordResetToken t : findPendingByAccount(accountId)) {
        byId.put(t.id(), t.consumedAt(at));
      }
    }

    @Override
    public long countIssuedSince(String accountId, Instant since) {
      return byId.values().stream()
          .filter(t -> t.accountId().equals(accountId) && !t.createdAt().isBefore(since))
          .count();
    }
  }

  private static final class FakeEmail
      implements com.engperf.application.port.outbound.EmailSenderPort {
    String to;
    String subject;
    String body;

    @Override
    public void send(String to, String subject, String body) {
      this.to = to;
      this.subject = subject;
      this.body = body;
    }
  }

  private static final class FakeConfig implements PlatformConfigPort {
    @Override
    public AdoIntegration getAdoIntegration() {
      return new AdoIntegration(false, null);
    }

    @Override
    public AdoIntegration saveAdoIntegration(AdoIntegration integration) {
      return integration;
    }

    @Override
    public AiConvention getAiConvention() {
      return new AiConvention(AiStrategy.TRAILER, null, null, null, false);
    }

    @Override
    public AiConvention saveAiConvention(AiConvention convention) {
      return convention;
    }

    @Override
    public SmtpSettings getSmtpSettings() {
      return new SmtpSettings(
          true,
          "smtp.x.com",
          587,
          MailTransport.STARTTLS,
          "no-reply",
          "secret",
          "no-reply@x.com",
          "Eng Performance",
          "https://app.example.com");
    }

    @Override
    public SmtpSettings saveSmtpSettings(SmtpSettings settings) {
      return Objects.requireNonNull(settings);
    }
  }
}
