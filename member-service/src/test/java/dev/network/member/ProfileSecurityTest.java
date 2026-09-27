package dev.network.member;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.network.member.profile.*;
import dev.network.web.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfiguration.class, ApiErrors.class})
class ProfileSecurityTest {
  @Autowired MockMvc mvc;
  @MockitoBean MemberRepository members;
  @MockitoBean ExperienceRepository experiences;

  @Test
  void anonymousRejected() throws Exception {
    mvc.perform(get("/api/v1/members/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void malformedTokenRejected() throws Exception {
    mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer not-a-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void subjectWinsOverForgedHeader() throws Exception {
    String id = UUID.randomUUID().toString();
    when(members.findById(id)).thenReturn(Optional.of(new Member(id, "Alice", Instant.EPOCH)));
    when(experiences.findByMemberIdOrderByPosition(id)).thenReturn(List.of());
    mvc.perform(
            get("/api/v1/members/me")
                .with(jwt().jwt(j -> j.subject(id)))
                .header("X-User-Id", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.email").doesNotExist());
  }

  @Test
  void internalApiNeedsServiceScope() throws Exception {
    mvc.perform(get("/internal/v1/connections/x").with(jwt())).andExpect(status().isForbidden());
  }

  @Test
  void invalidProfileRejected() throws Exception {
    mvc.perform(
            put("/api/v1/members/me")
                .with(jwt())
                .contentType("application/json")
                .content("{\"displayName\":\"\",\"experiences\":[]}"))
        .andExpect(status().isBadRequest());
  }
}
