package com.aivle.project.finance.internal.dataimport.repository;

import com.aivle.project.finance.internal.dataimport.entity.QActiveDataSnapshotEntity;
import com.aivle.project.finance.internal.dataimport.entity.QDataSnapshotStateEntity;
import com.aivle.project.finance.api.MetricValueType;
import com.aivle.project.finance.internal.report.entity.QCompanyReportVersionsEntity;
import com.querydsl.core.types.dsl.*;
import com.querydsl.jpa.JPAExpressions;

/** outer 조회와 latest-version subquery가 동일한 범위를 사용하도록 한다. */
public final class SnapshotQueryScope {
	private SnapshotQueryScope() { }
	public static BooleanExpression actual(QCompanyReportVersionsEntity version) {
		return actualSource(version.dataSnapshotId);
	}
	public static BooleanExpression actualSource(StringPath snapshotId) {
		var active=new QActiveDataSnapshotEntity("financialScope");
		return snapshotId.eq(JPAExpressions.select(active.snapshotId).from(active).where(active.family.eq("FINANCIAL")))
			.or(snapshotId.isNull().and(JPAExpressions.selectOne().from(active).where(active.family.eq("FINANCIAL"),active.snapshotId.isNotNull()).notExists()));
	}
	public static BooleanExpression fixedVersion(QCompanyReportVersionsEntity version) {
		var source=new QDataSnapshotStateEntity("fixedSnapshot");
		return version.dataSnapshotId.isNull().or(JPAExpressions.selectOne().from(source).where(source.id.eq(version.dataSnapshotId),source.state.eq("VERIFIED")).exists());
	}
	public static BooleanExpression forType(QCompanyReportVersionsEntity version,MetricValueType type) {return type==MetricValueType.ACTUAL?actual(version):version.dataSnapshotId.isNull();}
	public static BooleanExpression forValue(QCompanyReportVersionsEntity version,EnumPath<MetricValueType> type) {return type.eq(MetricValueType.ACTUAL).and(actual(version)).or(type.ne(MetricValueType.ACTUAL).and(version.dataSnapshotId.isNull()));}
}
