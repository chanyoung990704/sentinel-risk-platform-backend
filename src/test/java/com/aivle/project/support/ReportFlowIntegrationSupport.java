package com.aivle.project.support;
import com.aivle.project.reporting.internal.company.reportrequest.service.ReportRequestService;
import com.aivle.project.reporting.api.CustomReportCommand;
import com.aivle.project.reporting.internal.company.reportjob.service.*;
import com.aivle.project.reporting.internal.company.reportjob.port.*;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import com.aivle.project.reporting.internal.messaging.inbox.ReportJobInbox;
import com.aivle.project.reporting.internal.messaging.outbox.ReportEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
/** 실제 source/HTTP를 사용하고 Kafka 전달만 직접 Inbox 호출로 대체한다. */
public abstract class ReportFlowIntegrationSupport extends CustomReportIntegrationSupport {
	private static final java.nio.file.Path FILE_ROOT=createRoot();
	private static java.nio.file.Path createRoot(){try{return java.nio.file.Files.createTempDirectory("sentinel-report-tests-");}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
	@org.springframework.test.context.DynamicPropertySource static void fileRoot(org.springframework.test.context.DynamicPropertyRegistry registry){registry.add("app.file.upload.upload-dir",()->FILE_ROOT.toString());}
	@org.junit.jupiter.api.AfterEach void removeOwnedTestFiles()throws Exception {
		for(long user:users)for(String id:jdbc.queryForList("SELECT id FROM report_requests WHERE user_id=?",String.class,user)){
			java.nio.file.Path directory=FILE_ROOT.resolve("custom-reports").resolve(UUID.fromString(id).toString()).normalize();if(!directory.startsWith(FILE_ROOT)||java.nio.file.Files.isSymbolicLink(directory))throw new IllegalStateException("Invalid owned fixture path");
			if(java.nio.file.Files.exists(directory,java.nio.file.LinkOption.NOFOLLOW_LINKS))try(var paths=java.nio.file.Files.walk(directory)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);}
		}
	}
	@Autowired protected ReportRequestService requests;@Autowired protected ReportJobInbox inbox;@Autowired protected ObjectMapper mapper;
	@Autowired protected ReportJobClaimService claims;@Autowired protected ReportClock clock;@Autowired protected ReportGenerationPort generator;@Autowired protected ReportSourceReader sources;
	protected UUID enqueue()throws Exception {var id=requests.accept(users.getFirst(),company,"flow-"+UUID.randomUUID(),new CustomReportCommand(null,"현금흐름을 검토해 주세요")).requestId();inbox.accept(mapper.readValue(jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,id.toString()),ReportEvent.class));return id;}
	protected JobLease claim(){return claims.claimReady(1,"artifact-fixture",clock.now()).getFirst();}
	protected GeneratedReport generate(JobLease l){var r=jdbc.queryForMap("SELECT * FROM report_requests WHERE id=?",l.requestId().toString());return generator.generate(sources.load(UUID.fromString((String)r.get("snapshot_id")),((Number)r.get("company_id")).longValue(),((Number)r.get("target_quarter_key")).intValue()),new CustomReportInput((String)r.get("additional_instructions"),(String)r.get("input_hash")),l.requestId());}
}
