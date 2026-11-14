package com.aivle.project.dataimport.service;

import com.aivle.project.dataimport.model.*;
import com.aivle.project.dataimport.entity.*;
import com.aivle.project.dataimport.repository.ImportJournalRepository;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 영속 적재 실행의 접수·재개·재시도 orchestration. */
@Service
public class ImportRunService {
	private final ImportJournalRepository repo;
	private final ImportChunkService chunks;
	private final ImportInputValidator validator;
	private final TransactionTemplate tx;
	public ImportRunService(ImportJournalRepository repo,ImportChunkService chunks,ImportInputValidator validator,PlatformTransactionManager manager) {
		this.repo=repo;this.chunks=chunks;this.validator=validator;tx=new TransactionTemplate(manager);
		tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);tx.setTimeout(20);
	}
	public UUID start(ImportStartCommand command) {
		List<CanonicalMetricCell> cells=validator.validate(command);
		var input=command.input();String policy=command.policy().name();
		String manifest=repo.json(new ImportManifest(input.manifest().fingerprint(),input.manifest().baseQuarterKey(),input.manifest().parserVersion(),input.manifest().decimalPolicyVersion(),input.manifest().canonicalChecksum(),input.manifest().sources().stream().sorted(Comparator.comparing(ImportManifest.SourceManifest::role)).toList(),new TreeMap<>(input.manifest().counts())));
		String companies=repo.json(input.companies().stream().sorted(Comparator.comparing(ImportPreflightResult.Company::stockCode)).toList());
		String industries=repo.json(input.industries().stream().sorted(Comparator.comparing(ImportPreflightResult.Industry::stockCode)).toList());
		String metadata=hash(manifest+"\n"+companies+"\n"+industries);
		String fingerprint=hash(input.manifest().fingerprint()+"|"+policy);
		int count=(cells.size()+command.chunkSize()-1)/command.chunkSize();
		DataSnapshotEntity fresh=new DataSnapshotEntity(UUID.randomUUID(),fingerprint,input.manifest().fingerprint(),input.manifest().canonicalChecksum(),metadata,command.chunkSize(),cells.size(),count,"PREPARING");
		DataSnapshotEntity existing=repo.find(fingerprint);
		if(existing==null)try{tx.executeWithoutResult(t->repo.create(fresh,command,policy,manifest,companies,industries));existing=fresh;}
		catch(DuplicateKeyException conflict){existing=repo.find(fingerprint);if(existing==null)throw conflict;}
		if(!existing.checksum().equals(fresh.checksum()) || !existing.metadataChecksum().equals(metadata) || existing.chunkSize()!=command.chunkSize() || existing.expectedCells()!=cells.size())throw new IllegalArgumentException("기존 데이터셋의 값/메타데이터/묶음 크기와 충돌");
		UUID snapshot=existing.id(),run=UUID.randomUUID();
		tx.executeWithoutResult(t->{repo.createRun(run,snapshot,command.submittedBy());for(var issue:input.issues())repo.error(new ImportErrorEntity(run,issue.code(),issue.severity().equals("ERROR")?"EXCLUDED":"WARNING",repo.json(issue)));});
		for(int i=0;i<count;i++) {
			int from=i*command.chunkSize(),to=Math.min(from+command.chunkSize(),cells.size());
			String payload=repo.json(cells.subList(from,to));var chunk=new ImportChunkEntity(i+1,to-from,hash(payload),payload,"PENDING");
			tx.executeWithoutResult(t->{repo.lock(snapshot);var old=repo.chunk(snapshot,chunk.number());
				if(old==null)repo.plan(snapshot,run,chunk);else if(!old.payloadHash().equals(chunk.payloadHash()) || old.cellCount()!=chunk.cellCount())throw new IllegalArgumentException("기존 묶음의 불변 입력과 충돌");});
		}
		tx.executeWithoutResult(t->{repo.lock(snapshot);if(repo.planCount(snapshot)!=count)throw new IllegalStateException("묶음 계획 미완료");repo.ready(snapshot,run);});
		return run;
	}
	public ImportChunkResult stageNext(UUID runId) {
		UUID snapshotId=null;int target=Integer.MIN_VALUE;
		for(int attempt=0;;attempt++)try {
			if(snapshotId==null)snapshotId=repo.run(runId).snapshotId();
			if(target==Integer.MIN_VALUE)target=repo.next(snapshotId);
			return chunks.stage(runId,snapshotId,target);
		}
		catch(RuntimeException error) {
			if(snapshotId==null && error instanceof EmptyResultDataAccessException)throw new IllegalArgumentException("적재 실행 없음",error);
			boolean transientDb=isTransient(error);String code=transientDb?"TRANSIENT_DB":"PERMANENT_FAILURE";
			if(transientDb && attempt<3) {
				journal(runId,snapshotId,target,code,true,error);pause(attempt);
			} else {
				journal(runId,snapshotId,target,code,false,error);
				throw error;
			}
		}
	}
	private void journal(UUID runId,UUID knownSnapshot,int target,String code,boolean retry,RuntimeException original) {
		for(int attempt=0;;attempt++)try {
			tx.executeWithoutResult(t->{
				// 감사는 run만 잠근다. snapshot을 추가 획득하지 않아 역순 잠금을 만들지 않는다.
				var run=repo.lockRun(runId);
				if(retry)repo.retry(runId,code);else repo.failed(runId,code);
				String disposition=run.state().equals("COMPLETED")?"AFTER_COMPLETE":retry?"RETRY":"FAILED";
				repo.error(new ImportErrorEntity(runId,code,disposition,repo.json(Map.of("reason",reason(original),"chunk",target,"phase",target==Integer.MIN_VALUE?"LOOKUP":"CHUNK"))));
			});return;
		} catch(RuntimeException auditFailure) {
			if(!isTransient(auditFailure) || attempt>=3)throw new ImportRecoveryRequiredException(original,auditFailure);
			pause(attempt);
		}
	}
	private static void pause(int attempt) {
		try{Thread.sleep(25L*(1L<<attempt));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("적재 재시도 중단",interrupted);}
	}
	public ImportRunStatus status(UUID runId) {return repo.status(runId);}
	public void resume(UUID runId) {
		tx.executeWithoutResult(t->{var run=repo.run(runId);var snapshot=repo.lock(run.snapshotId());
			if(snapshot.state().equals("PREPARING"))throw new IllegalStateException("동일 원본 재접수로 준비를 완료해야 함");
			repo.resume(runId,Set.of("STAGED","VERIFIED").contains(snapshot.state()));});
	}
	private static String hash(String value){return ImportPreflightService.sha256(value.getBytes(StandardCharsets.UTF_8));}
	private static boolean isTransient(Throwable e) {
		for(Throwable cause=e;cause!=null;cause=cause.getCause()) {
			if(cause instanceof TransientDataAccessException)return true;
			if(cause instanceof SQLException sql && (sql.getErrorCode()==1205 || sql.getErrorCode()==1213 || (sql.getSQLState()!=null && (sql.getSQLState().startsWith("08") || sql.getSQLState().equals("40001")))))return true;
		}return false;
	}
	private static String reason(Throwable e) {
		for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof SQLException sql)return "sqlState="+sql.getSQLState()+",vendor="+sql.getErrorCode();
		return e.getClass().getSimpleName();
	}
}
