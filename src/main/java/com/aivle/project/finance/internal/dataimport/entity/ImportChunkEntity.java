package com.aivle.project.finance.internal.dataimport.entity;
public record ImportChunkEntity(int number, int cellCount, String payloadHash, String payload, String state) { }
