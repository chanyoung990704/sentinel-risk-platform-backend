package com.aivle.project.common.config;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/** 내부 클래스를 공개하지 않고 테스트 조립 지점에서 실제 어댑터를 선택한다. */
public class JpaReadAdapters implements ImportSelector {

	@Override
	public String[] selectImports(AnnotationMetadata metadata) {
		return new String[] {
			"com.aivle.project.app.internal.common.config.QuerydslConfig",
			"com.aivle.project.finance.internal.query.WatchlistFinancialQueryService",
			"com.aivle.project.finance.internal.CompanyDirectoryService",
			"com.aivle.project.finance.internal.FinancialReferenceService",
			"com.aivle.project.finance.internal.DashboardFinancialService",
			"com.aivle.project.identity.internal.IdentityQueryService",
			"com.aivle.project.storage.internal.FileCatalogService",
			"com.aivle.project.watchlist.internal.WatchlistMembershipService"
		};
	}
}
