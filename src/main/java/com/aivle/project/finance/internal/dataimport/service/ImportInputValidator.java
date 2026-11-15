package com.aivle.project.finance.internal.dataimport.service;

import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.api.CanonicalMetricCell;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand.QuarantinePolicy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

/** 접수 전에 checksum/키/출처를 검사하고 알려진 아카이브 제외만 명시적으로 허용한다. */
@Component
public class ImportInputValidator {
	private static final String ARCHIVE="8447350f40677e8ce60b3317f475134c017943c2a672f6371d06b6f4d6bf63f2";
	private static final String ARCHIVE_VALUES="bdde302162d4a00adc458851666ed63a1755a8b9c064ae3ed65e53ee1d32892f";
	public List<CanonicalMetricCell> validate(ImportStartCommand c) {
		if(c==null || c.input()==null || c.policy()==null || c.submittedBy()==null || c.submittedBy().isBlank() || c.submittedBy().length()>128 || c.chunkSize()<1 || c.chunkSize()>500)throw new IllegalArgumentException("접수 조건 오류");
		var input=c.input();var m=input.manifest();
		if(m==null || !ImportPreflightService.PARSER_VERSION.equals(m.parserVersion()) || !MetricDecimalPolicy.VERSION.equals(m.decimalPolicyVersion()))throw new IllegalArgumentException("해석 정책 버전 불일치");
		ImportPreflightService.offsetQuarter(m.baseQuarterKey(),0);
		StringBuilder identity=new StringBuilder(m.baseQuarterKey()+"|"+m.parserVersion()+"|"+m.decimalPolicyVersion()+"\n");
		Map<String,ImportManifest.SourceManifest> sources=new HashMap<>();
		for(var s:m.sources().stream().sorted(Comparator.comparing(ImportManifest.SourceManifest::role)).toList()) {
			if(sources.putIfAbsent(s.role(),s)!=null || !s.sha256().matches("[0-9a-f]{64}"))throw new IllegalArgumentException("원본 manifest 오류");
			identity.append(s.role()).append('|').append(s.sha256()).append('|').append(s.encoding()).append('\n');
		}
		if(!ImportPreflightService.sha256(identity.toString().getBytes(StandardCharsets.UTF_8)).equals(m.fingerprint()))throw new IllegalArgumentException("원본 지문 불일치");
		List<CanonicalMetricCell> cells=input.cells().stream().sorted(Comparator.comparing(CanonicalMetricCell::key)).toList();
		if(cells.isEmpty() || !ImportPreflightService.checksum(cells).equals(m.canonicalChecksum()))throw new IllegalArgumentException("canonical checksum 불일치/빈 입력");
		if(!Objects.equals(m.counts().get("canonicalCells"),(long)cells.size()))throw new IllegalArgumentException("canonical 집계 불일치");
		Set<CanonicalMetricCell.Key> keys=new HashSet<>();
		for(var cell:cells) {
			var k=cell.key();
			if(!keys.add(k) || !k.stockCode().matches("[0-9A-Z]{6}") || !k.metricCode().matches("[A-Za-z][A-Za-z0-9_]{0,63}") || !k.valueType().equals("ACTUAL"))throw new IllegalArgumentException("canonical 키 오류/중복");
			Set<Integer> quarters=new HashSet<>();for(int i=0;i<4;i++)quarters.add(ImportPreflightService.offsetQuarter(m.baseQuarterKey(),-i));
			if(!quarters.contains(k.quarterKey()) || (cell.value()!=null && (cell.value().scale()!=4 || cell.value().precision()>20)))throw new IllegalArgumentException("분기/정밀도 오류");
			if(cell.provenance().isEmpty())throw new IllegalArgumentException("출처 없음");
			for(var p:cell.provenance()) {var source=sources.get(p.source());if(source==null || !source.sha256().equals(p.fileHash()) || !source.sheet().equals(p.sheet()) || p.row()<2 || p.row()>source.rows()+1 || p.column()<1)throw new IllegalArgumentException("출처 manifest 불일치");}
		}
		validateIssues(input,c.policy());return cells;
	}
	public void validateIssues(ImportPreflightResult input,QuarantinePolicy policy) {
		var errors=input.issues().stream().filter(i->i.severity().equals("ERROR")).toList();
		long warnings=input.issues().stream().filter(i->i.severity().equals("WARNING")).count();
		if(input.manifest().counts().getOrDefault("errors",0L)!=errors.size() || input.manifest().counts().getOrDefault("warnings",0L)!=warnings)throw new IllegalArgumentException("사전 검증 오류/경고 집계와 상세 목록 불일치");
		if(input.comparisons().stream().anyMatch(c->c.missing()!=0 || c.unexpected()!=0 || c.different()!=0 || c.primaryKeys()!=c.referenceKeys() || !Objects.equals(c.primaryChecksum(),c.referenceChecksum())))throw new IllegalArgumentException("원본 대조 불일치");
		if(input.issues().stream().anyMatch(i->!Set.of("ERROR","WARNING").contains(i.severity())))throw new IllegalArgumentException("오류 심각도 불명");
		if(policy==QuarantinePolicy.STRICT) {if(!errors.isEmpty())throw new IllegalArgumentException("사전 검증 오류가 있는 입력");return;}
		var m=input.manifest();
		if(!m.fingerprint().equals(ARCHIVE) || !m.canonicalChecksum().equals(ARCHIVE_VALUES) || errors.size()!=5)throw new IllegalArgumentException("아카이브 제외 정책 대상 불일치");
		Set<String> roles=new HashSet<>(List.of("INDUSTRY","METRIC13","METRIC5","REFERENCE13","REFERENCE5"));
		Map<String,ImportManifest.SourceManifest> sources=new HashMap<>();m.sources().forEach(s->sources.put(s.role(),s));
		for(var i:errors) {var p=i.provenance();var s=sources.get(p.source());
			if(!i.code().equals("INVALID_STOCK_CODE") || !roles.remove(p.source()) || s==null || !s.sha256().equals(p.fileHash()) || !s.sheet().equals(p.sheet()) || p.row()!=2085 || p.column()!=1 || (i.raw()!=null && !i.raw().isBlank() && !i.raw().equalsIgnoreCase("null")))throw new IllegalArgumentException("허용하지 않은 격리 오류");
		}
		if(!roles.isEmpty())throw new IllegalArgumentException("격리 출처 누락");
	}
}
