package com.aivle.project.support;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
/** 이 시험이 소유한 사용자/요청만 정리한다. 실제 재무 snapshot은 보존한다. */
public abstract class CustomReportIntegrationSupport extends PortfolioIntegrationSupport {
	@Autowired protected JdbcTemplate jdbc;
	@Autowired private com.fasterxml.jackson.databind.ObjectMapper evidenceMapper;
	protected final List<Long> users=new ArrayList<>();
	protected long company;
	@BeforeEach void createReportUsers() {
		company=jdbc.queryForObject("SELECT c.id FROM companies c JOIN snapshot_report_versions b ON b.company_id=c.id JOIN active_data_snapshots a ON a.snapshot_id=b.snapshot_id WHERE a.family='FINANCIAL' ORDER BY c.id LIMIT 1",Long.class);
		for(int i=0;i<2;i++) {
			String email="custom-"+UUID.randomUUID()+"@sentinel.invalid";
			jdbc.update("INSERT INTO users(uuid,email,password,name,status) VALUES(UUID_TO_BIN(UUID()),?,?,?,'ACTIVE')",email,UUID.randomUUID().toString(),"맞춤시험");
			users.add(jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email));
		}
	}
	@AfterEach void cleanReportUsers(TestInfo test) throws Exception {
		boolean present=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='report_requests'",Integer.class)>0;
		for(long user:users) {
			if(present) {
				for(String id:jdbc.queryForList("SELECT id FROM report_requests WHERE user_id=?",String.class,user))recordEvidence(test.getTestMethod().orElseThrow().getName(),id);
				var ownedFiles=jdbc.queryForList("SELECT a.file_id FROM report_artifacts a JOIN report_requests r ON r.id=a.request_id WHERE r.user_id=?",Long.class,user);
				long held=jdbc.queryForObject("SELECT COUNT(*) FROM report_requests WHERE user_id=? AND admission_slot_held=1",Long.class,user);
				jdbc.update("DELETE FROM outbox_events WHERE aggregateid IN (SELECT id FROM report_requests WHERE user_id=?)",user);
				jdbc.update("DELETE FROM report_requests WHERE user_id=?",user);
				for(long file:ownedFiles)jdbc.update("DELETE FROM files WHERE id=? AND usage_type='CUSTOM_REPORT_PDF'",file);
				jdbc.update("UPDATE report_admission SET used_count=used_count-? WHERE scope_key='GLOBAL'",held);
				jdbc.update("DELETE FROM report_admission WHERE scope_key=?","USER:"+user);
			}
			jdbc.update("DELETE FROM users WHERE id=?",user);
		}
		users.clear();
	}
	protected long count(String table) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
	private void recordEvidence(String name,String id)throws Exception {
		// 요청 원문·키·lease token·사용자 이메일·로컬 경로는 증거에서도 제외한다.
		var trace=new LinkedHashMap<String,Object>();trace.put("case",getClass().getSimpleName()+"."+name);
		trace.put("request",jdbc.queryForMap("SELECT id,company_id,target_quarter_key,snapshot_id,input_hash,status,state_version,admission_slot_held,last_error_code,retry_at FROM report_requests WHERE id=?",id));
		trace.put("events",jdbc.queryForList("SELECT id,type,destination FROM outbox_events WHERE aggregateid=? ORDER BY created_at,id",id));
		trace.put("inbox",jdbc.queryForList("SELECT consumer,event_id FROM inbox_events WHERE request_id=?",id));
		trace.put("jobs",jdbc.queryForList("SELECT id,request_id,state,version,generation,attempt_no,attempt_id,lease_until,retry_at,last_error_code FROM report_jobs WHERE request_id=?",id));
		trace.put("attempts",jdbc.queryForList("SELECT a.id,a.job_id,a.generation,a.attempt_no,a.claim_version,a.state,a.error_code FROM report_attempts a JOIN report_jobs j ON j.id=a.job_id WHERE j.request_id=? ORDER BY a.generation,a.attempt_no",id));
		trace.put("artifacts",jdbc.queryForList("SELECT id,attempt_id,generation,file_id,file_size,sha256,content_type FROM report_artifacts WHERE request_id=?",id));
		trace.put("recovery",jdbc.queryForList("SELECT id,actor_user_id,target_generation FROM report_recovery_commands WHERE request_id=?",id));
		System.out.println("CUSTOM_REPORT_EVIDENCE "+evidenceMapper.writeValueAsString(trace));
	}
}
