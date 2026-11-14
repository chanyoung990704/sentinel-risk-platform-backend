package com.aivle.project.company.reportjob.service;

import com.aivle.project.company.reportjob.adapter.LocalReportArtifactStore;
import com.aivle.project.company.reportjob.model.ReportGenerationFailure;
import com.aivle.project.company.reportjob.repository.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 파일 하나의 짧은 unlink만 완료와 동일한 job 잠금 안에서 수행한다. */
@Service
public class ReportArtifactCleanupTransaction {
	private final ReportJobRepository jobs;
	private final ReportArtifactRepository artifacts;
	private final LocalReportArtifactStore store;
	private final ReportClock clock;
	public ReportArtifactCleanupTransaction(ReportJobRepository jobs,ReportArtifactRepository artifacts,LocalReportArtifactStore store,ReportClock clock){this.jobs=jobs;this.artifacts=artifacts;this.store=store;this.clock=clock;}
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public boolean deleteIfOrphan(Path file,String key,Instant before){
		Path target=store.path(key);Path candidate=file.toAbsolutePath().normalize();
		if(!candidate.getParent().equals(target.getParent())||(!candidate.equals(target)&&!candidate.getFileName().toString().matches(java.util.regex.Pattern.quote(target.getFileName().toString())+"\\.[0-9a-f-]{36}\\.pending")))throw new ReportGenerationFailure("FILE_KEY",false);
		String[] parts=key.split("/");UUID request=UUID.fromString(parts[1]),attempt=UUID.fromString(parts[3].substring(0,36));
		// 완료가 진행 중이면 commit/rollback 후까지 기다려 최종 참조를 읽는다.
		jobs.lockByRequest(request);
		Instant cutoff=clock.now().minusSeconds(120);if(before.isBefore(cutoff))cutoff=before;
		try {
			if(!Files.isRegularFile(candidate,LinkOption.NOFOLLOW_LINKS)||!Files.getLastModifiedTime(candidate,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)||artifacts.protectedFromCleanup(key,request,attempt))return false;
			store.path(key);return Files.deleteIfExists(candidate);
		}catch(java.io.IOException failure){throw new ReportGenerationFailure("CLEANUP_IO",true);}
	}
}
