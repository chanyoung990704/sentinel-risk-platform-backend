package com.aivle.project.finance.internal.company.service;

import com.aivle.project.foundation.api.util.GetOrCreateResolver;
import com.aivle.project.aiintegration.api.AiGateway;
import com.aivle.project.aiintegration.api.AiHealthScoreResponse;
import com.aivle.project.finance.internal.company.entity.CompaniesEntity;
import com.aivle.project.finance.internal.company.keymetric.entity.CompanyKeyMetricEntity;
import com.aivle.project.finance.api.CompanyKeyMetricRiskLevel;
import com.aivle.project.finance.internal.company.keymetric.repository.CompanyKeyMetricRepository;
import com.aivle.project.finance.internal.company.repository.CompaniesRepository;
import com.aivle.project.finance.internal.quarter.entity.QuartersEntity;
import com.aivle.project.finance.internal.quarter.repository.QuartersRepository;
import com.aivle.project.finance.api.QuarterCalculator;
import com.aivle.project.finance.api.YearQuarter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재무건전성 점수 캐시 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyHealthScoreCacheService implements com.aivle.project.finance.api.HealthScoreWarmup {

	private static final int DEFAULT_CALCULATION_LOGIC_VER = 1;

	private final AiGateway aiServerClient;
	private final CompaniesRepository companiesRepository;
	private final QuartersRepository quartersRepository;
	private final CompanyKeyMetricRepository companyKeyMetricRepository;

	/**
	 * 분기별 재무건전성 점수 캐시를 보장한다.
	 */
	@Transactional
	public void ensureHealthScoreCached(Long companyId, int requestedQuarterKey) {
		CompaniesEntity company = companiesRepository.findById(companyId)
			.orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

		Optional<CompanyKeyMetricEntity> cached = companyKeyMetricRepository
			.findByCompanyIdAndQuarter_QuarterKey(companyId, requestedQuarterKey);
		if (cached.filter(this::hasHealthScore).isPresent()) {
			return;
		}

		AiHealthScoreResponse response = aiServerClient.getHealthScore(company.getStockCode());
		if (response == null || response.quarters() == null || response.quarters().isEmpty()) {
			log.warn("Empty AI health score response for company: {}", company.getStockCode());
			return;
		}

		for (AiHealthScoreResponse.HealthScoreQuarter quarterScore : response.quarters()) {
			if (quarterScore == null || quarterScore.period() == null || quarterScore.score() == null) {
				continue;
			}
			int quarterKey = parseQuarterKey(quarterScore.period());
			QuartersEntity quarter = getOrCreateQuarter(quarterKey);
			CompanyKeyMetricEntity entity = companyKeyMetricRepository
				.findByCompanyIdAndQuarterId(companyId, quarter.getId())
				.orElse(null);

			BigDecimal score = BigDecimal.valueOf(quarterScore.score());
			CompanyKeyMetricRiskLevel riskLevel = mapRiskLevel(quarterScore.label());
			LocalDateTime now = LocalDateTime.now();

			if (entity == null) {
				try {
					companyKeyMetricRepository.saveAndFlush(CompanyKeyMetricEntity.create(
						company,
						quarter,
						null,
						score,
						null,
						score,
						riskLevel,
						DEFAULT_CALCULATION_LOGIC_VER,
						now
					));
				} catch (org.springframework.dao.DataIntegrityViolationException e) {
					log.warn("Concurrent insert detected for health score caching, companyId={}, quarterId={}", companyId, quarter.getId());
				}
				continue;
			}

			if (shouldUpdate(entity)) {
				entity.applyHealthScore(
					score,
					score,
					riskLevel,
					DEFAULT_CALCULATION_LOGIC_VER,
					now
				);
			}
		}
	}

	/**
	 * 요청 분기의 CompanyKeyMetric 엔티티를 보장한다.
	 */
	@Transactional
	public CompanyKeyMetricEntity getOrCreateKeyMetric(Long companyId, int requestedQuarterKey) {
		Optional<CompanyKeyMetricEntity> existing = companyKeyMetricRepository
			.findByCompanyIdAndQuarter_QuarterKey(companyId, requestedQuarterKey);
		if (existing.isPresent()) {
			return existing.get();
		}

		ensureHealthScoreCached(companyId, requestedQuarterKey);

		return companyKeyMetricRepository.findByCompanyIdAndQuarter_QuarterKey(companyId, requestedQuarterKey)
			.orElseGet(() -> createFallbackKeyMetric(companyId, requestedQuarterKey));
	}

	private boolean hasHealthScore(CompanyKeyMetricEntity entity) {
		return entity.getInternalHealthScore() != null && entity.getRiskLevel() != null;
	}

	private boolean shouldUpdate(CompanyKeyMetricEntity entity) {
		return entity.getInternalHealthScore() == null
			|| entity.getRiskLevel() == null
			|| entity.getCompositeScore() == null;
	}

	private CompanyKeyMetricRiskLevel mapRiskLevel(String label) {
		if (label == null || label.isBlank()) {
			return CompanyKeyMetricRiskLevel.WARN;
		}
		String normalized = label.trim().toUpperCase();
		if (normalized.contains("안정") || normalized.contains("SAFE")) {
			return CompanyKeyMetricRiskLevel.SAFE;
		}
		if (normalized.contains("주의") || normalized.contains("WARN")) {
			return CompanyKeyMetricRiskLevel.WARN;
		}
		if (normalized.contains("위험") || normalized.contains("RISK")) {
			return CompanyKeyMetricRiskLevel.RISK;
		}
		log.warn("Unknown health score label: {}", label);
		return CompanyKeyMetricRiskLevel.WARN;
	}

	private CompanyKeyMetricEntity createFallbackKeyMetric(Long companyId, int quarterKey) {
		CompaniesEntity company = companiesRepository.findById(companyId)
			.orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));
		QuartersEntity quarter = getOrCreateQuarter(quarterKey);
		
		return GetOrCreateResolver.resolve(
			() -> companyKeyMetricRepository.findByCompanyIdAndQuarterId(companyId, quarter.getId()),
			() -> companyKeyMetricRepository.save(CompanyKeyMetricEntity.create(
				company,
				quarter,
				null,
				null,
				null,
				null,
				CompanyKeyMetricRiskLevel.WARN,
				DEFAULT_CALCULATION_LOGIC_VER,
				LocalDateTime.now()
			)),
			() -> companyKeyMetricRepository.findByCompanyIdAndQuarterId(companyId, quarter.getId())
		);
	}

	private QuartersEntity getOrCreateQuarter(int quarterKey) {
		YearQuarter yearQuarter = QuarterCalculator.parseQuarterKey(quarterKey);
		return GetOrCreateResolver.resolve(
			() -> quartersRepository.findByYearAndQuarter((short) yearQuarter.year(), (byte) yearQuarter.quarter()),
			() -> quartersRepository.save(QuartersEntity.create(
				yearQuarter.year(),
				yearQuarter.quarter(),
				quarterKey,
				QuarterCalculator.startDate(yearQuarter),
				QuarterCalculator.endDate(yearQuarter)
			)),
			() -> quartersRepository.findByYearAndQuarter((short) yearQuarter.year(), (byte) yearQuarter.quarter())
		);
	}

	private int parseQuarterKey(String period) {
		try {
			String normalized = normalizeQuarterKey(period);
			int parsed = Integer.parseInt(normalized);
			YearQuarter yearQuarter = QuarterCalculator.parseQuarterKey(parsed);
			return yearQuarter.toQuarterKey();
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("period는 숫자 형식이어야 합니다.", e);
		}
	}

	private String normalizeQuarterKey(String period) {
		String trimmed = period.trim();
		if (trimmed.length() == 6) {
			String yearPart = trimmed.substring(0, 4);
			String quarterPart = trimmed.substring(4, 6);
			if (quarterPart.startsWith("0")) {
				quarterPart = quarterPart.substring(1);
			}
			return yearPart + quarterPart;
		}
		return trimmed;
	}
}
