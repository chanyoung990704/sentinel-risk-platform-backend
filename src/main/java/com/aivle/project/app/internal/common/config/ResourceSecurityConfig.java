package com.aivle.project.app.internal.common.config;

import com.aivle.project.app.internal.common.security.RestAuthenticationEntryPoint;
import com.aivle.project.app.internal.common.security.RestAccessDeniedHandler;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** 전체 앱의 경로·역할·CORS를 조립하며 JWT 개인 키/발급 저장소는 참조하지 않는다. */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class ResourceSecurityConfig {
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final Environment environment;
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;
	private static final String[] PUBLIC_RESOURCE_ENDPOINTS = {
		"/favicon.ico",
		"/css/**",
		"/js/**",
		"/images/**",
		"/webjars/**",
		"/assets/**",
		"/api-docs",
		"/api-docs/**",
		"/swagger-ui.html",
		"/swagger-ui/**",
		"/openapi/**",
		"/actuator/health",
		"/actuator/health/**",
		"/actuator/prometheus",
		"/error"
	};

	private static final String[] PUBLIC_AUTH_ENDPOINTS = {
		"/api/auth/login",
		"/api/auth/refresh",
		"/api/auth/signup",
		"/api/auth/verify-email"
	};

	private static final String[] PUBLIC_GET_ENDPOINTS = {
		"/api/posts/**",
		"/api/categories",
		"/api/companies/search"
	};

	private static final String[] AUTHENTICATED_POST_ENDPOINTS = {
		"/api/posts/**"
	};

	private static final String[] AUTHENTICATED_PATCH_ENDPOINTS = {
		"/api/posts/**"
	};

	private static final String[] AUTHENTICATED_DELETE_ENDPOINTS = {
		"/api/posts/**"
	};

	private static final String[] USER_GET_ENDPOINTS = {
		"/api/report-requests/*",
		"/api/report-requests/*/file",
		"/api/companies",
		"/api/companies/*/insights",
		"/api/companies/*/overview",
		"/api/dashboard/summary",
		"/api/dashboard/risk-records",
		"/api/watchlists/dashboard",
		"/api/watchlists/metric-averages",
		"/api/watchlists/metric-values",
		"/api/companies/*/ai-analysis",
		"/api/companies/*/ai-report/download",
		"/api/reports/metrics/grouped",
		"/api/reports/metrics/predict-latest",
		"/api/reports/files/*",
		"/api/reports/files/*/url"
	};

	private static final String[] USER_POST_ENDPOINTS = {
		"/api/companies/*/custom-report-requests",
		"/api/companies/*/news/refresh-latest",
		"/api/watchlists",
		"/api/companies/*/ai-report",
		"/api/auth/resend-verification"
	};

	private static final String[] USER_DELETE_ENDPOINTS = {
		"/api/watchlists/*"
	};

	@Bean
	public RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.fromHierarchy("""
			ROLE_ADMIN > ROLE_ANALYST
			ROLE_ANALYST > ROLE_USER
			""");
	}

	@Bean
	public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
		throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> {
				// 정적 리소스/문서/헬스체크 공개
				authorize.requestMatchers(PUBLIC_RESOURCE_ENDPOINTS).permitAll();
				authorize.requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll();
				authorize.requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll();

				// 게시글 작성/수정/삭제는 로그인 필요
				authorize.requestMatchers(HttpMethod.POST, AUTHENTICATED_POST_ENDPOINTS).authenticated();
				authorize.requestMatchers(HttpMethod.PATCH, AUTHENTICATED_PATCH_ENDPOINTS).authenticated();
				authorize.requestMatchers(HttpMethod.DELETE, AUTHENTICATED_DELETE_ENDPOINTS).authenticated();

				// 사용자 권한 API
				authorize.requestMatchers(HttpMethod.GET, USER_GET_ENDPOINTS).hasRole("USER");
				authorize.requestMatchers(HttpMethod.POST, USER_POST_ENDPOINTS).hasRole("USER");
				authorize.requestMatchers(HttpMethod.DELETE, USER_DELETE_ENDPOINTS).hasRole("USER");

				if (isDevProfile()) {
					authorize.requestMatchers("/dev/**").permitAll();
				}
				if (isPerfProfile()) {
					authorize.requestMatchers("/api/perf/**").permitAll();
					authorize.requestMatchers("/actuator/metrics", "/actuator/metrics/**").permitAll();
				}
				authorize.requestMatchers("/api/admin/**").hasRole("ADMIN");
				authorize.anyRequest().authenticated();
			})
			.headers(headers -> headers.contentSecurityPolicy(csp -> csp
				.policyDirectives(
					"script-src 'self' https://challenges.cloudflare.com; "
						+ "script-src-elem 'self' https://challenges.cloudflare.com; "
						+ "script-src-attr 'self'; "
						+ "frame-src https://challenges.cloudflare.com; "
						+ "connect-src 'self' https://challenges.cloudflare.com; "
						+ "object-src 'none'; "
						+ "base-uri 'self'"
				)
				.reportOnly()
			))
			.oauth2ResourceServer(oauth2 -> oauth2
				.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler)
			)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable);

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		List<String> origins = Arrays.stream(allowedOrigins.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isBlank())
			.toList();
		if (origins.isEmpty()) {
			origins = List.of("http://localhost:3000");
		}
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-CSRF-Token", "Idempotency-Key", "X-Run-Id"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private boolean isDevProfile() {
		return Arrays.asList(environment.getActiveProfiles()).contains("dev");
	}

	private boolean isPerfProfile() {
		return Arrays.asList(environment.getActiveProfiles()).contains("perf");
	}
}
