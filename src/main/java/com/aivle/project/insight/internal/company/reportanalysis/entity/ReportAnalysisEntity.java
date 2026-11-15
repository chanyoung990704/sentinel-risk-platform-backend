package com.aivle.project.insight.internal.company.reportanalysis.entity;

import com.aivle.project.foundation.api.entity.BaseEntity;
import com.aivle.project.finance.api.CompanyIdentity;
import com.aivle.project.finance.api.CompanyView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * report_analyses 테이블에 매핑되는 사업보고서 분석 세션 엔티티.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "report_analyses")
public class ReportAnalysisEntity extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "company_id", nullable = false)
	private Long companyId;

	public CompanyIdentity getCompany() { return new CompanyView(companyId, companyName, null, null); }

	@Column(name = "company_name", nullable = false, length = 255)
	private String companyName;

	@Column(name = "total_count", nullable = false)
	private Integer totalCount;

	@Column(name = "average_score", precision = 10, scale = 6)
	private BigDecimal averageScore;

	@Column(name = "analyzed_at", nullable = false)
	private LocalDateTime analyzedAt;

	/**
	 * 사업보고서 분석 엔티티 생성.
	 */
	@Builder
	public static ReportAnalysisEntity create(
		CompanyIdentity company,
		String companyName,
		Integer totalCount,
		BigDecimal averageScore,
		LocalDateTime analyzedAt
	) {
		ReportAnalysisEntity entity = new ReportAnalysisEntity();
		entity.companyId = company.getId();
		entity.companyName = companyName;
		entity.totalCount = totalCount;
		entity.averageScore = averageScore;
		entity.analyzedAt = analyzedAt;
		return entity;
	}
}
