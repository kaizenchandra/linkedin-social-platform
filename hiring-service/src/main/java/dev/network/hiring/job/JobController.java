package dev.network.hiring.job;

import dev.network.hiring.shared.HiringPages;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class JobController {
  private final JobService service;

  public JobController(JobService s) {
    service = s;
  }

  @PostMapping("/companies/{companyId}/jobs")
  public JobService.View create(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String companyId,
      @Valid @RequestBody JobService.Input in) {
    return service.create(j.getSubject(), companyId, in);
  }

  @PutMapping("/jobs/{id}")
  public JobService.View edit(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @Valid @RequestBody JobService.Input in) {
    return service.edit(j.getSubject(), id, in);
  }

  @PostMapping("/jobs/{id}/{action}")
  public JobService.View transition(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @PathVariable String action) {
    return service.transition(j.getSubject(), id, action);
  }

  @GetMapping("/jobs/{id}")
  public JobService.View get(@PathVariable String id) {
    return service.get(id);
  }

  @GetMapping("/companies/{companyId}/jobs/{id}")
  public JobService.View managed(
      @AuthenticationPrincipal Jwt j, @PathVariable String companyId, @PathVariable String id) {
    return service.managed(j.getSubject(), companyId, id);
  }

  @GetMapping("/jobs")
  public HiringPages.Slice<JobService.View> search(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(defaultValue = "false") boolean followedCompanies,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) String companyId,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) Job.Work workArrangement,
      @RequestParam(required = false) Job.Employment employmentType,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.search(
        q,
        companyId,
        location,
        workArrangement,
        employmentType,
        cursor,
        size,
        j.getSubject(),
        followedCompanies);
  }

  @GetMapping("/companies/{companyId}/jobs")
  public HiringPages.Slice<JobService.View> company(
      @PathVariable String companyId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.search("", companyId, null, null, null, cursor, size);
  }

  @GetMapping("/companies/{companyId}/jobs/manage")
  public HiringPages.Slice<JobService.View> manage(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String companyId,
      @RequestParam(required = false) Job.State state,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.manage(j.getSubject(), companyId, state, cursor, size);
  }
}
