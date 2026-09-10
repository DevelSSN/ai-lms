package com.ailms.common.dto;

import java.util.List;

/** Structured payload attached to {@link ChatResponse#metadata()} for ASSESSMENT responses. */
public record QuizMetadata(String contentId, int questionCount, String difficulty, List<QuizItem> items) {}