package com.aivle.project.perf;

import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.common.PreparedStatementInformation;
import com.p6spy.engine.common.StatementInformation;
import java.sql.SQLException;
import java.util.Set;

/** 요청 스레드의 JDBC 실행 수만 세며 SQL/바인딩 값은 보관하지 않는다. */
public class QueryCountProbe extends JdbcEventListener {
	public record Counts(long statements,long elapsedNanos,long errors) { }
	private static final Set<String> LABELS = Set.of("company_overview", "company_search", "company_list", "dashboard", "report_accept", "report_status");
	private final ThreadLocal<long[]> scope = new ThreadLocal<>();
	public void begin(String label) {
		if (!LABELS.contains(label)) throw new IllegalArgumentException("Unknown measurement endpoint");
		if (scope.get() != null) throw new IllegalStateException("Nested measurement scope");
		scope.set(new long[3]);
	}
	public Counts end() {
		long[] counts = scope.get();
		scope.remove();
		if (counts == null) throw new IllegalStateException("No measurement scope");
		return new Counts(counts[0], counts[1], counts[2]);
	}
	private void record(long nanos, SQLException error) {
		long[] counts = scope.get();
		if (counts != null) { counts[0]++; counts[1] += nanos; if (error != null) counts[2]++; }
	}
	// 배치는 JDBC executeBatch 호출 1회로 센다. SQL 문자열/바인딩은 읽지 않는다.
	@Override public void onAfterExecute(PreparedStatementInformation s,long n,SQLException e) {record(n,e);}
	@Override public void onAfterExecute(StatementInformation s,long n,String sql,SQLException e) {record(n,e);}
	@Override public void onAfterExecuteQuery(PreparedStatementInformation s,long n,SQLException e) {record(n,e);}
	@Override public void onAfterExecuteQuery(StatementInformation s,long n,String sql,SQLException e) {record(n,e);}
	@Override public void onAfterExecuteUpdate(PreparedStatementInformation s,long n,int rows,SQLException e) {record(n,e);}
	@Override public void onAfterExecuteUpdate(StatementInformation s,long n,String sql,int rows,SQLException e) {record(n,e);}
	@Override public void onAfterExecuteBatch(StatementInformation s,long n,int[] rows,SQLException e) {record(n,e);}
}
