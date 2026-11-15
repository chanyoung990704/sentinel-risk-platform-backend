package com.aivle.project.company.reportjob;
import static org.assertj.core.api.Assertions.*;
import com.aivle.project.reporting.internal.company.reportjob.port.*;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import com.aivle.project.reporting.internal.company.reportjob.adapter.HttpMockReportGenerationAdapter;
import com.aivle.project.reporting.internal.company.reportrequest.service.ReportRequestService;
import com.aivle.project.reporting.api.CustomReportCommand;
import com.aivle.project.reporting.internal.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.support.CustomReportIntegrationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("realinfra") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
	"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio",
	"spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false"})
class ReportGenerationContractIntegrationTest extends CustomReportIntegrationSupport {
	@Autowired ReportRequestService requests;@Autowired ReportRequestRepository requestRows;@Autowired ReportSourceReader reader;@Autowired ReportGenerationPort generator;@Autowired ObjectMapper mapper;
	void control(String mode,int failures,int delay)throws Exception {var body=Map.of("response_mode",mode,"fail_first",failures,"delay_ms",delay,"seed","r03-fixture");var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/control/custom-reports")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);}
	@AfterEach void resetMock()throws Exception{control("valid",0,0);}
	UUID accepted(String key,String text){return requests.accept(users.getFirst(),company,key,new CustomReportCommand(null,text)).requestId();}
	@Test void acceptedSnapshotAndAllActualValuesStayFixedWhenPointerChanges()throws Exception {
		// given
		var id=accepted("source-key-1","추가 위험 설명");var request=requestRows.find(id).orElseThrow();var source=reader.load(request.snapshotId(),company,request.targetQuarterKey());String original=jdbc.queryForObject("SELECT snapshot_id FROM active_data_snapshots WHERE family='FINANCIAL'",String.class);
		control("valid",0,0);
		try {
			// D03에서 실제 materialize/verify한 별도 snapshot을 사용하고 그 데이터는 변경하지 않는다.
			String replacement=jdbc.queryForObject("SELECT s.id FROM data_snapshots s WHERE s.state='VERIFIED' AND s.id<>? AND EXISTS(SELECT 1 FROM snapshot_report_versions b WHERE b.snapshot_id=s.id) ORDER BY s.id LIMIT 1",String.class,original);
			jdbc.update("UPDATE active_data_snapshots SET snapshot_id=? WHERE family='FINANCIAL'",replacement);
			// when
			var repeated=reader.load(request.snapshotId(),company,request.targetQuarterKey());var pdf=generator.generate(repeated,new CustomReportInput(request.instructions(),request.inputHash()),id);
			// then
			assertThat(repeated.checksum()).isEqualTo(source.checksum());assertThat(source.cells()).allMatch(c->c.key().valueType().equals("ACTUAL")&&c.key().quarterKey()<=request.targetQuarterKey()&&c.provenance().isEmpty());
			assertThat(new String(pdf.bytes(),0,5,java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
			String journal=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/journal")).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
			var matches=mapper.readTree(journal).findParents("requestId").stream().filter(e->e.path("requestId").asText().equals(id.toString())).toList();
			assertThat(matches).hasSize(1);assertThat(matches.getFirst().path("sourceChecksum").asText()).isEqualTo(source.checksum());assertThat(matches.getFirst().path("sourceSnapshot").asText()).isEqualTo(request.snapshotId().toString());assertThat(journal).doesNotContain("추가 위험 설명");
		} finally {jdbc.update("UPDATE active_data_snapshots SET snapshot_id=? WHERE family='FINANCIAL'",original);}
	}
	@Test void differentAdditionalInputsProduceDistinctPayloadHashesAndPdf()throws Exception {
		// given
		control("valid",0,0);var a=requestRows.find(accepted("source-key-a","비교 항목 A")).orElseThrow();var b=requestRows.find(accepted("source-key-b","비교 항목 B")).orElseThrow();
		// when
		var pa=generator.generate(reader.load(a.snapshotId(),company,a.targetQuarterKey()),new CustomReportInput(a.instructions(),a.inputHash()),a.id());var pb=generator.generate(reader.load(b.snapshotId(),company,b.targetQuarterKey()),new CustomReportInput(b.instructions(),b.inputHash()),b.id());
		// then
		assertThat(a.inputHash()).isNotEqualTo(b.inputHash());assertThat(pa.sha256()).isNotEqualTo(pb.sha256());
	}
	@Test void adapterDoesNotAutomaticallyRetryTransientHttpErrors()throws Exception {
		// given
		control("valid",1,0);var r=requestRows.find(accepted("source-retry","재시도 원장 분리")).orElseThrow();var source=reader.load(r.snapshotId(),company,r.targetQuarterKey());var input=new CustomReportInput(r.instructions(),r.inputHash());
		// when / then
		assertThatThrownBy(()->generator.generate(source,input,r.id())).isInstanceOfSatisfying(ReportGenerationFailure.class,e->assertThat(e.retryable()).isTrue());
		assertThat(generator.generate(source,input,r.id()).bytes()).isNotEmpty();
	}
	@Test void adapterDoesNotSilentlyRetryAfterRemoteProcessingLosesResponse()throws Exception {
		// given: 원격 처리는 끝났지만 첫 응답만 유실한다.
		var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/control/custom-reports")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"drop_first\":1}")).build(),HttpResponse.BodyHandlers.discarding());assertThat(response.statusCode()).isEqualTo(200);
		var request=requestRows.find(accepted("adapter-response-loss","재시도 예산 경계")).orElseThrow();var source=reader.load(request.snapshotId(),company,request.targetQuarterKey());var input=new CustomReportInput(request.instructions(),request.inputHash());
		// when / then: 숨은 HTTP 재전송 대신 durable worker가 다음 시도를 기록해야 한다.
		assertThatThrownBy(()->generator.generate(source,input,request.id())).isInstanceOfSatisfying(ReportGenerationFailure.class,failure->assertThat(failure.retryable()).isTrue());
		var journal=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/journal")).GET().build(),HttpResponse.BodyHandlers.ofString());
		long calls=0;for(var entry:mapper.readTree(journal.body()))if(entry.path("requestId").asText().equals(request.id().toString()))calls++;
		assertThat(calls).isEqualTo(1);assertThat(generator.generate(source,input,request.id()).bytes()).isNotEmpty();
	}
	@Test void corruptEmptyTruncatedAndOversizePdfArePermanentFailures()throws Exception {
		// given
		var r=requestRows.find(accepted("source-badpdf","PDF 검증")).orElseThrow();var source=reader.load(r.snapshotId(),company,r.targetQuarterKey());
		for(String mode:List.of("not_pdf","wrong_type","empty","truncated","oversize")) {
			control(mode,0,0);
			// when / then
			assertThatThrownBy(()->generator.generate(source,new CustomReportInput(r.instructions(),r.inputHash()),r.id())).isInstanceOfSatisfying(ReportGenerationFailure.class,e->assertThat(e.retryable()).isFalse());
		}
	}
	@Test void timeoutIsTransientAndRemoteAddressesAreNeverCalled()throws Exception {
		// given
		var r=requestRows.find(accepted("source-timeout","응답 제한")).orElseThrow();var source=reader.load(r.snapshotId(),company,r.targetQuarterKey());var input=new CustomReportInput(r.instructions(),r.inputHash());control("valid",0,1500);
		// when / then
		assertThatThrownBy(()->new HttpMockReportGenerationAdapter(new com.aivle.project.aiintegration.internal.LocalAiPdfClient("http://127.0.0.1:18081",100,200),new com.fasterxml.jackson.databind.ObjectMapper()).generate(source,input,r.id())).isInstanceOfSatisfying(ReportGenerationFailure.class,e->assertThat(e.retryable()).isTrue());
		assertThatThrownBy(()->new HttpMockReportGenerationAdapter(new com.aivle.project.aiintegration.internal.LocalAiPdfClient("https://example.invalid",100,200),new com.fasterxml.jackson.databind.ObjectMapper()).generate(source,input,r.id())).isInstanceOfSatisfying(ReportGenerationFailure.class,e->assertThat(e.code()).isEqualTo("AI_NOT_LOCAL"));
	}
}
