package com.aivle.project.reporting.api;
import java.util.UUID;
public interface ReportingQueries {
	ReportRequestView getOwned(Long userId,UUID requestId);
	byte[] openOwnedFile(Long userId,UUID requestId);
}
