package com.aivle.project.finance.internal.dataimport.service;

import com.aivle.project.finance.internal.dataimport.entity.*;
import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.api.CanonicalMetricCell;
import com.aivle.project.finance.internal.dataimport.repository.ImportJournalRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 한 묶음의 값과 checkpoint를 별도 transaction에서 함께 확정한다. */
@Service
public class ImportChunkService {
	private final ImportJournalRepository repo;
	private final ObjectMapper mapper;
	public ImportChunkService(ImportJournalRepository repo,ObjectMapper mapper){this.repo=repo;this.mapper=mapper;}
	@Transactional(propagation=Propagation.REQUIRES_NEW,timeout=20)
	public ImportChunkResult stage(UUID runId,UUID snapshotId,int number) {
		// MySQL RR의 일관 읽기 view가 잠금 획득 전에 만들어지지 않도록 한다.
		var snapshot=repo.lock(snapshotId);var run=repo.lockRun(runId);
		if(!run.snapshotId().equals(snapshotId))throw new IllegalArgumentException("실행과 snapshot 불일치");
		if(snapshot.state().equals("PREPARING"))throw new IllegalStateException("묶음 계획 준비 미완료");
		if(run.state().equals("FAILED"))throw new IllegalStateException("실패한 실행은 명시적으로 재개해야 함");
		boolean completeState=Set.of("STAGED","VERIFIED").contains(snapshot.state());
		if(number<0)return new ImportChunkResult(-1,0,completeState);
		var chunk=repo.chunk(snapshot.id(),number);
		if(chunk==null)throw new IllegalStateException("묶음 계획 없음");
		if(chunk.state().equals("COMMITTED"))return new ImportChunkResult(number,0,completeState);
		if(snapshot.state().equals("VERIFIED"))throw new IllegalStateException("검증 완료 입력 변경 거부");
		if(!ImportPreflightService.sha256(chunk.payload().getBytes(StandardCharsets.UTF_8)).equals(chunk.payloadHash()))throw new IllegalStateException("불변 입력 hash 불일치");
		List<CanonicalMetricCell> cells;
		try{cells=mapper.readValue(chunk.payload(),new TypeReference<List<CanonicalMetricCell>>(){});}catch(Exception e){throw new IllegalStateException("묶음 입력 손상",e);}
		if(cells.size()!=chunk.cellCount())throw new IllegalStateException("묶음 행 수 불일치");
		repo.insertStage(cells.stream().map(c->new ImportStageValueEntity(snapshot.id(),c)).toList());
		repo.commitChunk(snapshot.id(),number,runId,cells.size());
		var progress=repo.status(runId);boolean complete=progress.committedChunks()==progress.totalChunks();
		if(complete){
			if(progress.stagedCells()!=snapshot.expectedCells() || !repo.checksum(snapshot.id()).equals(snapshot.checksum()))throw new IllegalStateException("전체 staging 대조 실패");
			repo.staged(snapshot.id());
		}
		return new ImportChunkResult(number,cells.size(),complete);
	}
}
