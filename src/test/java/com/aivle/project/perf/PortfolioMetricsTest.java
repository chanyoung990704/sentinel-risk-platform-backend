package com.aivle.project.app.internal.perf;

import static org.assertj.core.api.Assertions.*;
import com.p6spy.engine.common.PreparedStatementInformation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletException;
import java.sql.SQLException;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class PortfolioMetricsTest {
	@Test void countsExecutionsBatchesAndErrorsWithoutReadingSql() {
		// given
		var probe=new QueryCountProbe();probe.begin("company_overview");
		// when
		probe.onAfterExecuteQuery((PreparedStatementInformation)null,10,null);
		probe.onAfterExecuteUpdate((PreparedStatementInformation)null,20,1,new SQLException("fixture"));
		probe.onAfterExecuteBatch(null,30,new int[]{1,1},null);
		// then
		assertThat(probe.end()).isEqualTo(new QueryCountProbe.Counts(3,60,1));
	}
	@Test void outsideQueriesAreIgnoredAndLabelsAreBounded() {
		// given / when
		var probe=new QueryCountProbe();probe.onAfterExecuteQuery((PreparedStatementInformation)null,10,null);
		probe.begin("company_overview");
		// then
		assertThat(probe.end().statements()).isZero();assertThatThrownBy(()->probe.begin("user-id-123")).isInstanceOf(IllegalArgumentException.class);
	}
	@Test void scopesAreIsolatedAcrossConcurrentThreads()throws Exception {
		// given
		var probe=new QueryCountProbe();var barrier=new CountDownLatch(2);var pool=Executors.newFixedThreadPool(2);
		try {
			Callable<Long> one=()->{probe.begin("company_overview");probe.onAfterExecuteQuery((PreparedStatementInformation)null,1,null);barrier.countDown();assertThat(barrier.await(3,TimeUnit.SECONDS)).isTrue();return probe.end().statements();};
			// when
			var a=pool.submit(one);var b=pool.submit(()->{probe.begin("company_search");probe.onAfterExecuteQuery((PreparedStatementInformation)null,1,null);probe.onAfterExecuteQuery((PreparedStatementInformation)null,1,null);barrier.countDown();assertThat(barrier.await(3,TimeUnit.SECONDS)).isTrue();return probe.end().statements();});
			// then
			assertThat(a.get(5,TimeUnit.SECONDS)).isEqualTo(1);assertThat(b.get(5,TimeUnit.SECONDS)).isEqualTo(2);
		} finally {pool.shutdownNow();}
	}
	@Test void httpHeadersAndRegistryUseActualRequestCount()throws Exception {
		// given
		var probe=new QueryCountProbe();var registry=new SimpleMeterRegistry();var filter=new PortfolioMetrics(probe,registry);
		var request=new MockHttpServletRequest("GET","/api/companies/1/overview");var response=new MockHttpServletResponse();
		// when
		filter.doFilter(request,response,(req,res)->{probe.onAfterExecuteQuery((PreparedStatementInformation)null,10,null);probe.onAfterExecuteQuery((PreparedStatementInformation)null,20,null);res.getWriter().write("{\"success\":true}");});
		// then
		assertThat(response.getHeader("X-Portfolio-Sql-Count")).isEqualTo("2");assertThat(response.getContentAsString()).contains("true");
		assertThat(registry.get("portfolio.jdbc.statements").tag("endpoint","company_overview").counter().count()).isEqualTo(2);
	}
	@Test void healthAndCollectionQueriesAreNotMeasured()throws Exception {
		// given
		var probe=new QueryCountProbe();var registry=new SimpleMeterRegistry();var filter=new PortfolioMetrics(probe,registry);var response=new MockHttpServletResponse();
		// when
		filter.doFilter(new MockHttpServletRequest("GET","/actuator/health"),response,(req,res)->probe.onAfterExecuteQuery((PreparedStatementInformation)null,10,null));
		// then
		assertThat(response.getHeader("X-Portfolio-Sql-Count")).isNull();assertThat(registry.getMeters()).isEmpty();
	}
	@Test void failedRequestReleasesThreadScope()throws Exception {
		// given
		var probe=new QueryCountProbe();var filter=new PortfolioMetrics(probe,new SimpleMeterRegistry());var request=new MockHttpServletRequest("GET","/api/companies/1/overview");
		// when / then
		assertThatThrownBy(()->filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{probe.onAfterExecuteQuery((PreparedStatementInformation)null,1,null);throw new ServletException("fixture");})).isInstanceOf(ServletException.class);
		probe.begin("company_overview");assertThat(probe.end().statements()).isZero();
	}
}
