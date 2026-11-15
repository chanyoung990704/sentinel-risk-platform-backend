package com.aivle.project.reporting.internal.company.reportjob.service;
import java.util.*;
import java.nio.file.*;
import java.io.IOException;
import com.aivle.project.reporting.internal.company.reportjob.adapter.LocalReportArtifactStore;
import com.aivle.project.reporting.internal.company.reportjob.adapter.ReportPdfValidator;
import com.aivle.project.foundation.api.Hashing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
/** 지정한 요청 집합을 읽기만 대조한다. 실행 중인 요청의 결과는 관찰 시점 기준이다. */
@Service public class ReportReconciliationService {
	private final JdbcTemplate jdbc;private final Path root;
	private final com.aivle.project.storage.api.FileCatalog files;
	@org.springframework.beans.factory.annotation.Autowired
	public ReportReconciliationService(JdbcTemplate jdbc,com.aivle.project.storage.api.FileCatalog files,@Value("${app.file.upload.upload-dir}") String directory){this.jdbc=jdbc;this.files=files;root=Path.of(directory).toAbsolutePath().normalize();}
	public ReportReconciliationService(JdbcTemplate jdbc,String directory){this(jdbc,com.aivle.project.storage.api.FileCatalogReaders.jdbc(jdbc),directory);}
	public record Result(int requested,int accepted,int nonterminal,int succeeded,int failed,int missingRequests,int missingJobs,int duplicateArtifacts,int missingArtifacts,int missingFiles,int invalidArtifacts,int unlinkedFiles) { }
	public Result reconcile(List<UUID> ids){
		if(ids==null||ids.isEmpty()||ids.size()>1000||ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("대조 범위는 중복 없는 요청 ID 1~1000개입니다");
		int accepted=0,nonterminal=0,succeeded=0,failed=0,missingJobs=0,duplicates=0,missingArtifacts=0,missingFiles=0,invalid=0,unlinked=0;
		for(UUID id:ids){
			var statuses=jdbc.queryForList("SELECT status FROM report_requests WHERE id=?",String.class,id.toString());
			if(!statuses.isEmpty()){
				accepted++;String status=statuses.getFirst();if(status.equals("SUCCEEDED"))succeeded++;else if(status.equals("FAILED"))failed++;else nonterminal++;
				if(!status.equals("ACCEPTED")&&jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())==0)missingJobs++;
			}
			var artifacts=jdbc.queryForList("SELECT storage_key,file_size,sha256,file_id FROM report_artifacts WHERE request_id=?",id.toString());
			duplicates+=Math.max(0,artifacts.size()-1);if(statuses.contains("SUCCEEDED")&&artifacts.isEmpty())missingArtifacts++;
			var metadataById=files.findAllByIds(artifacts.stream().map(row -> (Number)row.get("file_id")).filter(Objects::nonNull).map(Number::longValue).toList());
			var linked=new HashSet<String>();
			for(var artifact:artifacts){
				String key=(String)artifact.get("storage_key");
				if(!LocalReportArtifactStore.validKey(key)||!key.startsWith("custom-reports/"+id+"/")){invalid++;continue;}
				linked.add(key);Path path=root.resolve(key);var fileId=(Number)artifact.get("file_id");var file=fileId==null?null:metadataById.get(fileId.longValue());boolean metadata=file!=null&&key.equals(file.getStorageKey())&&((Number)artifact.get("file_size")).longValue()==file.getFileSize()&&"application/pdf".equals(file.getContentType());
				if(!safePath(path)){invalid++;continue;}
				if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)){missingFiles++;continue;}
				if(!metadata||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)){invalid++;continue;}
				try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
					byte[] bytes=input.readNBytes(ReportPdfValidator.MAX_BYTES+1);
					if(bytes.length>ReportPdfValidator.MAX_BYTES||bytes.length!=((Number)artifact.get("file_size")).longValue()||!Hashing.sha256(bytes).equals(artifact.get("sha256")))invalid++;
				}catch(IOException failure){invalid++;}
			}
			Path directory=root.resolve("custom-reports").resolve(id.toString());
			if(!safePath(directory))throw new IllegalStateException("대조 경로에 링크가 있습니다");
			if(Files.exists(directory,LinkOption.NOFOLLOW_LINKS))try(var paths=Files.walk(directory,3)){
				for(Path path:paths.toList())if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&!linked.contains(root.relativize(path).toString().replace('\\','/')))unlinked++;
			}catch(IOException failure){throw new IllegalStateException("소유 파일 대조 실패");}
		}
		return new Result(ids.size(),accepted,nonterminal,succeeded,failed,ids.size()-accepted,missingJobs,duplicates,missingArtifacts,missingFiles,invalid,unlinked);
	}
	private boolean safePath(Path path){if(!path.normalize().startsWith(root))return false;Path cursor=path.getRoot();for(Path part:path){cursor=cursor.resolve(part);if(Files.isSymbolicLink(cursor))return false;}return true;}
}
