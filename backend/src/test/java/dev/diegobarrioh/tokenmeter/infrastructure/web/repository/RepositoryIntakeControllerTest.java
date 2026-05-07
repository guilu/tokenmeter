package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.repository.RepositoryIntakeResult;
import dev.diegobarrioh.tokenmeter.application.repository.RepositoryIntakeService;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepositoryIntakeController.class)
class RepositoryIntakeControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private RepositoryIntakeService intakeService;

  @Test
  void returnsCreatedForValidRepository() throws Exception {
    when(intakeService.intake(anyString()))
        .thenReturn(
            new RepositoryIntakeResult(
                "https://github.com/guilu/tokenmeter",
                "https://github.com/guilu/tokenmeter.git",
                "guilu",
                "tokenmeter",
                42,
                2,
                true));

    mockMvc
        .perform(
            post("/api/repositories/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/tokenmeter\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/guilu/tokenmeter"))
        .andExpect(jsonPath("$.totalBytes").value(42))
        .andExpect(jsonPath("$.cleanedUp").value(true));
  }

  @Test
  void mapsInvalidUrlToBadRequest() throws Exception {
    when(intakeService.intake(anyString()))
        .thenThrow(
            new RepositoryIntakeException(RepositoryIntakeErrorCode.INVALID_URL, "invalid url"));

    mockMvc
        .perform(
            post("/api/repositories/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"not-a-url\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_URL"));
  }

  @Test
  void mapsPrivateOrMissingRepositoryToNotFound() throws Exception {
    when(intakeService.intake(anyString()))
        .thenThrow(
            new RepositoryIntakeException(
                RepositoryIntakeErrorCode.REPOSITORY_NOT_ACCESSIBLE, "not accessible"));

    mockMvc
        .perform(
            post("/api/repositories/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/private\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPOSITORY_NOT_ACCESSIBLE"));
  }

  @Test
  void mapsRepositoryTooLargeToPayloadTooLarge() throws Exception {
    when(intakeService.intake(anyString()))
        .thenThrow(
            new RepositoryIntakeException(
                RepositoryIntakeErrorCode.REPOSITORY_TOO_LARGE, "too large"));

    mockMvc
        .perform(
            post("/api/repositories/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/huge\"}"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("REPOSITORY_TOO_LARGE"));
  }

  @Test
  void mapsCloneTimeoutToGatewayTimeout() throws Exception {
    when(intakeService.intake(anyString()))
        .thenThrow(
            new RepositoryIntakeException(RepositoryIntakeErrorCode.CLONE_TIMEOUT, "timeout"));

    mockMvc
        .perform(
            post("/api/repositories/intake")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/slow\"}"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("CLONE_TIMEOUT"));
  }
}
