package com.aivle.project.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

/** 공개 계약의 중첩 제네릭까지 확인해 저장소·엔티티 유출을 차단한다. */
@Tag("modulith")
class ModuleContractRulesTest {
	@Test
	void identityDoesNotOwnApplicationHttpRouting() {
		// given
		var identity = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("com.aivle.project.identity");
		// when
		var routing = identity.stream().flatMap(type -> type.getDirectDependenciesFromSelf().stream())
			.filter(dependency -> Set.of("org.springframework.security.web.SecurityFilterChain",
				"org.springframework.web.cors.CorsConfigurationSource").contains(dependency.getTargetClass().getName()))
			.map(Object::toString).toList();
		// then: 발급/키 보관은 identity, 전체 앱의 자원 경로 정책은 app이다.
		assertThat(routing).isEmpty();
	}
	@Test
	void publicContractsContainOnlyValuesAndPublicTypes() throws Exception {
		// given
		var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("com.aivle.project");
		var checked = new HashSet<String>();
		// when / then
		for (var javaClass : classes) {
			if (!javaClass.getPackageName().contains(".api") || javaClass.getSimpleName().equals("package-info")) continue;
			Class<?> contract = Class.forName(javaClass.getName(), false, getClass().getClassLoader());
			check(contract, checked);
			for (Type parent : contract.getGenericInterfaces()) check(parent, new HashSet<>());
			if (contract.getGenericSuperclass() != null) check(contract.getGenericSuperclass(), new HashSet<>());
			for (var method : contract.getDeclaredMethods()) {
				if (!Modifier.isPublic(method.getModifiers())) continue;
				check(method.getGenericReturnType(), new HashSet<>());
				for (Type parameter : method.getGenericParameterTypes()) check(parameter, new HashSet<>());
			}
			for (var field : contract.getDeclaredFields()) {
				if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) check(field.getGenericType(), new HashSet<>());
			}
		}
		assertThat(checked).isNotEmpty();
	}

	@Test
	void ruleRejectsEntityRepositoryAndNestedGenericFixtures() throws Exception {
		// given / when / then
		assertThatThrownBy(() -> check(com.aivle.project.storage.internal.file.entity.FilesEntity.class, new HashSet<>())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> check(ForbiddenRepository.class, new HashSet<>())).isInstanceOf(IllegalStateException.class);
		var nested = ForbiddenContract.class.getDeclaredMethod("leak").getGenericReturnType();
		assertThatThrownBy(() -> check(nested, new HashSet<>())).isInstanceOf(IllegalStateException.class);
	}

	static void check(Type type, Set<String> seen) {
		if (!seen.add(type.getTypeName())) return;
		if (type instanceof Class<?> clazz) {
			if (clazz.isArray()) { check(clazz.componentType(), seen); return; }
			if (clazz.isAnnotationPresent(Entity.class) || Repository.class.isAssignableFrom(clazz)
				|| (clazz.getName().startsWith("com.aivle.project.") && clazz.getName().contains(".internal."))) {
				throw new IllegalStateException("공개 계약에서 내부 저장 타입을 노출합니다: " + clazz.getName());
			}
		} else if (type instanceof ParameterizedType parameterized) {
			check(parameterized.getRawType(), seen);
			for (Type argument : parameterized.getActualTypeArguments()) check(argument, seen);
		} else if (type instanceof WildcardType wildcard) {
			for (Type bound : wildcard.getUpperBounds()) check(bound, seen);
			for (Type bound : wildcard.getLowerBounds()) check(bound, seen);
		} else if (type instanceof TypeVariable<?> variable) {
			for (Type bound : variable.getBounds()) check(bound, seen);
		} else if (type instanceof GenericArrayType array) check(array.getGenericComponentType(), seen);
	}

	
	interface ForbiddenRepository extends Repository<com.aivle.project.storage.internal.file.entity.FilesEntity, Long> {}
	interface ForbiddenContract { java.util.Map<String, List<? extends com.aivle.project.storage.internal.file.entity.FilesEntity[]>> leak(); }
}
