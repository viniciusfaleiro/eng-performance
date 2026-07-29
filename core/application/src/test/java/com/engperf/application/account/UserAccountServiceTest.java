package com.engperf.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.engperf.application.port.inbound.IdentityUseCase;
import com.engperf.application.port.outbound.PasswordHasher;
import com.engperf.application.port.outbound.UserAccountRepositoryPort;
import com.engperf.application.structure.Coverage;
import com.engperf.domain.account.AccountStatus;
import com.engperf.domain.account.Role;
import com.engperf.domain.account.UserAccount;
import com.engperf.domain.common.ConflictException;
import com.engperf.domain.structure.CommitterIdentity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserAccountServiceTest {

  private FakeRepo repo;
  private UserAccountService service;

  @BeforeEach
  void setUp() {
    repo = new FakeRepo();
    service = new UserAccountService(repo, HASHER, NOOP_IDENTITIES);
  }

  private static final IdentityUseCase NOOP_IDENTITIES =
      new IdentityUseCase() {
        @Override
        public List<CommitterIdentity> identities() {
          return List.of();
        }

        @Override
        public CommitterIdentity assign(String identity, String personId) {
          return null;
        }

        @Override
        public Coverage coverage() {
          return Coverage.of(0, 0);
        }

        @Override
        public Reload reload() {
          return new Reload(0, 0);
        }

        @Override
        public int autoLink() {
          return 0;
        }
      };

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

  @Test
  void createsHashesAndDerivesId() {
    UserAccount a =
        service.create("Ana", "Ana@Empresa.com", "secret123", Role.MANAGER, null, "p:ana");
    assertThat(a.id()).isEqualTo("u:ana-empresa-com");
    assertThat(a.email()).isEqualTo("ana@empresa.com");
    assertThat(a.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(a.passwordHash()).isEqualTo("h:secret123");
  }

  @Test
  void rejectsDuplicateEmailAndBlankPassword() {
    service.create("Ana", "ana@x.com", "pw12345678", Role.ADMIN, AccountStatus.ACTIVE, null);
    assertThatExceptionOfType(ConflictException.class)
        .isThrownBy(
            () ->
                service.create("Ana2", "ana@x.com", "pw2", Role.ADMIN, AccountStatus.ACTIVE, null));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> service.create("B", "b@x.com", " ", Role.ADMIN, AccountStatus.ACTIVE, null));
  }

  @Test
  void updatesResetsAndDeletes() {
    UserAccount a =
        service.create(
            "Ana", "ana@x.com", "pw12345678", Role.CONTRIBUTOR, AccountStatus.ACTIVE, null);
    UserAccount updated =
        service.update(a.id(), "Ana S.", Role.MANAGER, AccountStatus.DISABLED, "p:ana");
    assertThat(updated.role()).isEqualTo(Role.MANAGER);
    assertThat(updated.status()).isEqualTo(AccountStatus.DISABLED);

    service.resetPassword(a.id(), "newpass99");
    assertThat(repo.findById(a.id()))
        .get()
        .extracting(UserAccount::passwordHash)
        .isEqualTo("h:newpass99");

    service.delete(a.id());
    assertThat(repo.findAll()).isEmpty();
  }

  private static final class FakeRepo implements UserAccountRepositoryPort {
    private final Map<String, UserAccount> byId = new LinkedHashMap<>();

    @Override
    public UserAccount save(UserAccount account) {
      byId.put(account.id(), account);
      return account;
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
}
