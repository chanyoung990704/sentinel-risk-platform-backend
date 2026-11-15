package com.aivle.project.storage.internal.file.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.project.storage.internal.file.config.FileUploadProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

class LocalStorageTransactionTest {
	@TempDir Path directory;

	@Test
	void blobAndMetadataAreRemovedWhenDatabaseTransactionRollsBack() {
		// given: 실제 JDBC 트랜잭션과 임시 로컬 파일을 사용한다.
		var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
		var jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("create table file_metadata (path varchar(1024))");
		var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
		var properties = new FileUploadProperties();
		properties.setUploadDir(directory.toString());
		var storage = new LocalFileStorageService(properties);
		var savedPath = new AtomicReference<Path>();
		// when
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			var file = storage.store(new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {1}), "posts/1");
			savedPath.set(Path.of(file.storageUrl()));
			jdbc.update("insert into file_metadata values (?)", file.storageUrl());
			throw new IllegalStateException("DB 후속 쓰기 실패");
		})).isInstanceOf(IllegalStateException.class);
		// then
		assertThat(jdbc.queryForObject("select count(*) from file_metadata", Integer.class)).isZero();
		assertThat(Files.exists(savedPath.get())).isFalse();
	}

	@Test
	void committedBlobRemainsReadable() throws Exception {
		// given
		var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
		var transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
		var properties = new FileUploadProperties();
		properties.setUploadDir(directory.toString());
		var storage = new LocalFileStorageService(properties);
		// when
		var stored = transaction.execute(status -> storage.store(
			new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[] {7, 8}), "posts/1"));
		// then
		assertThat(Files.readAllBytes(Path.of(stored.storageUrl()))).containsExactly(7, 8);
	}
}
