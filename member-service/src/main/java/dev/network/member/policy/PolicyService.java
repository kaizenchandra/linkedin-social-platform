package dev.network.member.policy;

import dev.network.member.connection.*;
import dev.network.member.profile.MemberRepository;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PolicyService {
  private final BlockRepository blocks;
  private final MemberRepository members;
  private final ConnectionRepository connections;
  private final Clock clock;

  public PolicyService(BlockRepository b, MemberRepository m, ConnectionRepository c, Clock clock) {
    blocks = b;
    members = m;
    connections = c;
    this.clock = clock;
  }

  public record Decision(String memberId, boolean visible, boolean connected) {}

  public void requireVisible(String actor, String target) {
    if (blocks.between(actor, target) > 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
  }

  @Transactional(readOnly = true)
  public List<Decision> check(String actor, List<String> targets) {
    UUID.fromString(actor);
    if (targets.isEmpty()) return List.of();
    if (targets.size() > 501) throw new IllegalArgumentException("Maximum501 members");
    targets.forEach(UUID::fromString);
    var blocked = new HashSet<String>();
    for (var b : blocks.relevant(actor, targets))
      blocked.add(b.blockerId.equals(actor) ? b.blockedId : b.blockerId);
    var accepted = new HashSet<String>();
    for (var c : connections.list(actor, Connection.State.ACCEPTED, PageRequest.of(0, 501)))
      accepted.add(c.other(actor));
    var existing = new HashSet<String>();
    members.findAllById(targets).forEach(m -> existing.add(m.id));
    return targets.stream()
        .distinct()
        .map(
            id ->
                new Decision(
                    id,
                    existing.contains(id) && !blocked.contains(id),
                    accepted.contains(id) && !blocked.contains(id)))
        .toList();
  }

  @Transactional
  public void block(String actor, String target) {
    UUID.fromString(target);
    if (actor.equals(target)) throw new IllegalArgumentException("Cannot block yourself");
    for (String id : java.util.stream.Stream.of(actor, target).sorted().toList())
      members
          .lock(id)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    if (blocks.findByBlockerIdAndBlockedId(actor, target).isEmpty()) {
      var b = new MemberBlock();
      b.id = UUID.randomUUID().toString();
      b.blockerId = actor;
      b.blockedId = target;
      b.createdAt = clock.instant();
      blocks.save(b);
    }
    connections
        .findByLowIdAndHighId(
            actor.compareTo(target) < 0 ? actor : target,
            actor.compareTo(target) < 0 ? target : actor)
        .ifPresent(
            c -> {
              if (c.state == Connection.State.ACCEPTED) c.state = Connection.State.REMOVED;
              else if (c.state == Connection.State.PENDING) c.state = Connection.State.CANCELLED;
              c.updatedAt = clock.instant();
            });
  }

  @Transactional
  public void unblock(String actor, String target) {
    UUID.fromString(target);
    if (actor.equals(target)) throw new IllegalArgumentException("Cannot unblock yourself");
    for (String id : java.util.stream.Stream.of(actor, target).sorted().toList())
      members
          .lock(id)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    blocks.findByBlockerIdAndBlockedId(actor, target).ifPresent(blocks::delete);
  }
}
