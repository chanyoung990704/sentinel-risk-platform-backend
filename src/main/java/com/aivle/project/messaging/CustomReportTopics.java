package com.aivle.project.messaging;
import java.util.Set;
/** 업무 이벤트는 명시한 토픽 세 개로만 전달한다. */
public final class CustomReportTopics {
	private CustomReportTopics() { }
	public static final String REQUEST="sentinel.custom-report.request.v1";
	public static final String STATUS="sentinel.custom-report.status.v1";
	public static final String DLT="sentinel.custom-report.dlt.v1";
	public static final Set<String> ALLOWED=Set.of(REQUEST,STATUS,DLT);
}
