package dev.network.member.connection;

import dev.network.member.events.EventWriter;
import dev.network.member.profile.MemberRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConnectionService {
  public static final int MAX_CONNECTIONS = 500;
  private final ConnectionRepository connections;
  private final MemberRepository members;
  private final EventWriter events;
  private final Clock clock;
  private final jakarta.persistence.EntityManager em;

  public ConnectionService(
      ConnectionRepository c,
      MemberRepository m,
      EventWriter e,
      Clock clock,
      jakarta.persistence.EntityManager em) {
    this.em = em;
    connections = c;
    members = m;
    events = e;
    this.clock = clock;
  }

  public record View(
      String id,
      String requesterId,
      String recipientId,
      String state,
      java.time.Instant updatedAt,
      long version) {}

  private void lockPair(String a, String b) {
    var pair = new ArrayList<>(List.of(a, b));
    pair.sort(String::compareTo);
    for (String id : pair)
      members
          .lock(id)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
  }

  @Transactional
  public View request(String actor, String target) {
    UUID.fromString(target);
    if (actor.equals(target))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Self connection is not allowed");
    lockPair(actor, target);
    String low = actor.compareTo(target) < 0 ? actor : target,
        high = actor.compareTo(target) < 0 ? target : actor;
    var c =
        connections
            .findByLowIdAndHighId(low, high)
            .orElseGet(
                () -> {
                  var x = new Connection();
                  x.id = UUID.randomUUID().toString();
                  x.lowId = low;
                  x.highId = high;
                  x.createdAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                  return x;
                });
    if (c.state == Connection.State.ACCEPTED) return view(c);
    if (c.state == Connection.State.PENDING) {
      if (!c.requesterId.equals(actor))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Reciprocal pending request: accept the existing request");
      return view(c);
    }
    c.requesterId = actor;
    c.state = Connection.State.PENDING;
    c.updatedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    connections.saveAndFlush(c);
    events.write("connection.requested", c.id, c.version, actor, target);
    return view(c);
  }

  @Transactional
  public View command(String actor, String id, String action) {
    var initial =
        connections
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));
    if (!initial.participant(actor))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found");
    lockPair(initial.lowId, initial.highId);
    // Refresh after acquiring participant locks: another transaction may have changed state while
    // we waited.
    em.refresh(initial);
    var c = initial;
    if (action.equals("accept")
        && c.state != Connection.State.ACCEPTED
        && (connections.degree(c.lowId) >= MAX_CONNECTIONS
            || connections.degree(c.highId) >= MAX_CONNECTIONS))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Connection limit is 500");
    boolean changed;
    try {
      changed = c.transition(actor, action);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
    if (changed) {
      c.updatedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
      connections.flush();
      if (action.equals("accept"))
        events.write("connection.accepted", c.id, c.version, actor, c.other(actor));
    }
    return view(c);
  }

  @Transactional(readOnly = true)
  public List<View> list(String actor, Connection.State state, int page, int size) {
    return connections
        .list(
            actor,
            state,
            PageRequest.of(dev.network.web.Pages.page(page), dev.network.web.Pages.size(size)))
        .stream()
        .map(this::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<String> accepted(String id) {
    if (!members.existsById(id))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    var list =
        connections.list(id, Connection.State.ACCEPTED, PageRequest.of(0, MAX_CONNECTIONS + 1));
    if (list.size() > MAX_CONNECTIONS)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Connection invariant violated");
    return list.stream().map(c -> c.other(id)).sorted().toList();
  }

  private View view(Connection c) {
    return new View(
        c.id, c.requesterId, c.other(c.requesterId), c.state.name(), c.updatedAt, c.version);
  }
}
