package dev.network.hiring;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.network.hiring.company.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.*;

class OracleCompanyIT extends OracleHiringTest {
  @Test
  void rolesTransferExpiryAndRevocation() {
    String owner = id(), recruiter = id(), stranger = id();
    var c = companies.create(owner, input("test-" + id()));
    assertThat(c.verificationStatus()).isEqualTo("UNVERIFIED");
    assertThat(status(() -> companies.edit(stranger, c.id(), input("changed-" + id()))))
        .isEqualTo(404);
    assertThat(status(() -> companies.remove(owner, c.id(), owner))).isEqualTo(409);
    var invite = companies.invite(owner, c.id(), recruiter);
    assertThat(companies.invite(owner, c.id(), recruiter).id()).isEqualTo(invite.id());
    assertThat(status(() -> companies.invitationAction(stranger, invite.id(), "accept")))
        .isEqualTo(404);
    companies.invitationAction(recruiter, invite.id(), "accept");
    companies.invitationAction(recruiter, invite.id(), "accept");
    companies.transfer(owner, c.id(), recruiter);
    assertThat(companies.members(recruiter, c.id()))
        .filteredOn(m -> m.role().equals("OWNER"))
        .extracting(CompanyService.Membership::memberId)
        .containsExactly(recruiter);
    companies.remove(recruiter, c.id(), owner);
    assertThat(status(() -> companies.members(owner, c.id()))).isEqualTo(404);
    var expired = companies.invite(recruiter, c.id(), stranger);
    db.update(
        "UPDATE company_invitations SET expires_at=SYSTIMESTAMP-INTERVAL '1' DAY WHERE id=?",
        expired.id());
    assertThat(status(() -> companies.invitationAction(stranger, expired.id(), "accept")))
        .isEqualTo(409);
    assertThat(companies.invite(recruiter, c.id(), stranger).id()).isNotEqualTo(expired.id());
    assertThat(companies.auditHistory(recruiter, c.id(), 0, 100))
        .extracting(CompanyService.Audit::action)
        .contains("OWNERSHIP_TRANSFERRED", "RECRUITER_REMOVED");
  }

  @RepeatedTest(10)
  void acceptanceCancellationAndTransferRemovalAreSerialized() throws Exception {
    String owner = id(), recruiter = id();
    var c = companies.create(owner, input("race-" + id()));
    var invite = companies.invite(owner, c.id(), recruiter);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var start = new CyclicBarrier(2);
      var a =
          pool.submit(
              () -> {
                start.await();
                return status(() -> companies.invitationAction(recruiter, invite.id(), "accept"));
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return status(() -> companies.invitationAction(owner, invite.id(), "cancel"));
              });
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
      if (!companies.member(c.id(), recruiter)) {
        var fresh = companies.invite(owner, c.id(), recruiter);
        companies.invitationAction(recruiter, fresh.id(), "accept");
      }
      var barrier = new CyclicBarrier(2);
      var t =
          pool.submit(
              () -> {
                barrier.await();
                return status(() -> companies.transfer(owner, c.id(), recruiter));
              });
      var r =
          pool.submit(
              () -> {
                barrier.await();
                return status(() -> companies.remove(owner, c.id(), recruiter));
              });
      var results = List.of(t.get(), r.get());
      assertThat(results.stream().filter(x -> x == 200).count()).isEqualTo(1);
      assertThat(
              db.queryForObject(
                  "SELECT COUNT(*) FROM companies c JOIN company_members m ON m.company_id=c.id AND"
                      + " m.member_id=c.owner_id WHERE c.id=?",
                  Long.class,
                  c.id()))
          .isEqualTo(1);
    }
  }

  @Test
  void normalizedSlugConstraintAndWebsiteValidation() {
    String owner = id(), slug = "normalized-" + id();
    companies.create(owner, input(slug.toUpperCase(Locale.ROOT)));
    assertThatThrownBy(() -> companies.create(owner, input(slug)))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                companies.create(
                    owner,
                    new CompanyService.Input(
                        "Company", "url-" + id(), "D", "I", "L", "file:///private")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(companies.list(owner, true, null, 1).items()).hasSize(1);
  }
}
