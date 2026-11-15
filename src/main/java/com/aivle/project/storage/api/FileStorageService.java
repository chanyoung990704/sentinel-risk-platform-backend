package com.aivle.project.storage.api;
import com.aivle.project.storage.api.StoredFile;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 인터페이스.
 */
public interface FileStorageService {

	StoredFile store(MultipartFile file, String keyPrefix);
}
