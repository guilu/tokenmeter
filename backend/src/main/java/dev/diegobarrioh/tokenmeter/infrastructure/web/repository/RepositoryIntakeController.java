package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import dev.diegobarrioh.tokenmeter.application.repository.RepositoryIntakeResult;
import dev.diegobarrioh.tokenmeter.application.repository.RepositoryIntakeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryIntakeController {
  private final RepositoryIntakeService intakeService;

  public RepositoryIntakeController(RepositoryIntakeService intakeService) {
    this.intakeService = intakeService;
  }

  @PostMapping("/intake")
  @ResponseStatus(HttpStatus.CREATED)
  public RepositoryIntakeResult intake(@Valid @RequestBody RepositoryIntakeRequest request) {
    return intakeService.intake(request.repositoryUrl());
  }
}
