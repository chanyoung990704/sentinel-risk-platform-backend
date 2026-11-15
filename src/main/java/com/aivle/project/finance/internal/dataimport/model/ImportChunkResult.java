package com.aivle.project.finance.internal.dataimport.model;

public record ImportChunkResult(int chunkNumber, int writtenCells, boolean complete) { }
