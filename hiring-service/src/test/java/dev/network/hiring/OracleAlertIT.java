package dev.network.hiring;

import static org.assertj.core.api.Assertions.*;

import dev.network.hiring.alerts.*;
import dev.network.hiring.job.*;

import java.util.*;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OracleAlertIT extends OracleHiringTest {
    @Autowired
    SavedSearchService searches;
    @Autowired
    PublicationConsumer consumer;
    @Autowired
    AlertMatcher matcher;
    @Autowired
    JobService jobs;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired
    io.micrometer.core.instrument.MeterRegistry metrics;

    SavedSearchService.Input search(String company, boolean enabled, Long version) {
        return new SavedSearchService.Input(
                "Search",
                "engineer",
                List.of(company),
                "London",
                Job.Work.REMOTE,
                Job.Employment.FULL_TIME,
                enabled,
                version);
    }

    String publish(String owner, String company) {
        var j =
                jobs.create(
                        owner,
                        company,
                        new JobService.Input(
                                "Engineer",
                                "Literal engineering",
                                "London",
                                Job.Work.REMOTE,
                                Job.Employment.FULL_TIME,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));
        return jobs.transition(owner, j.id(), "publish").id();
    }

    String event(String job) {
        return db.queryForObject(
                "SELECT envelope FROM outbox WHERE aggregate_id=? AND"
                        + " JSON_VALUE(envelope,'$.eventType')='hiring.job.published'",
                String.class,
                job);
    }

    void drain(String job) {
        for (int i = 0;
             i < 500
                     && !db.queryForObject("SELECT state FROM alert_work WHERE job_id=?", String.class, job)
                     .equals("DONE");
             i++)
            matcher.step(job);
        assertThat(db.queryForObject("SELECT state FROM alert_work WHERE job_id=?", String.class, job))
                .isEqualTo("DONE");
    }

    long matches(String actor, String job) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM job_alert_matches WHERE member_id=? AND job_id=?",
                Long.class,
                actor,
                job);
    }

    @Test
    void overlappingSearchesReplayAndEditsProduceOneDurableMatch() {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("alert-" + id()));
        var one = searches.create(actor, search(c.id(), true, null));
        var two = searches.create(actor, search(c.id(), true, null));
        String job = publish(owner, c.id());
        String event = event(job);
        consumer.consume(event);
        consumer.consume(event);
        matcher.step(job);
        drain(job);
        assertThat(matches(actor, job)).isEqualTo(1);
        String chosen =
                db.queryForObject(
                        "SELECT search_id FROM job_alert_matches WHERE member_id=? AND job_id=?",
                        String.class,
                        actor,
                        job);
        assertThat(chosen).isEqualTo(one.id().compareTo(two.id()) < 0 ? one.id() : two.id());
        assertThat(
                db.queryForObject(
                        "SELECT COUNT(*) FROM outbox WHERE aggregate_id=? AND"
                                + " JSON_VALUE(envelope,'$.eventType')='hiring.job.alert'",
                        Long.class,
                        job))
                .isEqualTo(1);
        consumer.consume(event);
        assertThat(matcher.step(job)).isFalse();
        assertThat(matches(actor, job)).isEqualTo(1);
        var j = jobs.managed(owner, c.id(), job);
        jobs.edit(
                owner,
                job,
                new JobService.Input(
                        "Edited",
                        "New description",
                        "Paris",
                        Job.Work.ONSITE,
                        Job.Employment.PART_TIME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        j.version()));
        assertThat(
                db.queryForObject(
                        "SELECT title FROM job_publications WHERE job_id=?", String.class, job))
                .isEqualTo("Engineer");
        assertThat(
                db.queryForObject(
                        "SELECT COUNT(*) FROM outbox WHERE aggregate_id=? AND"
                                + " JSON_VALUE(envelope,'$.eventType')='hiring.job.published'",
                        Long.class,
                        job))
                .isEqualTo(1);
        String match =
                db.queryForObject(
                        "SELECT id FROM job_alert_matches WHERE member_id=? AND job_id=?",
                        String.class,
                        actor,
                        job);
        assertThat(matcher.eligible(actor, match, job)).isTrue();
        var selected = searches.get(actor, chosen);
        searches.update(actor, chosen, search(c.id(), false, selected.version()));
        assertThat(matcher.eligible(actor, match, job)).isFalse();
    }

    @Test
    void newAndChangedCriteriaNeverBackfillAndHiddenJobsSuppress() {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("prospective-" + id()));
        String old = publish(owner, c.id());
        var search = searches.create(actor, search(c.id(), true, null));
        consumer.consume(event(old));
        drain(old);
        assertThat(matches(actor, old)).isZero();
        String next = publish(owner, c.id());
        var updated =
                searches.update(
                        actor,
                        search.id(),
                        new SavedSearchService.Input(
                                "Different",
                                "engineer",
                                List.of(c.id()),
                                null,
                                Job.Work.REMOTE,
                                Job.Employment.FULL_TIME,
                                true,
                                search.version()));
        assertThat(updated.criteriaVersion()).isEqualTo(2);
        consumer.consume(event(next));
        drain(next);
        assertThat(matches(actor, next)).isZero();
        String hidden = publish(owner, c.id());
        db.update("UPDATE jobs SET hidden=1 WHERE id=?", hidden);
        consumer.consume(event(hidden));
        drain(hidden);
        assertThat(matches(actor, hidden)).isZero();
        String deleted = publish(owner, c.id());
        searches.delete(actor, search.id());
        consumer.consume(event(deleted));
        drain(deleted);
        assertThat(matches(actor, deleted)).isZero();
    }

    @Test
    void disableRacingMatcherHasDefinedCommitBoundaryAndResumeIsDurable() throws Exception {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("race-alert-" + id()));
        var search = searches.create(actor, search(c.id(), true, null));
        String job = publish(owner, c.id());
        consumer.consume(event(job));
        // Position this fixture immediately before its owner so the two commands contend on that row.
        db.update(
                "UPDATE alert_work SET last_member=NVL((SELECT MAX(member_id) FROM saved_searches WHERE"
                        + " member_id<?),'0') WHERE job_id=?",
                actor,
                job);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var one =
                    pool.submit(
                            () -> {
                                barrier.await();
                                return matcher.step(job);
                            });
            var two =
                    pool.submit(
                            () -> {
                                barrier.await();
                                return searches.update(actor, search.id(), search(c.id(), false, search.version()));
                            });
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        }
        drain(job);
        assertThat(matches(actor, job)).isBetween(0L, 1L);
        var ids =
                db.queryForList(
                        "SELECT id FROM job_alert_matches WHERE member_id=? AND job_id=?",
                        String.class,
                        actor,
                        job);
        for (String match : ids) assertThat(matcher.eligible(actor, match, job)).isFalse();
        assertThat(db.queryForObject("SELECT state FROM alert_work WHERE job_id=?", String.class, job))
                .isEqualTo("DONE");
    }

    @Test
    void concurrentSearchLimitAndPrivateOwnershipAreEnforced() throws Exception {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("limit-alert-" + id()));
        for (int i = 0; i < 9; i++) searches.create(actor, search(c.id(), false, null));
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a =
                    pool.submit(
                            () -> {
                                barrier.await();
                                return status(() -> searches.create(actor, search(c.id(), true, null)));
                            });
            var b =
                    pool.submit(
                            () -> {
                                barrier.await();
                                return status(() -> searches.create(actor, search(c.id(), true, null)));
                            });
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        var existing = searches.list(actor).getFirst();
        assertThat(searches.list(owner)).isEmpty();
        assertThat(
                status(
                        () ->
                                searches.update(
                                        owner, existing.id(), search(c.id(), true, existing.version()))))
                .isEqualTo(404);
        searches.delete(owner, existing.id());
        assertThat(searches.list(actor)).hasSize(10);
    }

    @Test
    void failedWorkRetainsCheckpointAndCanBeReplayed() {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("retry-alert-" + id()));
        searches.create(actor, search(c.id(), true, null));
        String job = publish(owner, c.id());
        consumer.consume(event(job));
        for (int i = 0; i < 5; i++) matcher.failure(job);
        assertThat(db.queryForObject("SELECT state FROM alert_work WHERE job_id=?", String.class, job))
                .isEqualTo("FAILED");
        assertThat(matcher.step(job)).isFalse();
        matcher.replay(job);
        drain(job);
        assertThat(matches(actor, job)).isEqualTo(1);
    }

    @Test
    void rolledBackMatchDoesNotAdvanceCheckpointOrCommittedMetric() {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("metric-alert-" + id()));
        searches.create(actor, search(c.id(), true, null));
        String job = publish(owner, c.id());
        consumer.consume(event(job));
        double before = metrics.counter("network.alert.matches", "outcome", "created").count();
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            for (int i = 0; i < 500 && matches(actor, job) == 0; i++) matcher.step(job);
                            assertThat(matches(actor, job)).isEqualTo(1);
                            status.setRollbackOnly();
                        });
        assertThat(matches(actor, job)).isZero();
        assertThat(
                db.queryForObject(
                        "SELECT last_member FROM alert_work WHERE job_id=?", String.class, job))
                .isEqualTo("0");
        assertThat(metrics.counter("network.alert.matches", "outcome", "created").count())
                .isEqualTo(before);
        drain(job);
        assertThat(metrics.counter("network.alert.matches", "outcome", "created").count())
                .isEqualTo(before + 1);
    }
}
