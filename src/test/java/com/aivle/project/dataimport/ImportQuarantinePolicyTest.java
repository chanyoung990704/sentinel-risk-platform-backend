package com.aivle.project.dataimport;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.dataimport.model.*;
import com.aivle.project.dataimport.model.ImportPreflightResult.*;
import com.aivle.project.dataimport.model.ImportStartCommand.QuarantinePolicy;
import com.aivle.project.dataimport.service.ImportInputValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class ImportQuarantinePolicyTest {
	private final ImportInputValidator validator=new ImportInputValidator();
	@Test void strictRejectsArchiveButExplicitVersionAllowsOnlyRecordedLocations()throws Exception {
		// given
		var input=archive();
		// when / then: 값 적재와 별도로 오류 위치 승인 계약을 검증한다.
		assertThatIllegalArgumentException().isThrownBy(()->validator.validateIssues(input,QuarantinePolicy.STRICT));
		assertThatCode(()->validator.validateIssues(input,QuarantinePolicy.SENTINEL_ARCHIVE_CODE_GAP_V1)).doesNotThrowAnyException();
		assertThatIllegalArgumentException().isThrownBy(()->validator.validate(new ImportStartCommand(input,"test",500,QuarantinePolicy.SENTINEL_ARCHIVE_CODE_GAP_V1)));
	}
	@Test void anyAdditionalOrChangedErrorIsRejected()throws Exception {
		// given
		var input=archive();var errors=new ArrayList<>(input.issues());var first=errors.getFirst();
		errors.add(new Issue("ERROR","INVALID_DECIMAL",first.provenance(),"","NaN","추가 오류"));
		var additional=withIssues(input,errors);
		// when / then
		assertThatIllegalArgumentException().isThrownBy(()->validator.validateIssues(additional,QuarantinePolicy.SENTINEL_ARCHIVE_CODE_GAP_V1));
		errors=new ArrayList<>(input.issues());errors.set(0,new Issue("ERROR","INVALID_DECIMAL",first.provenance(),"","NaN","변경 오류"));var changed=withIssues(input,errors);
		assertThatIllegalArgumentException().isThrownBy(()->validator.validateIssues(changed,QuarantinePolicy.SENTINEL_ARCHIVE_CODE_GAP_V1));
	}
	@Test void changedSourceFingerprintCannotReuseException()throws Exception {
		// given
		var input=archive();var m=input.manifest();var changed=new ImportManifest("0".repeat(64),m.baseQuarterKey(),m.parserVersion(),m.decimalPolicyVersion(),m.canonicalChecksum(),m.sources(),m.counts());
		// when / then
		assertThatIllegalArgumentException().isThrownBy(()->validator.validateIssues(new ImportPreflightResult(changed,List.of(),List.of(),List.of(),input.issues(),List.of()),QuarantinePolicy.SENTINEL_ARCHIVE_CODE_GAP_V1));
	}
	@Test void omittedIssuesAndComparisonMismatchCannotBecomeStrictSuccess()throws Exception {
		// given: 오류 집계가 남아 있는데 issue 목록만 지운 입력이다.
		var input=archive();var hidden=withIssues(input,List.of());
		// when / then
		assertThatIllegalArgumentException().isThrownBy(()->validator.validateIssues(hidden,QuarantinePolicy.STRICT));
		var m=input.manifest();var cleanCounts=new ImportManifest(m.fingerprint(),m.baseQuarterKey(),m.parserVersion(),m.decimalPolicyVersion(),m.canonicalChecksum(),m.sources(),Map.of("errors",0L,"warnings",0L));
		var mismatch=new ImportPreflightResult(cleanCounts,List.of(),List.of(),List.of(),List.of(),List.of(new Comparison("REFERENCE13",1,1,0,0,1,"a","b")));
		assertThatIllegalArgumentException().isThrownBy(()->validator.validateIssues(mismatch,QuarantinePolicy.STRICT));
	}
	private ImportPreflightResult archive()throws Exception {
		ObjectMapper mapper=new ObjectMapper();
		try(var m=getClass().getResourceAsStream("/dataimport/archive-manifest.json");var i=getClass().getResourceAsStream("/dataimport/archive-issues.json")) {
			return new ImportPreflightResult(mapper.readValue(m,ImportManifest.class),List.of(),List.of(),List.of(),mapper.readValue(i,new TypeReference<List<Issue>>(){}),List.of());
		}
	}
	private ImportPreflightResult withIssues(ImportPreflightResult i,List<Issue> issues){return new ImportPreflightResult(i.manifest(),i.cells(),i.companies(),i.industries(),issues,List.of());}
}
