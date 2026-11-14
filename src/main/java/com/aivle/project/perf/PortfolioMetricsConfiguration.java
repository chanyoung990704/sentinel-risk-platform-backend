package com.aivle.project.perf;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/** 실제 데이터 계측은 명시적인 로컬 프로필에서만 허용한다. */
@Configuration(proxyBeanMethods = false)
@Profile("portfolio-perf")
public class PortfolioMetricsConfiguration {
	@Bean QueryCountProbe queryCountProbe(Environment environment) {
		if (!environment.matchesProfiles("local") || !"127.0.0.1".equals(environment.getProperty("server.address"))) {
			throw new IllegalStateException("portfolio-perf requires local loopback configuration");
		}
		return new QueryCountProbe();
	}
	@Bean FilterRegistrationBean<PortfolioMetrics> portfolioMetricsFilter(QueryCountProbe probe, MeterRegistry registry) {
		var registration = new FilterRegistrationBean<>(new PortfolioMetrics(probe,registry));
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
		return registration;
	}
}
