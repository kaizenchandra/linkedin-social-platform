package dev.network.hiring;

import static org.assertj.core.api.Assertions.*;

import dev.network.hiring.discovery.CompanyFollowService;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OracleFollowIT extends OracleHiringTest {
  @Autowired CompanyFollowService follows;

  @Test
  void concurrentInitialFollowsAreUniqueAndGrantNoRole() throws Exception {
    String owner = id(), actor = id();
    var company = companies.create(owner, input("follow-" + id()));
    var barrier = new CyclicBarrier(2);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var one =
          pool.submit(
              () -> {
                barrier.await();
                follows.follow(actor, company.id());
                return true;
              });
      var two =
          pool.submit(
              () -> {
                barrier.await();
                follows.follow(actor, company.id());
                return true;
              });
      assertThat(one.get(20, TimeUnit.SECONDS)).isTrue();
      assertThat(two.get(20, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(follows.list(actor, null, 10).items()).hasSize(1);
    assertThat(follows.list(owner, null, 10).items()).isEmpty();
    assertThat(status(() -> companies.members(actor, company.id()))).isEqualTo(404);
    follows.unfollow(actor, company.id());
    follows.unfollow(actor, company.id());
    assertThat(follows.status(actor, company.id())).isFalse();
  }

  @Test
  void privatePaginationDoesNotRepeatCompanies() {
    String owner = id(), actor = id();
    var a = companies.create(owner, input("page-a-" + id()));
    var b = companies.create(owner, input("page-b-" + id()));
    follows.follow(actor, a.id());
    follows.follow(actor, b.id());
    var first = follows.list(actor, null, 1);
    var second = follows.list(actor, first.nextCursor(), 1);
    assertThat(first.hasMore()).isTrue();
    assertThat(second.hasMore()).isFalse();
    assertThat(first.items().getFirst().companyId())
        .isNotEqualTo(second.items().getFirst().companyId());
  }
}
