package com.ailms.common.dto;

import java.util.List;

/**
 * Structured payload attached to {@link ChatResponse#metadata()} for responses that cite retrieved
 * document chunks via numbered {@code [N]} markers.
 */
public record CitationMetadata(List<Citation> citations) {}