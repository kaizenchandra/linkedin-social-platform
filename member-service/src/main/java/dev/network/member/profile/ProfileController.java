package dev.network.member.profile;

import dev.network.web.Pages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/members")
public class ProfileController {
  private final MemberRepository members;
  private final ExperienceRepository experiences;
  private final Clock clock;

  public ProfileController(MemberRepository m, ExperienceRepository e, Clock c) {
    members = m;
    experiences = e;
    clock = c;
  }

  public record ExperienceInput(
      @NotBlank @Size(max = 100) String company,
      @NotBlank @Size(max = 100) String title,
      @NotNull @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String startMonth,
      @Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])") String endMonth) {}

  public record Input(
      @NotBlank @Size(max = 100) String displayName,
      @Size(max = 200) String headline,
      @Size(max = 2000) String summary,
      @Size(max = 100) String location,
      @NotNull @Size(max = 10) List<@Valid ExperienceInput> experiences) {}

  public record View(
      String id,
      String displayName,
      String headline,
      String summary,
      String location,
      List<ExperienceInput> experiences,
      long version) {}

  public record Summary(String id, String displayName, String headline, String location) {}

  @PutMapping("/me")
  @Transactional
  public View save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Input in) {
    var member =
        members
            .lock(jwt.getSubject())
            .orElseGet(() -> new Member(jwt.getSubject(), in.displayName(), clock.instant()));
    member.displayName = in.displayName().strip();
    member.headline = in.headline();
    member.summary = in.summary();
    member.location = in.location();
    members.saveAndFlush(member);
    experiences.deleteByMemberId(member.id);
    experiences.flush();
    int pos = 0;
    for (var x : in.experiences()) {
      if (x.endMonth() != null && x.endMonth().compareTo(x.startMonth()) < 0)
        throw new IllegalArgumentException("end before start");
      var e = new Experience();
      e.id = UUID.randomUUID().toString();
      e.memberId = member.id;
      e.company = x.company();
      e.title = x.title();
      e.startMonth = x.startMonth();
      e.endMonth = x.endMonth();
      e.position = pos++;
      experiences.save(e);
    }
    return view(member);
  }

  @GetMapping("/me")
  @Transactional(readOnly = true)
  public View me(@AuthenticationPrincipal Jwt jwt) {
    return get(jwt.getSubject());
  }

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  public View get(@PathVariable String id) {
    return view(
        members
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not initialized")));
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<Summary> search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (q.length() > 100) throw new IllegalArgumentException();
    String term =
        "%"
            + q.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
            + "%";
    return members.search(term, PageRequest.of(Pages.page(page), Pages.size(size))).stream()
        .map(m -> new Summary(m.id, m.displayName, m.headline, m.location))
        .toList();
  }

  @PostMapping("/lookup")
  @Transactional(readOnly = true)
  public List<Summary> lookup(@RequestBody @Size(max = 100) List<String> ids) {
    if (ids.size() > 100) throw new IllegalArgumentException();
    return members.findAllById(ids).stream()
        .map(m -> new Summary(m.id, m.displayName, m.headline, m.location))
        .toList();
  }

  private View view(Member m) {
    return new View(
        m.id,
        m.displayName,
        m.headline,
        m.summary,
        m.location,
        experiences.findByMemberIdOrderByPosition(m.id).stream()
            .map(e -> new ExperienceInput(e.company, e.title, e.startMonth, e.endMonth))
            .toList(),
        m.version);
  }
}
