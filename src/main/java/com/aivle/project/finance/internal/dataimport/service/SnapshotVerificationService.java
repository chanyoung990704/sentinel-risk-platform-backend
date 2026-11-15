package com.aivle.project.finance.internal.dataimport.service;

import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.internal.dataimport.model.ImportPreflightResult.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand.QuarantinePolicy;
import com.aivle.project.finance.internal.dataimport.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;

/** 활성화 전에 source/stage/보고서 전체를 대조한다. 호출자는 snapshot row를 잠근다. */
@Service
public class SnapshotVerificationService {
	public static final Set<String> CODES=Set.of("ROA","ROE","OpMargin","DbRatio","EqRatio","CapImpRatio","STDebtRatio","CurRatio","QkRatio","CurLibRatio","CFO_AsRatio","CFO_Sale","CFO_GR","ROA_IndRel","DbRatio_IndRel","STDebtRatio_IndRel","CurRatio_IndRel","CFO_AsRatio_IndRel");
	private final SnapshotReportRepository repo;private final ImportInputValidator validator;
	public SnapshotVerificationService(SnapshotReportRepository repo,ImportInputValidator validator){this.repo=repo;this.validator=validator;}
	public void check(UUID id) {
		var row=repo.metadata(id);String state=(String)row.get("state");if(!Set.of("STAGED","VERIFIED").contains(state))throw new IllegalStateException("staging 미완료");
		String manifestJson=(String)row.get("manifest_json"),companiesJson=(String)row.get("companies_json"),industriesJson=(String)row.get("industries_json");
		String metadata=ImportPreflightService.sha256((manifestJson+"\n"+companiesJson+"\n"+industriesJson).getBytes(StandardCharsets.UTF_8));
		if(!metadata.equals(row.get("metadata_checksum")))throw new IllegalStateException("snapshot 메타데이터 손상");
		ImportManifest manifest=repo.decode(manifestJson,new TypeReference<ImportManifest>(){});var staged=repo.staged(id);
		var input=new ImportPreflightResult(manifest,staged,repo.decode(companiesJson,new TypeReference<List<Company>>(){}),repo.decode(industriesJson,new TypeReference<List<Industry>>(){}),repo.issues(id),List.of());
		try{validator.validate(new ImportStartCommand(input,"snapshot-verify",((Number)row.get("chunk_size")).intValue(),QuarantinePolicy.valueOf((String)row.get("policy_version"))));}catch(IllegalArgumentException error){throw new IllegalStateException("사전 검증/격리 계약 위반",error);}
		if(!repo.planned(id).equals(staged))throw new IllegalStateException("DB 입력과 staging의 값/출처 불일치");
		Map<String,Map<Integer,Set<String>>> coverage=new TreeMap<>();
		for(var c:staged)coverage.computeIfAbsent(c.key().stockCode(),k->new TreeMap<>()).computeIfAbsent(c.key().quarterKey(),k->new HashSet<>()).add(c.key().metricCode());
		Set<Integer> quarters=new HashSet<>();for(int i=0;i<4;i++)quarters.add(ImportPreflightService.offsetQuarter(manifest.baseQuarterKey(),-i));
		int groups=0;for(var company:coverage.values()) {if(!company.keySet().equals(quarters))throw new IllegalStateException("4분기 미완료");for(var codes:company.values())if(!codes.equals(CODES))throw new IllegalStateException("13+5 지표 미완료");groups+=company.size();}
		if(!repo.bridgesMatch(id,groups))throw new IllegalStateException("보고서 버전 연결 미완료/불일치");
		if(!repo.materialized(id).equals(staged))throw new IllegalStateException("staging/보고서 키·값·출처 불일치");
	}
}
