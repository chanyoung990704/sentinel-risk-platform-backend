package com.aivle.project.finance.internal.report.service;

import com.aivle.project.finance.internal.company.entity.CompaniesEntity;
import com.aivle.project.finance.internal.company.repository.CompaniesRepository;
import com.aivle.project.foundation.api.util.GetOrCreateResolver;
import com.aivle.project.storage.api.FileUsageType;
import com.aivle.project.storage.api.FileMetadata;
import com.aivle.project.storage.api.FileErrorCode;
import com.aivle.project.storage.api.FileException;
import com.aivle.project.storage.api.FileCatalog;
import com.aivle.project.storage.api.FileStorageService;
import com.aivle.project.storage.api.StoredFile;
import com.aivle.project.storage.api.UploadValidation;
import com.aivle.project.finance.internal.quarter.entity.QuartersEntity;
import com.aivle.project.finance.internal.quarter.repository.QuartersRepository;
import com.aivle.project.finance.api.QuarterCalculator;
import com.aivle.project.finance.api.YearQuarter;
import com.aivle.project.finance.api.ReportPdfPublishResult;
import com.aivle.project.finance.internal.report.entity.CompanyReportVersionsEntity;
import com.aivle.project.finance.internal.report.entity.CompanyReportsEntity;
import com.aivle.project.finance.internal.report.repository.CompanyReportVersionsRepository;
import com.aivle.project.finance.internal.report.repository.CompanyReportsRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 보고서 PDF 단일 업로드 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyReportPdfPublishService {

	private final CompaniesRepository companiesRepository;
	private final QuartersRepository quartersRepository;
	private final CompanyReportsRepository companyReportsRepository;
	private final CompanyReportVersionsRepository companyReportVersionsRepository;
	private final FileStorageService fileStorageService;
	private final UploadValidation fileValidator;
	private final FileCatalog filesRepository;
	private final CompanyReportVersionIssueService companyReportVersionIssueService;

	@Transactional
	public ReportPdfPublishResult publishPdfOnly(String stockCode, int quarterKey, MultipartFile pdfFile) {
		String normalizedStockCode = normalizeStockCode(stockCode);
		if (normalizedStockCode.isBlank()) {
			log.info("PDF 업로드 스킵: 기업 코드 누락");
			return new ReportPdfPublishResult(0, 1, null, null);
		}

		Optional<CompaniesEntity> company = companiesRepository.findByStockCode(normalizedStockCode);
		if (company.isEmpty()) {
			log.info("PDF 업로드 스킵: 기업 코드 미존재 (stockCode={})", normalizedStockCode);
			return new ReportPdfPublishResult(0, 1, null, null);
		}

		validatePdf(pdfFile);

		YearQuarter baseQuarter = QuarterCalculator.parseQuarterKey(quarterKey);
		QuartersEntity quarter = getOrCreateQuarter(quarterKey, baseQuarter);

		CompanyReportsEntity report = getOrCreateReport(company.get(), quarter);

		CompanyReportVersionsEntity version = resolvePdfVersion(report);
		FileMetadata pdfEntity = savePdf(report, version, pdfFile);
		version.attachPdf(pdfEntity);
		companyReportVersionsRepository.save(version);

		log.info(
			"PDF 업로드 완료: stockCode={}, quarterKey={}, versionNo={}",
			normalizedStockCode,
			quarterKey,
			version.getVersionNo()
		);
		return new ReportPdfPublishResult(1, 0, version.getVersionNo(), pdfEntity.getId());
	}

	private CompanyReportVersionsEntity resolvePdfVersion(CompanyReportsEntity report) {
		return companyReportVersionsRepository.findTopByCompanyReportAndPublishedFalseOrderByVersionNoDesc(report)
			.filter(existing -> existing.getPdfFileId() == null)
			.orElseGet(() -> createNewVersion(report));
	}

	private CompanyReportVersionsEntity createNewVersion(CompanyReportsEntity report) {
		return companyReportVersionIssueService.issueNextVersion(report, false, null);
	}

	private CompanyReportsEntity getOrCreateReport(CompaniesEntity company, QuartersEntity quarter) {
		return GetOrCreateResolver.resolve(
			() -> companyReportsRepository.findByCompanyIdAndQuarterId(company.getId(), quarter.getId()),
			() -> companyReportsRepository.save(CompanyReportsEntity.create(company, quarter, null)),
			() -> companyReportsRepository.findByCompanyIdAndQuarterId(company.getId(), quarter.getId())
		);
	}

	private QuartersEntity getOrCreateQuarter(int quarterKey, YearQuarter yearQuarter) {
		return GetOrCreateResolver.resolve(
			() -> quartersRepository.findByQuarterKey(quarterKey),
			() -> quartersRepository.save(QuartersEntity.create(
				yearQuarter.year(),
				yearQuarter.quarter(),
				quarterKey,
				QuarterCalculator.startDate(yearQuarter),
				QuarterCalculator.endDate(yearQuarter)
			)),
			() -> quartersRepository.findByQuarterKey(quarterKey)
		);
	}

	private FileMetadata savePdf(CompanyReportsEntity report, CompanyReportVersionsEntity version, MultipartFile file) {
		String keyPrefix = "reports/" + report.getId() + "/v" + version.getVersionNo();
		StoredFile stored = fileStorageService.store(file, keyPrefix);
		FileMetadata entity = FileMetadata.create(
			FileUsageType.REPORT_PDF,
			stored.storageUrl(),
			stored.storageKey(),
			stored.originalFilename(),
			stored.fileSize(),
			stored.contentType()
		);
		return filesRepository.save(entity);
	}

	private void validatePdf(MultipartFile file) {
		fileValidator.validate(file);
		String contentType = file.getContentType();
		if (contentType == null || !"application/pdf".equalsIgnoreCase(contentType)) {
			throw new FileException(FileErrorCode.FILE_400_CONTENT_TYPE);
		}
		String filename = file.getOriginalFilename();
		if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
			throw new FileException(FileErrorCode.FILE_400_EXTENSION);
		}
	}

	private String normalizeStockCode(String stockCode) {
		if (stockCode == null) {
			return "";
		}
		String trimmed = stockCode.trim();
		if (trimmed.isBlank()) {
			return "";
		}
		if (trimmed.length() < 6) {
			return "0".repeat(6 - trimmed.length()) + trimmed;
		}
		return trimmed;
	}
}
