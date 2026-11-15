package com.aivle.project.news.internal.company.news.entity;

import com.aivle.project.foundation.api.entity.BaseEntity;
import com.aivle.project.finance.api.CompanyIdentity;
import com.aivle.project.finance.api.CompanyView;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * news_analyses 테이블에 매핑되는 뉴스 분석 세션 엔티티.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "news_analyses")
public class NewsAnalysisEntity extends BaseEntity implements com.aivle.project.news.api.NewsAnalysisView {

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
	 * 뉴스 분석 엔티티 생성.
	 */
	@Builder
	public static NewsAnalysisEntity create(
		CompanyIdentity company,
		String companyName,
		Integer totalCount,
		BigDecimal averageScore,
		LocalDateTime analyzedAt
	) {
		NewsAnalysisEntity entity = new NewsAnalysisEntity();
		entity.companyId = company.getId();
		entity.companyName = companyName;
		entity.totalCount = totalCount;
		entity.averageScore = averageScore;
		entity.analyzedAt = analyzedAt;
		return entity;
	}

	/**
	 * 평균 감성 점수를 갱신한다.
	 */
	public void applyAverageScore(BigDecimal averageScore) {
		this.averageScore = averageScore;
	}
}
