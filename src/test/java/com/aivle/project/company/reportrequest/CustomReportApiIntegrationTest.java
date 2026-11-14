package com.aivle.project.company.reportrequest;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.company.reportjob.service.ReportResultService;
import com.aivle.project.company.reportrequest.model.CustomReportCommand;
import com.aivle.project.common.logging.ApiLogProcessor;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
@Tag("realinfra") @ActiveProfiles("local") @AutoConfigureMockMvc
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false"})
class CustomReportApiIntegrationTest extends ReportFlowIntegrationSupport {
	@Autowired MockMvc mvc;@Autowired ReportResultService results;@Autowired ApiLogProcessor logProcessor;
	org.springframework.test.web.servlet.request.RequestPostProcessor auth(long id){return jwt().jwt(j->j.subject(jdbc.queryForObject("SELECT BIN_TO_UUID(uuid) FROM users WHERE id=?",String.class,id)).claim("userId",id)).authorities(new SimpleGrantedAuthority("ROLE_USER"));}
	@Test void acceptsAfterCommitAndReplaysOrRejectsChangedInput()throws Exception {
		// given
		String path="/api/companies/"+company+"/custom-report-requests",key="api-"+UUID.randomUUID(),body="{\"additionalInstructions\":\"추가 내용\"}";
		// when
		var first=mvc.perform(post(path).with(auth(users.getFirst())).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isAccepted()).andReturn();
		// then
		String id=mapper.readTree(first.getResponse().getContentAsString()).at("/data/requestId").asText();assertThat(jdbc.queryForObject("SELECT status FROM report_requests WHERE id=?",String.class,id)).isEqualTo("ACCEPTED");
		mvc.perform(post(path).with(auth(users.getFirst())).header("Idempotency-Key",key).contentType("application/json").content(body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.requestId").value(id));
		mvc.perform(post(path).with(auth(users.getFirst())).header("Idempotency-Key",key).contentType("application/json").content("{\"additionalInstructions\":\"변경\"}")).andExpect(status().isConflict());
		mvc.perform(post(path).with(auth(users.getFirst())).contentType("application/json").content(body)).andExpect(status().isBadRequest());
		mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
	}
	@Test void ownerCanReadCompletedPdfButOtherUsersAndGeneralRoutesCannot()throws Exception {
		// given
		var id=enqueue();String path="/api/report-requests/"+id;
		// when / then
		mvc.perform(get(path+"/file").with(auth(users.getFirst()))).andExpect(status().isConflict());var lease=claim();results.storeAndComplete(lease,generate(lease));
		mvc.perform(get(path).with(auth(users.getFirst()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED")).andExpect(jsonPath("$.data.storageKey").doesNotExist());
		var response=mvc.perform(get(path+"/file").with(auth(users.getFirst()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(content().contentType("application/pdf")).andReturn().getResponse();assertThat(new String(response.getContentAsByteArray(),0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		mvc.perform(get(path).with(auth(users.getLast()))).andExpect(status().isNotFound());mvc.perform(get(path+"/file").with(auth(users.getLast()))).andExpect(status().isNotFound());mvc.perform(get(path+"/file")).andExpect(status().isUnauthorized());
		long file=jdbc.queryForObject("SELECT file_id FROM report_artifacts WHERE request_id=?",Long.class,id.toString());
		for(String url:java.util.List.of("/api/files/"+file,"/api/files/"+file+"/download-url","/api/reports/files/"+file,"/api/reports/files/"+file+"/url"))mvc.perform(get(url).with(auth(users.getLast()))).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company_report_versions WHERE pdf_file_id=?",Integer.class,file)).isZero();
	}
	@Test void customInputWithEscapedQuotesDoesNotAppearInApiLog() {
		// given / when / then
		assertThat(logProcessor.maskArgs(new Object[]{new CustomReportCommand(null,"비공개\"뒤쪽내용") })).doesNotContain("비공개","뒤쪽내용");
	}
}
