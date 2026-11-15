package com.aivle.project.finance.api;

import com.aivle.project.storage.api.FileMetadata;

/**
 * AI 리포트 파일 저장 응답 DTO.
 */
public record AiReportFileResponse(
    Long fileId,
    String storageUrl,
    String originalFilename,
    long fileSize,
    String contentType
) {

    public static AiReportFileResponse from(FileMetadata file) {
        return new AiReportFileResponse(
            file.getId(),
            file.getStorageUrl(),
            file.getOriginalFilename(),
            file.getFileSize(),
            file.getContentType()
        );
    }
}
