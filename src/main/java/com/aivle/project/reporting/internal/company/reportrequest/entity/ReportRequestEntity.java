package com.aivle.project.reporting.internal.company.reportrequest.entity;
import com.aivle.project.reporting.api.ReportRequestStatus;
import java.util.UUID;
/** JDBC 원장의 불변 조회 모델. 변경은 서비스 트랜잭션 안의 조건부 SQL로 수행한다. */
public record ReportRequestEntity(UUID id,long userId,long companyId,int targetQuarterKey,UUID snapshotId,String keyHash,String inputHash,String instructions,ReportRequestStatus status,long stateVersion,boolean admissionSlotHeld) { }
