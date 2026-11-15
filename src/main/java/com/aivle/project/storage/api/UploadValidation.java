package com.aivle.project.storage.api;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface UploadValidation {
	void validateMultiple(List<MultipartFile> files);
	void validate(MultipartFile file);
}
