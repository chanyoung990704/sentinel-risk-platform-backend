package com.aivle.project.app.internal.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {
	@Test
	void groupedDocumentationScansExistingControllerPackages() {
		// given
		var config = new OpenApiConfig("v1");
		// when / then
		assertThat(config.authApi().getPackagesToScan()).contains("com.aivle.project.identity.internal.auth.controller");
		assertThat(config.postApi().getPackagesToScan()).contains("com.aivle.project.community.internal.post.controller");
		assertThat(config.adminApi().getPackagesToScan()).contains("com.aivle.project.app.internal.company.controller");
	}

	@Test
	@DisplayName("OpenAPI 서버 URL은 상대 경로(/)로 고정한다")
	void openApiServerUrlShouldBeRelativePath() {
		// given
		OpenApiConfig config = new OpenApiConfig("v1");

		// when
		OpenAPI openAPI = config.openAPI();

		// then
		assertThat(openAPI.getServers()).isNotNull();
		assertThat(openAPI.getServers()).hasSize(1);
		assertThat(openAPI.getServers().getFirst().getUrl()).isEqualTo("/");
	}
}
