package dev.network.hiring;

import static org.assertj.core.api.Assertions.*;

import dev.network.hiring.discovery.SavedJobService;
import dev.network.hiring.job.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OracleSavedJobIT extends OracleHiringTest {
    @Autowired
    SavedJobService saved;
    @Autowired
    JobService jobs;

    @Test
    void savedJobsArePrivateCurrentAndSafeWhenClosed() {
        String owner = id(), actor = id();
        var c = companies.create(owner, input("saved-" + id()));
        var j =
                jobs.create(
                        owner,
                        c.id(),
                        new JobService.Input(
                                "Engineer",
                                "Private draft",
                                "Remote",
                                Job.Work.REMOTE,
                                Job.Employment.FULL_TIME,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));
        assertThat(status(() -> saved.save(actor, j.id()))).isEqualTo(404);
        jobs.transition(owner, j.id(), "publish");
        saved.save(actor, j.id());
        saved.save(actor, j.id());
        assertThat(saved.list(actor, null, 20).items()).hasSize(1);
        assertThat(saved.list(owner, null, 20).items()).isEmpty();
        jobs.transition(owner, j.id(), "close");
        assertThat(saved.list(actor, null, 20).items().getFirst().status()).isEqualTo("CLOSED");
        db.update("UPDATE jobs SET hidden=1 WHERE id=?", j.id());
        assertThat(saved.list(actor, null, 20).items()).isEmpty();
        saved.unsave(actor, j.id());
        saved.unsave(actor, j.id());
    }
}
