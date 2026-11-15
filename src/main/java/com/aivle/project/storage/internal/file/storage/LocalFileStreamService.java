package com.aivle.project.storage.internal.file.storage;
import com.aivle.project.storage.api.FileStreamService;

import com.aivle.project.storage.api.FileMetadata;
import com.aivle.project.storage.api.FileErrorCode;
import com.aivle.project.storage.api.FileException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 로컬 파일 스트리밍 서비스 (prod 제외).
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class LocalFileStreamService implements FileStreamService {

	@Override
	public InputStream openStream(FileMetadata file) {
		if (file == null || !StringUtils.hasText(file.getStorageUrl())) {
			throw new FileException(FileErrorCode.FILE_404_NOT_FOUND);
		}

		Path path = Path.of(file.getStorageUrl());
		if (!Files.exists(path)) {
			throw new FileException(FileErrorCode.FILE_404_NOT_FOUND);
		}

		try {
			return Files.newInputStream(path);
		} catch (IOException ex) {
			throw new FileException(FileErrorCode.FILE_500_STORAGE);
		}
	}
}
