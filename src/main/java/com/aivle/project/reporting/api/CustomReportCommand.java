package com.aivle.project.reporting.api;
/** 클라이언트 입력. 실제 분기와 snapshot은 최초 접수 시 서버가 고정한다. */
public record CustomReportCommand(Integer targetQuarterKey,String additionalInstructions) { }
