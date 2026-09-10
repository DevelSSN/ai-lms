package com.ailms.common.dto;

/** A single citation resolved from a numbered {@code [N]} marker in a bot response. */
public record Citation(int number, String source, String documentName, int chunkIndex, String text) {}