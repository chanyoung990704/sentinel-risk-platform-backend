package com.aivle.project.architecture;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** 모듈의 업무 구현을 가져오지 않는 테스트 전용 인프라 조립이다. */
@TestConfiguration(proxyBeanMethods = false)
public class ModuleBootSupport {
	public static void assertOwnEntities(org.springframework.context.ApplicationContext context, String module) {
		// 독립 기동이 다른 모듈의 엔티티 스캔으로 우회되지 않았는지 확인한다.
		var entities = context.getBean(jakarta.persistence.EntityManagerFactory.class).getMetamodel().getEntities();
		for (var entity : entities) {
			org.assertj.core.api.Assertions.assertThat(entity.getJavaType().getPackageName())
				.startsWith("com.aivle.project." + module + ".");
		}
	}
	@Bean JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
		return new JPAQueryFactory(entityManager);
	}
}
