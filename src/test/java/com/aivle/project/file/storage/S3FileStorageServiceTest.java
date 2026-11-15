package com.aivle.project.storage.internal.file.storage;
import com.aivle.project.storage.api.StoredFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aivle.project.storage.internal.file.config.FileStorageProperties;
import com.aivle.project.storage.api.FileErrorCode;
import com.aivle.project.storage.api.FileException;
import java.net.URL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3FileStorageServiceTest {
	@Test
	void successfulPutStillCompensatesWhenClosingInputFails() throws Exception {
		// given: 업로드는 성공했지만 입력 스트림 종료가 실패한다.
		var client=org.mockito.Mockito.mock(S3Client.class);
		var properties=new FileStorageProperties();properties.getS3().setBucket("fixture-bucket");
		var file=new MockMultipartFile("file","test.pdf","application/pdf",new byte[]{1}) {
			@Override public java.io.InputStream getInputStream() {
				return new java.io.ByteArrayInputStream(new byte[]{1}) {
					@Override public void close() throws java.io.IOException { throw new java.io.IOException("fixture close failure"); }
				};
			}
		};
		org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
		org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
		try {
			// when
			assertThatThrownBy(()->new S3FileStorageService(client,properties).store(file,"posts/1")).isInstanceOf(FileException.class);
			for(var callback:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())
				callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
			// then
			verify(client).deleteObject(any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class));
		} finally {
			org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
			org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}
	@Test
	void rollbackDeletesOnlyTheNewObjectThroughMockS3() throws Exception {
		// given: 실제 S3에 연결하지 않고 보상 요청의 bucket/key를 확인한다.
		var client = org.mockito.Mockito.mock(S3Client.class);
		var utilities = org.mockito.Mockito.mock(S3Utilities.class);
		var properties = new FileStorageProperties();
		properties.getS3().setBucket("fixture-bucket");
		when(client.utilities()).thenReturn(utilities);
		when(utilities.getUrl(any(GetUrlRequest.class))).thenReturn(java.net.URI.create("https://example.com/file").toURL());
		org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
		org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
		try {
			// when
			var stored = new S3FileStorageService(client, properties).store(
				new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {1}), "posts/1");
			for (var callback : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations())
				callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
			// then
			var delete = ArgumentCaptor.forClass(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class);
			verify(client).deleteObject(delete.capture());
			assertThat(delete.getValue().bucket()).isEqualTo("fixture-bucket");
			assertThat(delete.getValue().key()).isEqualTo(stored.storageKey());
		} finally {
			org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
			org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}

	@Test
	@DisplayName("S3 저장소에 파일 업로드 요청을 보낸다")
	void store_shouldUploadToS3() throws Exception {
		// given
		S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
		S3Utilities utilities = org.mockito.Mockito.mock(S3Utilities.class);

		FileStorageProperties properties = new FileStorageProperties();
		properties.getS3().setBucket("bucket");
		properties.getS3().setRegion("ap-northeast-2");
		properties.getS3().setPrefix("uploads");

		when(s3Client.utilities()).thenReturn(utilities);
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());
		when(utilities.getUrl(any(GetUrlRequest.class)))
			.thenReturn(new URL("https://example.com/file"));

		S3FileStorageService storageService = new S3FileStorageService(s3Client, properties);
		MultipartFile file = new MockMultipartFile(
			"file",
			"image.png",
			"image/png",
			new byte[] {1, 2, 3}
		);

		// when
		StoredFile stored = storageService.store(file, "posts/1");

		// then
		ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
		String key = requestCaptor.getValue().key();
		assertThat(key).startsWith("uploads/posts/1/");
		assertThat(key).endsWith(".png");
		assertThat(stored.storageUrl()).isEqualTo("https://example.com/file");
		assertThat(stored.storageKey()).isEqualTo(key);
	}

	@Test
	@DisplayName("빈 파일은 업로드할 수 없다")
	void store_shouldFailWhenFileEmpty() {
		// given
		S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
		FileStorageProperties properties = new FileStorageProperties();
		properties.getS3().setBucket("bucket");
		properties.getS3().setRegion("ap-northeast-2");

		S3FileStorageService storageService = new S3FileStorageService(s3Client, properties);
		MultipartFile file = new MockMultipartFile(
			"file",
			"image.png",
			"image/png",
			new byte[] {}
		);

		// when & then
		assertThatThrownBy(() -> storageService.store(file, "posts/1"))
			.isInstanceOf(FileException.class)
			.extracting(ex -> ((FileException) ex).getErrorCode())
			.isEqualTo(FileErrorCode.FILE_400_EMPTY);
	}
}
