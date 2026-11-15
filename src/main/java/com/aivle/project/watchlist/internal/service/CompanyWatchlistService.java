package com.aivle.project.watchlist.internal.service;

import com.aivle.project.finance.api.CompanyIdentity;
import com.aivle.project.finance.api.CompanyView;
import com.aivle.project.finance.api.CompanyDirectory;
import com.aivle.project.finance.api.CompanyInformationBatches;
import com.aivle.project.foundation.api.error.CommonException;
import com.aivle.project.finance.api.MetricValueType;
import com.aivle.project.finance.api.QuarterIdentity;
import com.aivle.project.finance.api.FinancialReferenceQueries;
import com.aivle.project.finance.api.RiskLevel;
import com.aivle.project.identity.api.CurrentActor;
import com.aivle.project.finance.api.WatchlistDashboardMetricRow;
import com.aivle.project.watchlist.api.WatchlistDashboardResponse;
import com.aivle.project.finance.api.WatchlistDashboardRiskRow;
import com.aivle.project.finance.api.WatchlistMetricAverageRow;
import com.aivle.project.watchlist.api.WatchlistMetricAveragesResponse;
import com.aivle.project.finance.api.WatchlistMetricValueRow;
import com.aivle.project.watchlist.api.WatchlistMetricValuesResponse;
import com.aivle.project.watchlist.api.WatchlistQuarterMetricValues;
import com.aivle.project.watchlist.api.WatchlistItem;
import com.aivle.project.watchlist.api.WatchlistResponse;
import com.aivle.project.watchlist.internal.entity.CompanyWatchlistEntity;
import com.aivle.project.watchlist.api.WatchlistErrorCode;
import com.aivle.project.watchlist.internal.repository.CompanyWatchlistRepository;
import com.aivle.project.finance.api.WatchlistMetricValueProjection;
import com.aivle.project.watchlist.api.CompanyWatchlistCreatedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyWatchlistService implements com.aivle.project.watchlist.api.WatchlistOperations {

	private final CompanyWatchlistRepository companyWatchlistRepository;
	private final CompanyDirectory companiesRepository;
	private final FinancialReferenceQueries financialReferences;
	private final CompanyInformationBatches companyInfoService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void addWatchlist(Long userId, Long companyId, String note) {
		if (companyWatchlistRepository.findByUserIdAndCompanyIdAndDeletedAtIsNull(userId, companyId).isPresent()) {
			throw new CommonException(WatchlistErrorCode.WATCHLIST_DUPLICATE);
		}
		CompanyWatchlistEntity deleted = companyWatchlistRepository
			.findByUserIdAndCompanyIdAndDeletedAtIsNotNull(userId, companyId)
			.orElse(null);
		if (deleted != null) {
			// 소프트 삭제된 동일 항목은 복구하여 재등록한다.
			deleted.restore();
			deleted.updateNote(note);
			eventPublisher.publishEvent(new CompanyWatchlistCreatedEvent(userId, companyId));
			return;
		}
		var user = new CurrentActor(userId,null,null);
		var company = new CompanyView(companyId,null,null,null);
		try {
			companyWatchlistRepository.saveAndFlush(CompanyWatchlistEntity.create(user, company, note));
			eventPublisher.publishEvent(new CompanyWatchlistCreatedEvent(userId, companyId));
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			throw new CommonException(WatchlistErrorCode.WATCHLIST_DUPLICATE);
		}
	}

	@Transactional
	public void removeWatchlist(Long userId, Long companyId) {
		CompanyWatchlistEntity existing = companyWatchlistRepository.findByUserIdAndCompanyIdAndDeletedAtIsNull(userId, companyId)
			.orElseThrow(() -> new CommonException(WatchlistErrorCode.WATCHLIST_NOT_FOUND));
		if (!existing.getUser().getId().equals(userId)) {
			throw new CommonException(WatchlistErrorCode.WATCHLIST_FORBIDDEN);
		}
		existing.delete();
	}

	@Transactional(readOnly = true)
	public WatchlistResponse getWatchlist(Long userId) {
		List<CompanyWatchlistEntity> watchlists = companyWatchlistRepository.findActiveByUserId(userId);
		resolveCompanies(watchlists);
		List<WatchlistItem> items = watchlists.stream()
			.map(cw -> new WatchlistItem(
				cw.getId(),
				cw.getCompany().getId(),
				cw.getCompany().getCorpName(),
				cw.getCompany().getCorpCode(),
				cw.getCompany().getStockCode(),
				cw.getNote(),
				cw.getCreatedAt()
			))
			.toList();
		return new WatchlistResponse(items);
	}

	@Transactional(readOnly = true)
	public List<com.aivle.project.finance.api.CompanyInfoDto> getWatchlistCompanies(Long userId) {
		List<CompanyWatchlistEntity> watchlists = companyWatchlistRepository.findActiveByUserId(userId);
		return companyInfoService.latest(watchlists.stream().map(CompanyWatchlistEntity::getCompanyId).toList());
	}

	@Transactional(readOnly = true)
	public WatchlistDashboardResponse getDashboard(
		Long userId,
		int year,
		int quarter,
		List<String> metricCodes,
		RiskLevel riskLevel
	) {
		QuarterIdentity quarterEntity = financialReferences.findByYearAndQuarter((short) year, (byte) quarter)
			.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 분기입니다."));
		List<String> codes = metricCodes == null ? List.of() : metricCodes.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
		boolean emptyCodes = codes.isEmpty();

		List<WatchlistDashboardMetricRow> metrics = companyWatchlistRepository.findDashboardMetrics(
			userId,
			(short) year,
			(byte) quarter,
			MetricValueType.ACTUAL,
			emptyCodes ? List.of("__none__") : codes,
			emptyCodes
		);

		List<WatchlistDashboardRiskRow> risks = companyWatchlistRepository.findDashboardRisks(
			userId,
			quarterEntity.getId(),
			riskLevel
		);

		return new WatchlistDashboardResponse(year, quarter, metrics, risks);
	}

	@Transactional(readOnly = true)
	public WatchlistMetricAveragesResponse getWatchlistMetricAverages(Long userId, int year, int quarter, List<String> metricCodes) {
		financialReferences.findByYearAndQuarter((short) year, (byte) quarter)
			.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 분기입니다."));
		List<String> codes = metricCodes == null ? List.of() : metricCodes.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
		boolean emptyCodes = codes.isEmpty();

		List<WatchlistMetricAverageRow> metrics = companyWatchlistRepository.findWatchlistMetricAverages(
			userId,
			(short) year,
			(byte) quarter,
			MetricValueType.ACTUAL,
			emptyCodes ? List.of("__none__") : codes,
			emptyCodes
		);

		return new WatchlistMetricAveragesResponse(year, quarter, metrics);
	}

	@Transactional(readOnly = true)
	public WatchlistMetricValuesResponse getWatchlistMetricValuesByQuarter(Long userId, int year, int quarter) {
		financialReferences.findByYearAndQuarter((short) year, (byte) quarter)
			.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 분기입니다."));

		List<WatchlistMetricValueProjection> rows = companyWatchlistRepository.findWatchlistMetricValues(
			userId,
			(short) year,
			(byte) quarter,
			MetricValueType.ACTUAL
		);

		return new WatchlistMetricValuesResponse(groupByQuarter(rows));
	}

	@Transactional(readOnly = true)
	public WatchlistMetricValuesResponse getWatchlistMetricValuesByQuarterRange(
		Long userId,
		int fromYear,
		int fromQuarter,
		int toYear,
		int toQuarter
	) {
		if (!isValidQuarterRange(fromYear, fromQuarter, toYear, toQuarter)) {
			throw new IllegalArgumentException("유효하지 않은 분기 범위입니다.");
		}

		List<WatchlistMetricValueProjection> rows = companyWatchlistRepository.findWatchlistMetricValuesInRange(
			userId,
			(short) fromYear,
			(byte) fromQuarter,
			(short) toYear,
			(byte) toQuarter,
			MetricValueType.ACTUAL
		);

		return new WatchlistMetricValuesResponse(groupByQuarter(rows));
	}

	private boolean isValidQuarterRange(int fromYear, int fromQuarter, int toYear, int toQuarter) {
		if (fromQuarter < 1 || fromQuarter > 4 || toQuarter < 1 || toQuarter > 4) {
			return false;
		}
		if (fromYear > toYear) {
			return false;
		}
		return fromYear != toYear || fromQuarter <= toQuarter;
	}

	private void resolveCompanies(List<CompanyWatchlistEntity> watchlists) {
		if(watchlists.isEmpty())return;
		var companies=companiesRepository.findAllById(watchlists.stream().map(CompanyWatchlistEntity::getCompanyId).distinct().toList())
			.stream().collect(java.util.stream.Collectors.toMap(CompanyIdentity::getId, company -> company));
		watchlists.forEach(watchlist -> {
			var company=companies.get(watchlist.getCompanyId());
			if(company==null)throw new IllegalStateException("관심기업의 기업 참조를 찾을 수 없습니다.");
			watchlist.resolveCompany(company);
		});
	}

	private List<WatchlistQuarterMetricValues> groupByQuarter(List<WatchlistMetricValueProjection> rows) {
		Map<String, WatchlistQuarterMetricValues> grouped = new LinkedHashMap<>();
		for (WatchlistMetricValueProjection row : rows) {
			String key = row.getYear() + "-" + row.getQuarter();
			WatchlistQuarterMetricValues bucket = grouped.get(key);
			if (bucket == null) {
				bucket = new WatchlistQuarterMetricValues(row.getYear(), row.getQuarter(), new ArrayList<>());
				grouped.put(key, bucket);
			}
			bucket.items().add(new WatchlistMetricValueRow(
				row.getWatchlistId(),
				row.getCompanyId(),
				row.getCorpName(),
				row.getCorpCode(),
				row.getMetricCode(),
				row.getMetricNameKo(),
				row.getMetricValue()
			));
		}
		return new ArrayList<>(grouped.values());
	}
}
