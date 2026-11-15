package com.aivle.project.architecture;

import com.aivle.project.ProjectApplication;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import static org.assertj.core.api.Assertions.assertThat;

/** 패키지 구분이 실제 모듈 경계로 검증되는지 확인한다. */
@Tag("modulith")
class ApplicationModulesTest {
	@Test
	void everyProductionPackageHasAnExplicitOwner() {
		// given
		var modules = ApplicationModules.of(ProjectApplication.class);
		// when
		var names = modules.stream().map(module -> module.getIdentifier().toString()).collect(Collectors.toSet());
		// then
		assertThat(names).isEqualTo(Set.of("identity", "finance", "insight", "reporting", "aiintegration",
			"watchlist", "news", "community", "storage", "app", "foundation"));
	}

	@Test
	void modulesHaveNoCyclesOrInternalAccess() {
		// given
		var modules = ApplicationModules.of(ProjectApplication.class);
		// when / then
		modules.verify();
		new Documenter(modules).writeDocumentation();
	}
}
