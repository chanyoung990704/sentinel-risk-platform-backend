package com.aivle.project.dataimport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** JPA 조회에서 활성 pointer를 같은 SQL statement의 범위로 사용한다. 쓰기는 JDBC 전환 service만 수행한다. */
@Entity @Table(name="active_data_snapshots") @Immutable @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ActiveDataSnapshotEntity {
	@Id @Column(name="family",length=32) private String family;
	@Column(name="snapshot_id",length=36,columnDefinition="CHAR(36)") private String snapshotId;
}
