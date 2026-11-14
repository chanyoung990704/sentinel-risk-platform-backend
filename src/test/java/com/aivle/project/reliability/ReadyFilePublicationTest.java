package com.aivle.project.reliability;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadyFilePublicationTest {

	@TempDir Path root;

	@Test void incompleteIdentityIsNotVisibleAtTheFileCreationBoundary() throws Exception {
		// given
		Path ready=root.resolve("ready.json");String identity="{\"pid\":123,\"port\":18080,\"nonce\":\"owned\"}";
		// when: 파일 생성 직후 본문 기록 전의 경계를 결정적으로 관측한다.
		ReportDeliveryApplicationProcess.publishReady(ready,identity,created->{
			assertThat(created).exists();assertThat(ready).doesNotExist();
		});
		// then
		assertThat(Files.readString(ready)).isEqualTo(identity);
	}

	@Test void existingReadyIdentityIsNotOverwritten() throws Exception {
		// given
		Path ready=root.resolve("ready.json");Files.writeString(ready,"previous");
		// when / then
		assertThatThrownBy(()->ReportDeliveryApplicationProcess.publishReady(ready,"next",created->{})).isInstanceOf(FileAlreadyExistsException.class);
		assertThat(Files.readString(ready)).isEqualTo("previous");
	}

	@Test void operationsIdentityIsNotVisibleBeforeItsBodyIsWritten() throws Exception {
		// given
		Path ready=root.resolve("operations-ready.json");String identity="{\"pid\":123,\"controlPort\":18080,\"nonce\":\"owned\"}";
		// when
		PortfolioOperationsProcess.publishReady(ready,identity,created->{assertThat(created).exists();assertThat(ready).doesNotExist();});
		// then
		assertThat(Files.readString(ready)).isEqualTo(identity);
	}
}
