package com.aivle.project.finance.internal.company.service;

import com.aivle.project.finance.api.CompanyInfoDto;
import com.aivle.project.finance.api.CompanyInformationBatches;
import com.aivle.project.finance.api.CompanySectorDto;
import com.aivle.project.finance.api.ExternalReputationScores;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 단일 기업의 분기/NULL/점수 정책을 유지하며 모듈 간 건별 호출을 없앤다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class LatestCompanyInformationService implements CompanyInformationBatches {
	private final NamedParameterJdbcTemplate jdbc;
	private final ExternalReputationScores reputation;

	@Override public List<CompanyInfoDto> latest(List<Long> ids) {
		if (ids == null || ids.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("기업 ID 목록을 확인해 주세요");
		if (ids.isEmpty()) return List.of();
		// 기존 stockCode 기반 최신 ACTUAL 및 활성 snapshot 선택식을 그대로 사용한다.
		var rows = jdbc.query("""
			SELECT c.id,c.corp_name,c.stock_code,COALESCE(i.industry_name,'') sector,
			       q.quarter_key,k.composite_score,k.risk_level,k.internal_health_score,k.external_health_score
			FROM companies c LEFT JOIN industry_codes i ON i.id=c.industry_code_id
			LEFT JOIN quarters q ON q.quarter_key=(
			  SELECT MAX(vq.quarter_key) FROM company_report_metric_values v
			  JOIN quarters vq ON vq.id=v.quarter_id
			  JOIN company_report_versions rv ON rv.id=v.report_version_id
			  JOIN company_reports cr ON cr.id=rv.company_report_id
			  JOIN companies source_company ON source_company.id=cr.company_id
			  WHERE source_company.stock_code=c.stock_code AND v.value_type='ACTUAL'
			    AND (rv.data_snapshot_id=(SELECT a.snapshot_id FROM active_data_snapshots a WHERE a.family='FINANCIAL')
			      OR (rv.data_snapshot_id IS NULL AND NOT EXISTS (
			        SELECT 1 FROM active_data_snapshots a WHERE a.family='FINANCIAL' AND a.snapshot_id IS NOT NULL))))
			LEFT JOIN company_key_metrics k ON k.company_id=c.id AND k.quarter_id=q.id
			WHERE c.id IN (:ids)
			""", Map.of("ids", ids), (r,n) -> new Row(r.getLong("id"),r.getString("corp_name"),
				r.getString("stock_code"),r.getString("sector"),r.getObject("quarter_key") != null,
				r.getBigDecimal("composite_score"),r.getString("risk_level"),r.getBigDecimal("internal_health_score"),
				r.getBigDecimal("external_health_score")));
		var missingScores = rows.stream().filter(row -> row.hasQuarter && row.reputation == null).map(Row::id).toList();
		var fallback = missingScores.isEmpty() ? Map.<Long,BigDecimal>of() : reputation.latestScores(missingScores);
		var result = new HashMap<Long,CompanyInfoDto>();
		for (var row : rows) {
			var score = row.reputation != null ? row.reputation : row.hasQuarter ? fallback.get(row.id) : null;
			var dto = new CompanyInfoDto(row.id,row.name,row.stockCode,new CompanySectorDto("",row.sector),
				decimal(row.overall),row.risk,decimal(row.health),CompanyInfoService.scaleReputationScore(decimal(score)));
			if (result.put(row.id,dto) != null) throw new IllegalStateException("기업/분기 점수 행이 중복됩니다");
		}
		return ids.stream().map(id -> {
			var value = result.get(id);
			if (value == null) throw new IllegalArgumentException("기업을 찾을 수 없습니다: " + id);
			return value;
		}).toList();
	}

	private Double decimal(BigDecimal value) { return value == null ? null : value.doubleValue(); }
	private record Row(long id,String name,String stockCode,String sector,boolean hasQuarter,
		BigDecimal overall,String risk,BigDecimal health,BigDecimal reputation) {}
}
