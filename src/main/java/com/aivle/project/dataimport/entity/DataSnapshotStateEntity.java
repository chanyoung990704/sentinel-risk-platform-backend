package com.aivle.project.dataimport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** 명시 버전 조회에서 활성 pointer와 독립적으로 검증 상태를 확인한다. */
@Entity @Table(name="data_snapshots") @Immutable @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class DataSnapshotStateEntity {
	@Id @Column(name="id",length=36,columnDefinition="CHAR(36)") private String id;
	@Column(name="state",length=16,nullable=false) private String state;
}
