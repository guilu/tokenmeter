package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.util.List;

public record CostBreakdownModelResponse(
    String provider,
    String model,
    CostBreakdownPricingResponse pricing,
    List<CostBreakdownModeResponse> modes) {}
