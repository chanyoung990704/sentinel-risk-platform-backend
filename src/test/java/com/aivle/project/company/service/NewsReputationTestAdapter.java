package com.aivle.project.finance.internal.company.service;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/** 실제 뉴스 저장소와 finance SPI 연결을 slice에서도 검증한다. */
class NewsReputationTestAdapter implements ImportSelector {
	public String[] selectImports(AnnotationMetadata metadata) {
		return new String[] {"com.aivle.project.news.internal.NewsReputationProvider"};
	}
}
