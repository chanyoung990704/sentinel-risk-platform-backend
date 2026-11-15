package com.aivle.project.finance.internal.dataimport.service;

import com.aivle.project.finance.internal.dataimport.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기존 마스터의 상충 값을 덮지 않고, 기업·업종 연결을 원자적으로 준비한다. */
@Service
public class ImportMasterService {
	private final ImportJournalRepository imports;
	private final ImportReconciliationRepository reconciliation;
	private final ImportMasterRepository masters;
	public ImportMasterService(ImportJournalRepository imports,ImportReconciliationRepository reconciliation,ImportMasterRepository masters) {this.imports=imports;this.reconciliation=reconciliation;this.masters=masters;}
	@Transactional(timeout=180)
	public void load(UUID id) {
		imports.lock(id);
		if(!reconciliation.metadataIntact(id))throw new IllegalStateException("snapshot 메타데이터 손상");
		var input=reconciliation.metadata(id);Set<String> stocks=new HashSet<>(),corps=new HashSet<>();
		for(var c:input.companies())if(!c.stockCode().matches("[0-9A-Z]{6}") || !c.corpCode().matches("[0-9]{8}") || c.name()==null || c.name().isBlank() || c.name().length()>100 || !stocks.add(c.stockCode()) || !corps.add(c.corpCode()))throw new IllegalStateException("기업 마스터 구조/중복 오류");
		Map<String,String> names=new TreeMap<>();Set<String> industryStocks=new HashSet<>();
		for(var i:input.industries()) {
			if(!stocks.contains(i.stockCode()) || !industryStocks.add(i.stockCode()) || !i.code().matches("[0-9]{1,5}") || (i.name()!=null && i.name().length()>100))throw new IllegalStateException("업종 마스터 구조/연결 오류");
			String previous=names.get(i.code());if(previous!=null && i.name()!=null && !previous.equals(i.name()))throw new IllegalStateException("같은 업종 코드의 이름 충돌");
			if(!names.containsKey(i.code()) || i.name()!=null)names.put(i.code(),i.name());
		}
		Map<String,Long> industryIds=new HashMap<>();
		for(var entry:names.entrySet()) {
			var rows=masters.lockIndustry(entry.getKey());
			if(rows.isEmpty())masters.insertIndustry(entry.getKey(),entry.getValue());
			else {
				var row=rows.getFirst();Object name=row.get("industry_name");
				if(row.get("deleted_at")!=null || (name!=null && entry.getValue()!=null && !name.equals(entry.getValue())))throw new IllegalStateException("기존 업종 마스터 충돌");
				if(name==null && entry.getValue()!=null)masters.enrichIndustry(row.get("id"),entry.getValue());
			}
			industryIds.put(entry.getKey(),masters.industryId(entry.getKey()));
		}
		for(var c:input.companies().stream().sorted(Comparator.comparing(com.aivle.project.finance.internal.dataimport.model.ImportPreflightResult.Company::stockCode)).toList()) {
			var rows=masters.lockCompanies(c.stockCode(),c.corpCode());
			if(rows.isEmpty())masters.insertCompany(c.corpCode(),c.name(),c.stockCode());
			else if(rows.size()!=1 || rows.getFirst().get("deleted_at")!=null || !c.corpCode().equals(rows.getFirst().get("corp_code")) || !c.stockCode().equals(rows.getFirst().get("stock_code")) || !c.name().equals(rows.getFirst().get("corp_name")))throw new IllegalStateException("기존 기업 마스터 충돌: "+c.stockCode());
		}
		for(var i:input.industries()) {
			Long industry=industryIds.get(i.code());
			if(!masters.bindIndustry(i.stockCode(),industry))throw new IllegalStateException("기존 기업/업종 연결 충돌: "+i.stockCode());
		}
	}
}
