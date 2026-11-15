package com.aivle.project.app.internal.perf;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** local 전용 계측 필터. 일반 실행 프로필에는 등록하지 않는다. */
public class PortfolioMetrics extends OncePerRequestFilter {
	private final QueryCountProbe probe;
	private final MeterRegistry registry;
	public PortfolioMetrics(QueryCountProbe probe,MeterRegistry registry) {this.probe=probe;this.registry=registry;}
	@Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
		String label = label(request);
		if (label == null) {chain.doFilter(request,response);return;}
		var wrapped = new org.springframework.web.util.ContentCachingResponseWrapper(response);
		long started = System.nanoTime();
		boolean failed = true;
		probe.begin(label);
		try {
			chain.doFilter(request,wrapped);
			failed = false;
		} finally {
			var counts = probe.end();
			wrapped.setHeader("X-Portfolio-Sql-Count",Long.toString(counts.statements()));
			wrapped.setHeader("X-Portfolio-Sql-Nanos",Long.toString(counts.elapsedNanos()));
			wrapped.setHeader("X-Portfolio-Sql-Errors",Long.toString(counts.errors()));
			registry.counter("portfolio.jdbc.statements","endpoint",label).increment(counts.statements());
			registry.counter("portfolio.jdbc.errors","endpoint",label).increment(counts.errors());
			registry.timer("portfolio.http.request","endpoint",label,"outcome", failed || wrapped.getStatus() >= 400 ? "error" : "success")
				.record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);
			wrapped.copyBodyToResponse();
		}
	}
	private String label(HttpServletRequest request) {
		String path = request.getRequestURI();
		if ("POST".equals(request.getMethod()) && path.matches("/api/companies/[0-9]+/custom-report-requests")) return "report_accept";
		if (!"GET".equals(request.getMethod())) return null;
		if (path.matches("/api/report-requests/[0-9a-fA-F-]{36}")) return "report_status";
		if (path.matches("/api/companies/[0-9]+/overview")) return "company_overview";
		if (path.equals("/api/companies/search")) return "company_search";
		if (path.equals("/api/companies")) return "company_list";
		if (path.equals("/api/dashboard")) return "dashboard";
		return null;
	}
}
