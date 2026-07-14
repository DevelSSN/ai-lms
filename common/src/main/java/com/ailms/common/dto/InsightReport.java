package com.ailms.common.dto;

import java.util.List;
import java.util.Map;

public record InsightReport(
    String userId,
    String period,
    Double progressScore,
    List<String> strengths,
    List<String> weaknesses,
    List<String> recommendations,
    Map<String, Object> metrics,
    String generatedAt
) {}
