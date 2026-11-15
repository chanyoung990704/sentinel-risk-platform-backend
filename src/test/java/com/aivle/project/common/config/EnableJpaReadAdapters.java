package com.aivle.project.common.config;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.springframework.context.annotation.Import;

/** JPA slice에서도 모듈의 실제 읽기 어댑터를 사용한다. */
@Retention(RetentionPolicy.RUNTIME)
@Import(JpaReadAdapters.class)
public @interface EnableJpaReadAdapters {}
