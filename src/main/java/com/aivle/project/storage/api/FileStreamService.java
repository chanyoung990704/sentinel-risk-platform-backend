package com.aivle.project.storage.api;

import com.aivle.project.storage.api.FileMetadata;
import java.io.InputStream;

/**
 * 파일 스트리밍 서비스.
 */
public interface FileStreamService {

	InputStream openStream(FileMetadata file);
}
