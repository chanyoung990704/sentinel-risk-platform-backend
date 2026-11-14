package com.aivle.project.dataimport.entity;
import java.util.UUID;
public record ImportErrorEntity(UUID runId, String code, String disposition, String issueJson) { }
