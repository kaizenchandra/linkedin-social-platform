package dev.network.hiring;

import static org.assertj.core.api.Assertions.*;

import dev.network.hiring.job.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OracleJobIT extends OracleHiringTest {
    @Autowired
    JobService jobs;

    JobService.Input job(String title, Long version) {
        return new JobService.Input(
                title,
                "Literal SQL 100%_! engineering description",
                "London",
                Job.Work.REMOTE,
                Job.Employment.FULL_TIME,
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                "USD",
                Job.Period.HOUR,
                null,
                version);
    }

    @Test
    void lifecyclePrivacyAndOptimisticEdits() throws Exception {
        String owner = id(), stranger = id();
        var c = companies.create(owner, input("job-" + id()));
        var j = jobs.create(owner, c.id(), job("Engineer", null));
        assertThat(status(() -> jobs.get(j.id()))).isEqualTo(404);
        assertThat(status(() -> jobs.managed(stranger, c.id(), j.id()))).isEqualTo(404);
        assertThat(jobs.manage(owner, c.id(), Job.State.DRAFT, null, 20).items()).hasSize(1);
        var edited = jobs.edit(owner, j.id(), job("Senior engineer", j.version()));
        assertThat(edited.version()).isGreaterThan(j.version());
        assertThat(status(() -> jobs.edit(owner, j.id(), job("Stale", j.version())))).isEqualTo(409);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CyclicBarrier(2);
            var a =
                    pool.submit(
                            () -> {
                                gate.await();
                                return jobs.transition(owner, j.id(), "publish");
                            });
            var b =
                    pool.submit(
                            () -> {
                                gate.await();
                                return jobs.transition(owner, j.id(), "publish");
                            });
            assertThat(a.get().version()).isEqualTo(b.get().version());
        }
        assertThat(jobs.get(j.id()).state()).isEqualTo(Job.State.PUBLISHED);
        var closed = jobs.transition(owner, j.id(), "close");
        assertThat(jobs.transition(owner, j.id(), "close").version()).isEqualTo(closed.version());
        assertThat(status(() -> jobs.transition(owner, j.id(), "publish"))).isEqualTo(409);
        assertThat(status(() -> jobs.get(j.id()))).isEqualTo(404);
        assertThat(jobs.search("", c.id(), null, null, null, null, 20).items()).isEmpty();
    }

    @Test
    void filtersLiteralWildcardsAndStableCursor() {
        String owner = id();
        var c = companies.create(owner, input("search-" + id()));
        var ids = new HashSet<String>();
        for (int i = 0; i < 3; i++) {
            var j = jobs.create(owner, c.id(), job("Engineer " + i, null));
            jobs.transition(owner, j.id(), "publish");
            ids.add(j.id());
        }
        db.update(
                "UPDATE jobs SET published_at=TIMESTAMP '2026-09-01 00:00:00' WHERE company_id=?", c.id());
        var first =
                jobs.search(
                        "100%_! engineer", c.id(), "LON", Job.Work.REMOTE, Job.Employment.FULL_TIME, null, 2);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.items()).hasSize(2);
        var second =
                jobs.search(
                        "100%_! engineer",
                        c.id(), "lon", Job.Work.REMOTE, Job.Employment.FULL_TIME, first.nextCursor(), 2);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.items()).hasSize(1);
        var all = new HashSet<String>();
        first.items().forEach(j -> assertThat(all.add(j.id())).isTrue());
        second.items().forEach(j -> assertThat(all.add(j.id())).isTrue());
        assertThat(all).isEqualTo(ids);
        assertThat(jobs.search("100%Z", c.id(), null, null, null, null, 20).items()).isEmpty();
        assertThat(jobs.search("", c.id(), null, Job.Work.ONSITE, null, null, 20).items()).isEmpty();
        db.update(
                "UPDATE jobs SET deadline=SYSTIMESTAMP-INTERVAL '1' SECOND WHERE company_id=?", c.id());
        assertThat(jobs.search("", c.id(), null, null, null, null, 20).items()).isEmpty();
        assertThat(status(() -> jobs.get(ids.iterator().next()))).isEqualTo(404);
    }

    @Test
    void salaryAndDeadlineValidation() {
        String owner = id();
        var c = companies.create(owner, input("salary-" + id()));
        var invalid =
                new JobService.Input(
                        "Job",
                        "Description",
                        "Local",
                        Job.Work.ONSITE,
                        Job.Employment.CONTRACT,
                        new BigDecimal("200"),
                        new BigDecimal("100"),
                        "USD",
                        Job.Period.YEAR,
                        null,
                        null);
        assertThatThrownBy(() -> jobs.create(owner, c.id(), invalid))
                .isInstanceOf(IllegalArgumentException.class);
        var expired =
                new JobService.Input(
                        "Job",
                        "Description",
                        "Local",
                        Job.Work.ONSITE,
                        Job.Employment.CONTRACT,
                        null,
                        null,
                        null,
                        null,
                        Instant.EPOCH,
                        null);
        assertThatThrownBy(() -> jobs.create(owner, c.id(), expired))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
