package com.ailms.common.dto;

/** A single retrieved vector result carrying the metadata needed for citations. */
public record RetrievedChunk(String text, String source, double score, int chunkIndex) {}