package com.aivle.project.app.internal.watchlist;

import com.aivle.project.finance.api.CompanyIdentity;
import com.aivle.project.finance.api.CompanyDirectory;
import com.aivle.project.finance.api.HealthScoreWarmup;
import com.aivle.project.finance.api.AiCommentOperations;
import com.aivle.project.finance.api.PredictionWarmup;
import com.aivle.project.finance.api.ReputationOperations;
import com.aivle.project.finance.api.SignalWarmup;
import com.aivle.project.aiintegration.api.AiJobs;
import com.aivle.project.aiintegration.api.ExternalAiUnavailableException;
import com.aivle.project.finance.api.FinancialReferenceQueries;
import com.aivle.project.watchlist.api.CompanyWatchlistCreatedEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
	@RequiredArgsConstructor
public class CompanyWatchlistAsyncHandler {

	private final CompanyDirectory companiesRepository;
	private final FinancialReferenceQueries companyReportMetricValuesRepository;
	private final HealthScoreWarmup companyHealthScoreCacheService;
	private final AiCommentOperations companyAiCommentService;
	private final PredictionWarmup companyPredictionCacheService;
	private final SignalWarmup companySignalCacheService;
	private final ReputationOperations companyReputationScoreService;
	private final AiJobs aiJobDispatchService;

	@Async("insightExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
		public void handleWatchlistCreated(CompanyWatchlistCreatedEvent event) {
			String stockCode = null;
			try {
				CompanyIdentity company = companiesRepository.findById(event.companyId()).orElse(null);
				if (company == null) {
					return;
				}
				stockCode = company.getStockCode();
				Integer latestActualQuarterKey = companyReportMetricValuesRepository
					.findMaxActualQuarterKeyByStockCode(stockCode)
					.orElse(null);
			if (latestActualQuarterKey == null) {
				return;
			}
			// 워치리스트 등록 후 기업 개요 캐시를 비동기로 선행 적재한다.
			companyHealthScoreCacheService.ensureHealthScoreCached(event.companyId(), latestActualQuarterKey);
			companyPredictionCacheService.ensurePredictionCached(event.companyId(), latestActualQuarterKey);
			companySignalCacheService.ensureSignalsCached(event.companyId(), latestActualQuarterKey);
				companyReputationScoreService.syncExternalHealthScoreIfPresent(event.companyId(), stockCode);
				boolean dispatched = aiJobDispatchService.dispatchCommentWarmup(
					java.util.UUID.randomUUID().toString(),
					event.companyId(),
					String.valueOf(latestActualQuarterKey)
				);
				if (!dispatched) {
					// 카프카 비활성/미설정 환경에서는 기존 동기 적재를 유지한다.
					companyAiCommentService.ensureAiCommentCached(event.companyId(), String.valueOf(latestActualQuarterKey));
				}
			} catch (Exception e) {
				String reasonCode = resolveReasonCode(e);
				log.warn(
					"워치리스트 캐시 선행 적재 실패: operation=watchlist-created-warmup, companyId={}, stockCode={}, reasonCode={}",
					event.companyId(),
					stockCode,
					reasonCode,
					e
				);
			}
		}

		private String resolveReasonCode(Throwable throwable) {
			if (throwable instanceof ExternalAiUnavailableException externalAiUnavailableException) {
				return externalAiUnavailableException.getReasonCode();
			}
			if (containsCause(throwable, CallNotPermittedException.class)) {
				return "AI_CIRCUIT_OPEN";
			}
			if (containsCause(throwable, io.netty.handler.timeout.ReadTimeoutException.class)
				|| containsCause(throwable, java.util.concurrent.TimeoutException.class)) {
				return "AI_TIMEOUT";
			}
			return "AI_UNAVAILABLE";
		}

		private boolean containsCause(Throwable throwable, Class<? extends Throwable> type) {
			Throwable current = throwable;
			while (current != null) {
				if (type.isInstance(current)) {
					return true;
				}
				current = current.getCause();
			}
			return false;
		}
	}
