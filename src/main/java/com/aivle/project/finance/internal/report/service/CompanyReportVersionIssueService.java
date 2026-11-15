package com.aivle.project.finance.internal.report.service;

import com.aivle.project.storage.api.FileMetadata;
import com.aivle.project.finance.internal.report.entity.CompanyReportVersionsEntity;
import com.aivle.project.finance.internal.report.entity.CompanyReportsEntity;
import com.aivle.project.finance.internal.report.repository.CompanyReportVersionsRepository;
import com.aivle.project.finance.internal.report.repository.CompanyReportsRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 보고서 버전 발급 동시성 제어를 공통화한다.
 */
@Service
@RequiredArgsConstructor
public class CompanyReportVersionIssueService {

	private final CompanyReportsRepository companyReportsRepository;
	private final CompanyReportVersionsRepository companyReportVersionsRepository;

	public CompanyReportVersionsEntity issueNextVersion(
		CompanyReportsEntity report,
		boolean published,
		FileMetadata pdfFile
	) {
		CompanyReportsEntity lockedReport = lockReport(report);
		int nextVersionNo = companyReportVersionsRepository.findTopByCompanyReportOrderByVersionNoDesc(lockedReport)
			.map(existing -> existing.getVersionNo() + 1)
			.orElse(1);

		CompanyReportVersionsEntity version = CompanyReportVersionsEntity.create(
			lockedReport,
			nextVersionNo,
			LocalDateTime.now(),
			published,
			pdfFile
		);
		return companyReportVersionsRepository.save(version);
	}

	private CompanyReportsEntity lockReport(CompanyReportsEntity report) {
		if (report.getId() == null) {
			return report;
		}
		return companyReportsRepository.findByIdForUpdate(report.getId()).orElse(report);
	}
}
