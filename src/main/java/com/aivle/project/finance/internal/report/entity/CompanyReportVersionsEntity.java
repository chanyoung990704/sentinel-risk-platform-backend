package com.aivle.project.finance.internal.report.entity;

import com.aivle.project.foundation.api.entity.BaseEntity;
import com.aivle.project.storage.api.FileMetadata;
import com.aivle.project.storage.api.FileMetadataSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * company_report_versions 테이블에 매핑되는 보고서 버전 엔티티.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "company_report_versions")
public class CompanyReportVersionsEntity extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_report_id", nullable = false)
	private CompanyReportsEntity companyReport;

	@Column(name = "pdf_file_id")
	private Long pdfFileId;

	@jakarta.persistence.Transient
	private FileMetadata pdfFile;

	public void resolvePdfFile(FileMetadata file) { this.pdfFile = FileMetadataSnapshot.copyOf(file); }

	@Column(name = "version_no", nullable = false)
	private int versionNo;

	// NULL은 기존 일반 보고서, 값이 있으면 검증/활성화 대상으로 격리된 버전이다.
	@Column(name = "data_snapshot_id", length = 36, columnDefinition = "CHAR(36)")
	private String dataSnapshotId;

	@Column(name = "generated_at", nullable = false)
	private LocalDateTime generatedAt;

	@Column(name = "is_published", nullable = false)
	private boolean published;

	/**
	 * 보고서 버전 생성.
	 */
	public static CompanyReportVersionsEntity create(
		CompanyReportsEntity companyReport,
		int versionNo,
		LocalDateTime generatedAt,
		boolean published,
		FileMetadata pdfFile
	) {
		CompanyReportVersionsEntity version = new CompanyReportVersionsEntity();
		version.companyReport = companyReport;
		version.versionNo = versionNo;
		version.generatedAt = generatedAt;
		version.published = published;
		version.pdfFile = FileMetadataSnapshot.copyOf(pdfFile);
		version.pdfFileId = pdfFile == null ? null : pdfFile.getId();
		return version;
	}

	/**
	 * PDF를 연결하고 발행 상태로 전환한다.
	 */
	public void publishWithPdf(FileMetadata pdfFile) {
		this.pdfFile = FileMetadataSnapshot.copyOf(pdfFile);
		this.pdfFileId = pdfFile == null ? null : pdfFile.getId();
		this.published = true;
	}

	/**
	 * PDF만 연결한다.
	 */
	public void attachPdf(FileMetadata pdfFile) {
		this.pdfFile = FileMetadataSnapshot.copyOf(pdfFile);
		this.pdfFileId = pdfFile == null ? null : pdfFile.getId();
	}

	/**
	 * 발행 상태로 전환한다.
	 */
	public void publish() {
		this.published = true;
	}
}
