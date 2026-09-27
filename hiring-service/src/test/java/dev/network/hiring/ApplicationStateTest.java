package dev.network.hiring;

import static dev.network.hiring.application.ApplicationState.*;
import static org.assertj.core.api.Assertions.*;

import dev.network.hiring.application.ApplicationState;
import java.util.*;
import org.junit.jupiter.api.Test;

class ApplicationStateTest {
  @Test
  void reviewerMatrixIsExhaustive() {
    var transitions =
        Map.of(
            SUBMITTED,
            Set.of(SUBMITTED, IN_REVIEW, SHORTLISTED, REJECTED),
            IN_REVIEW,
            Set.of(IN_REVIEW, SHORTLISTED, REJECTED),
            SHORTLISTED,
            Set.of(SHORTLISTED, REJECTED),
            REJECTED,
            Set.of(REJECTED),
            WITHDRAWN,
            Set.of(WITHDRAWN));
    for (var from : ApplicationState.values())
      for (var to : ApplicationState.values())
        assertThat(from.reviewerMayMoveTo(to))
            .as("%s -> %s", from, to)
            .isEqualTo(transitions.get(from).contains(to));
    assertThat(REJECTED.terminal()).isTrue();
    assertThat(WITHDRAWN.terminal()).isTrue();
    assertThat(SHORTLISTED.terminal()).isFalse();
  }
}
