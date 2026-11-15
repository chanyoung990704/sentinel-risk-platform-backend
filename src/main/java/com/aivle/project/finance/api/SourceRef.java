package com.aivle.project.finance.api;
import java.util.UUID;
/** 수락 시 고정한 원본 식별자. 활성 포인터를 다시 조회해 바꾸지 않는다. */
public record SourceRef(UUID snapshotId, Long companyId, int targetQuarterKey) { }
