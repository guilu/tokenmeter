package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaderboardLanguagesResponse(
    List<LanguageInsightEntry> languages,
    long totalTokensAllLanguages,
    Map<String, String> filters) {}
