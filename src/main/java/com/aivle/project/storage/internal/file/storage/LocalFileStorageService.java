package com.aivle.project.storage.internal.file.storage;
import com.aivle.project.storage.api.StoredFile;
import com.aivle.project.storage.api.FileStorageService;

import com.aivle.project.storage.internal.file.config.FileUploadProperties;
import com.aivle.project.storage.api.FileErrorCode;
import com.aivle.project.storage.api.FileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 파일 저장소 (dev/local 전용).
 */
@Component
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

	private final FileUploadProperties properties;

	@Override
	public StoredFile store(MultipartFile file, String keyPrefix) {
		validateFile(file);
		String basePathValue = requireBasePath();
		String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
		String extension = resolveExtension(originalFilename);
		String storedName = UUID.randomUUID() + extension;

		Path basePath = Path.of(basePathValue).toAbsolutePath().normalize();
		Path targetDir = keyPrefix == null || keyPrefix.isBlank()
			? basePath
			: basePath.resolve(keyPrefix).normalize();
		Path target = targetDir.resolve(storedName).normalize();
		if (!targetDir.startsWith(basePath) || !target.getParent().equals(targetDir)) {
			throw new FileException(FileErrorCode.FILE_500_STORAGE);
		}
		boolean created = false;

		try {
			Files.createDirectories(targetDir);
			try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				created = true;
				try (var input = file.getInputStream()) { input.transferTo(output); }
			}
			BlobRollback.register(() -> Files.deleteIfExists(target));
			String storageUrl = target.toString().replace("\\", "/");
			return new StoredFile(storageUrl, originalFilename, file.getSize(), file.getContentType(), null);
		} catch (IOException ex) {
			if (created) BlobRollback.cleanup(() -> Files.deleteIfExists(target));
			throw new FileException(FileErrorCode.FILE_500_STORAGE);
		}
	}

	private void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new FileException(FileErrorCode.FILE_400_EMPTY);
		}
	}

	private String requireBasePath() {
		String basePath = properties.getUploadDir();
		if (!StringUtils.hasText(basePath)) {
			throw new FileException(FileErrorCode.FILE_500_STORAGE);
		}
		return basePath;
	}

	private String resolveExtension(String filename) {
		if (filename == null || filename.isBlank()) {
			return "";
		}
		int index = filename.lastIndexOf('.');
		if (index < 0 || index == filename.length() - 1) {
			return "";
		}
		return filename.substring(index);
	}
}
