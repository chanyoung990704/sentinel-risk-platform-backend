package com.aivle.project.dataimport.model;

import java.nio.file.Path;
import java.util.List;

/** 원본 네 파일과 선택적 CSV 대조 파일을 명시적으로 지정한다. */
public record ImportDatasetCommand(int baseQuarterKey, List<Source> sources) {

	public ImportDatasetCommand {
		sources = List.copyOf(sources);
	}

	public enum Role { COMPANY, INDUSTRY, METRIC13, METRIC5, REFERENCE13, REFERENCE5 }

	public record Source(Role role, Path path, String encoding) { }
}
