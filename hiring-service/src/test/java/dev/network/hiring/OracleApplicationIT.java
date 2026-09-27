package dev.network.hiring;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.network.hiring.application.*;
import dev.network.hiring.job.*;

import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

class OracleApplicationIT extends OracleHiringTest {
    @Autowired
    ApplicationService applications;
    @Autowired
    JobService jobs;

    @BeforeEach
    void snapshots() {
        when(http.post(anyString(), any(), eq(ProfileSnapshots.Snapshot.class)))
                .thenAnswer(
                        i -> {
                            Map<String, String> body = i.getArgument(1);
                            return new ProfileSnapshots.Snapshot(
                                    body.get("memberId"),
                                    42,
                                    "Applicant",
                                    "Engineer",
                                    "Snapshot summary",
                                    "Local",
                                    List.of());
                        });
    }

    JobService.Input job(Long version) {
        return new JobService.Input(
                "Original job",
                "Original description",
                "Local",
                Job.Work.REMOTE,
                Job.Employment.FULL_TIME,
                null,
                null,
                null,
                null,
                null,
                version);
    }

    JobService.View published(String owner, String company) {
        var j = jobs.create(owner, company, job(null));
        return jobs.transition(owner, j.id(), "publish");
    }

    @Test
    void duplicateInputSnapshotsIsolationAndWithdrawal() throws Exception {
        String owner = id(), actor = id(), stranger = id();
        var c = companies.create(owner, input("apply-" + id()));
        var j = published(owner, c.id());
        var input = new ApplicationService.Input(j.id(), id(), "Private cover note");
        ApplicationService.View a;
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CyclicBarrier(2);
            var one =
                    pool.submit(
                            () -> {
                                gate.await();
                                return applications.submit(actor, input);
                            });
            var two =
                    pool.submit(
                            () -> {
                                gate.await();
                                return applications.submit(actor, input);
                            });
            a = one.get();
            assertThat(two.get().id()).isEqualTo(a.id());
        }
        assertThat(applications.submit(actor, input).id()).isEqualTo(a.id());
        assertThat(a.profileSnapshot().version()).isEqualTo(42);
        assertThat(
                status(
                        () ->
                                applications.submit(
                                        actor,
                                        new ApplicationService.Input(j.id(), input.idempotencyKey(), "Changed"))))
                .isEqualTo(409);
        assertThat(
                status(
                        () ->
                                applications.submit(
                                        actor, new ApplicationService.Input(j.id(), id(), "Private cover note"))))
                .isEqualTo(409);
        assertThat(status(() -> applications.get(stranger, a.id()))).isEqualTo(404);
        assertThat(status(() -> applications.list(stranger, c.id(), null, null, null, 20)))
                .isEqualTo(404);
        assertThat(
                status(
                        () -> applications.submit(owner, new ApplicationService.Input(j.id(), id(), null))))
                .isEqualTo(409);
        jobs.edit(
                owner,
                j.id(),
                new JobService.Input(
                        "Changed job",
                        "Changed description",
                        "Local",
                        Job.Work.REMOTE,
                        Job.Employment.FULL_TIME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        j.version()));
        assertThat(applications.get(actor, a.id()).jobSnapshot().title()).isEqualTo("Original job");
        var review =
                applications.review(
                        owner, a.id(), new ApplicationService.Review(ApplicationState.IN_REVIEW, a.version()));
        var shortlisted =
                applications.review(
                        owner,
                        a.id(),
                        new ApplicationService.Review(ApplicationState.SHORTLISTED, review.version()));
        assertThat(
                applications
                        .review(
                                owner, a.id(), new ApplicationService.Review(ApplicationState.SHORTLISTED, 0L))
                        .version())
                .isEqualTo(shortlisted.version());
        var withdrawn = applications.withdraw(actor, a.id());
        assertThat(withdrawn.profileSnapshot()).isNotNull();
        var reviewer = applications.get(owner, a.id());
        assertThat(reviewer.profileSnapshot()).isNull();
        assertThat(reviewer.coverNote()).isNull();
        assertThat(applications.history(actor, a.id())).hasSize(4);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox WHERE aggregate_id=?", Long.class, a.id()))
                .isEqualTo(3); // submission + two real reviewer transitions; retries/withdrawal are silent
        for (String envelope : db.queryForList("SELECT envelope FROM outbox WHERE aggregate_id=?", String.class, a.id()))
            assertThat(envelope).doesNotContain("Private cover note", "Snapshot summary", "Original description");
        assertThat(
                applications.list(owner, c.id(), j.id(), ApplicationState.WITHDRAWN, null, 20).items())
                .hasSize(1);
        assertThat(
                status(
                        () ->
                                applications.review(
                                        owner,
                                        a.id(),
                                        new ApplicationService.Review(
                                                ApplicationState.REJECTED, withdrawn.version()))))
                .isEqualTo(409);
    }

    @Test
    void twoReviewersCannotOverwriteSameVersion() throws Exception {
        String owner = id(), recruiter = id(), actor = id();
        var c = companies.create(owner, input("review-" + id()));
        var invite = companies.invite(owner, c.id(), recruiter);
        companies.invitationAction(recruiter, invite.id(), "accept");
        var j = published(owner, c.id());
        var a = applications.submit(actor, new ApplicationService.Input(j.id(), id(), null));
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CyclicBarrier(2);
            var one =
                    pool.submit(
                            () -> {
                                gate.await();
                                return status(
                                        () ->
                                                applications.review(
                                                        owner,
                                                        a.id(),
                                                        new ApplicationService.Review(
                                                                ApplicationState.IN_REVIEW, a.version())));
                            });
            var two =
                    pool.submit(
                            () -> {
                                gate.await();
                                return status(
                                        () ->
                                                applications.review(
                                                        recruiter,
                                                        a.id(),
                                                        new ApplicationService.Review(
                                                                ApplicationState.SHORTLISTED, a.version())));
                            });
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(applications.history(actor, a.id())).hasSize(2);
        companies.remove(owner, c.id(), recruiter);
        assertThat(status(() -> applications.get(recruiter, a.id()))).isEqualTo(404);
    }

    @RepeatedTest(5)
    void closingAndSubmissionHaveOneTransactionalOrder() throws Exception {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("close-" + id()));
        var j = published(owner, c.id());
        var input = new ApplicationService.Input(j.id(), id(), null);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CyclicBarrier(2);
            var close =
                    pool.submit(
                            () -> {
                                gate.await();
                                return jobs.transition(owner, j.id(), "close");
                            });
            var submit =
                    pool.submit(
                            () -> {
                                gate.await();
                                return status(() -> applications.submit(actor, input));
                            });
            assertThat(close.get().state()).isEqualTo(Job.State.CLOSED);
            int code = submit.get();
            assertThat(code).isIn(200, 409);
            assertThat(applications.list(actor, null, j.id(), null, null, 20).items())
                    .hasSize(code == 200 ? 1 : 0);
        }
        assertThat(
                status(
                        () -> applications.submit(id(), new ApplicationService.Input(j.id(), id(), null))))
                .isEqualTo(409);
    }

    @Test
    void profileFailureDoesNotCreateIncompleteApplication() {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("snapshot-" + id()));
        var j = published(owner, c.id());
        when(http.post(anyString(), any(), eq(ProfileSnapshots.Snapshot.class)))
                .thenThrow(
                        new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(
                status(
                        () ->
                                applications.submit(
                                        actor, new ApplicationService.Input(j.id(), id(), "Sensitive"))))
                .isEqualTo(503);
        assertThat(applications.list(actor, null, j.id(), null, null, 20).items()).isEmpty();
    }
}
