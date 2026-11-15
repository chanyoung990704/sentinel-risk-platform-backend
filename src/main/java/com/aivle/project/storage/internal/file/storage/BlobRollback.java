package com.aivle.project.storage.internal.file.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** DB 트랜잭션에서 새로 생성한 blob만 롤백 시 보상한다. */
final class BlobRollback {
	private static final Logger LOG = LoggerFactory.getLogger(BlobRollback.class);
	@FunctionalInterface interface Cleanup { void run() throws Exception; }
	private BlobRollback() {}

	static void register(Cleanup cleanup) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()
			|| !TransactionSynchronizationManager.isSynchronizationActive()) return;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override public void afterCompletion(int status) {
				if (status == STATUS_ROLLED_BACK) cleanup(cleanup);
			}
		});
	}

	static void cleanup(Cleanup cleanup) {
		try { cleanup.run(); }
		catch (Exception failure) {
			// 프로세스 종료·스토리지 장애는 자동 보상을 보장하지 못하므로 운영 점검 대상으로 남긴다.
			LOG.error("신규 blob 보상 삭제 실패: 고아 파일 점검이 필요합니다", failure);
		}
	}
}
