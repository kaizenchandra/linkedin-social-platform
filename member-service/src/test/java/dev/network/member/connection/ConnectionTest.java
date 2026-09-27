package dev.network.member.connection;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConnectionTest {
  Connection pending() {
    var c = new Connection();
    c.lowId = "a";
    c.highId = "b";
    c.requesterId = "a";
    c.state = Connection.State.PENDING;
    return c;
  }

  @Test
  void onlyRecipientAccepts() {
    assertThatThrownBy(() -> pending().transition("a", "accept"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void acceptAndRepeatAreDeterministic() {
    var c = pending();
    assertThat(c.transition("b", "accept")).isTrue();
    assertThat(c.transition("b", "accept")).isFalse();
    assertThat(c.transition("a", "remove")).isTrue();
  }

  @Test
  void strangerCannotTransition() {
    assertThatThrownBy(() -> pending().transition("c", "accept"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlySenderCancels() {
    assertThatThrownBy(() -> pending().transition("b", "cancel"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void terminalCannotAccept() {
    var c = pending();
    c.transition("b", "reject");
    assertThatThrownBy(() -> c.transition("b", "accept")).isInstanceOf(IllegalStateException.class);
  }
}
