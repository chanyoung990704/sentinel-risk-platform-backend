package com.aivle.project.news;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;

/** 외부 업무 API만 대체하고 현재 모듈의 실제 빈을 독립 기동한다. */
@Tag("modulith")
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
@org.springframework.context.annotation.Import(com.aivle.project.architecture.ModuleBootSupport.class)
class NewsModuleTest {
    @Autowired ApplicationContext context;
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.data.redis.core.StringRedisTemplate redis;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.aiintegration.api.NewsGateway newsGateway;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.CompanyDirectory companyDirectory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.aivle.project.finance.api.ReputationOperations reputationOperations;
    @Test void startsWithPublicPortsOnly() {
        com.aivle.project.architecture.ModuleBootSupport.assertOwnEntities(context, "news");
        // given / when: STANDALONE 모듈 컨텍스트를 기동한다.
        // then: 자신의 공개 API는 실제 구현이다.
        assertThat(org.mockito.Mockito.mockingDetails(context.getBean(com.aivle.project.news.api.NewsReadModel.class)).isMock()).isFalse();
    }
}