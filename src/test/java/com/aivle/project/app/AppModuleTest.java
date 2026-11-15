package com.aivle.project.app;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

/** 외부 업무 API만 대체하고 현재 모듈의 실제 빈을 독립 기동한다. */
@Tag("modulith")
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)

class AppModuleTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter jwtConverter;
    @Autowired ApplicationContext context;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.data.redis.core.StringRedisTemplate redis;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.aiintegration.api.AiJobs aiJobs;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.community.api.CategoryQueries categoryQueries;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.AiCommentOperations aiCommentOperations;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.CompanyDirectory companyDirectory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.CompanyOverviewQueries companyOverviewQueries;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.CompanySearchQueries companySearchQueries;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.DashboardFinancialQueries dashboardFinancialQueries;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.FinancialReferenceQueries financialReferenceQueries;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.HealthScoreWarmup healthScoreWarmup;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.LegacyFinancialAi legacyFinancialAi;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.LegacyReportStatuses legacyReportStatuses;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.PredictionWarmup predictionWarmup;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.ReputationOperations reputationOperations;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.SignalWarmup signalWarmup;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.identity.api.AdminUserQueries adminUserQueries;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.identity.api.TurnstileVerification turnstileVerification;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.insight.api.CompanyInsights companyInsights;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.insight.api.ReportAnalysisOperations reportAnalysisOperations;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.news.api.NewsOperations newsOperations;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.storage.api.FileStreamService fileStreamService;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.watchlist.api.WatchlistMemberships watchlistMemberships;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.watchlist.api.WatchlistOperations watchlistOperations;
    @Test void startsWithPublicPortsOnly() {
        com.aivle.project.architecture.ModuleBootSupport.assertOwnEntities(context, "app");
        // given / when: STANDALONE 모듈 컨텍스트를 기동한다.
        // then: 자신의 공개 API는 실제 구현이다.
        assertThat(context).isNotNull();
    }
}