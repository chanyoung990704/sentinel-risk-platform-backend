package com.aivle.project.dataimport.model;

public record ImportChunkResult(int chunkNumber, int writtenCells, boolean complete) { }
