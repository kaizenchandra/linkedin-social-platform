package dev.network.member.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/hiring")
public class HiringProfileController {
    private final MemberRepository members;
    private final ExperienceRepository experiences;

    public HiringProfileController(MemberRepository members, ExperienceRepository experiences) {
        this.members = members;
        this.experiences = experiences;
    }

    @PostMapping("/member-exists")
    public boolean exists(@Valid @RequestBody Request in) {
        UUID.fromString(in.memberId());
        return members.existsById(in.memberId());
    }

    @PostMapping("/profile-snapshot")
    @org.springframework.transaction.annotation.Transactional
    public Snapshot snapshot(@Valid @RequestBody Request in) {
        UUID.fromString(in.memberId());
        var m =
                members
                        .lock(in.memberId())
                        .orElseThrow(
                                () ->
                                        new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.CONFLICT,
                                                "Initialize professional profile before applying"));
        return new Snapshot(
                m.id,
                m.version,
                m.displayName,
                m.headline,
                m.summary,
                m.location,
                experiences.findByMemberIdOrderByPosition(m.id).stream()
                        .map(
                                e ->
                                        new ProfileController.ExperienceInput(
                                                e.company, e.title, e.startMonth, e.endMonth))
                        .toList());
    }

    public record Request(@NotBlank String memberId) {
    }

    public record Snapshot(
            String id,
            long version,
            String displayName,
            String headline,
            String summary,
            String location,
            java.util.List<ProfileController.ExperienceInput> experiences) {
    }
}
