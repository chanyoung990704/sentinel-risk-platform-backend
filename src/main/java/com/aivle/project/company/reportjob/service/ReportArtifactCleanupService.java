package com.aivle.project.company.reportjob.service;
import java.time.Instant;
import org.springframework.stereotype.Service;
import com.aivle.project.company.reportjob.adapter.LocalReportArtifactStore;
import java.nio.file.*;
@Service public class ReportArtifactCleanupService {
	private final LocalReportArtifactStore store;private final ReportArtifactCleanupTransaction cleanup;private final ReportClock clock;
	public ReportArtifactCleanupService(LocalReportArtifactStore store,ReportArtifactCleanupTransaction cleanup,ReportClock clock){this.store=store;this.cleanup=cleanup;this.clock=clock;}
	public int cleanupOrphans(Instant before){
		Instant cutoff=clock.now().minusSeconds(120);if(before.isBefore(cutoff))cutoff=before;Path root=store.root().resolve("custom-reports");if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(root))return 0;int removed=0;
		try(var paths=Files.walk(root,4)){for(Path path:paths.toList()){
			if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;String key=store.root().relativize(path).toString().replace('\\','/');String canonical=key.replaceFirst("\\.[0-9a-f-]{36}\\.pending$","");if(!LocalReportArtifactStore.validKey(canonical))continue;
			store.path(canonical);if(!Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff))continue;
			if(cleanup.deleteIfOrphan(path,canonical,cutoff))removed++;
		}}catch(java.io.IOException failure){throw new com.aivle.project.company.reportjob.model.ReportGenerationFailure("CLEANUP_IO",true);}return removed;
	}
}
