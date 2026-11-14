package com.aivle.project.company.reportjob;
import static org.assertj.core.api.Assertions.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.company.reportjob.service.*;
import com.aivle.project.company.reportjob.adapter.LocalReportArtifactStore;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
@Tag("realinfra") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false"})
class ReportReconciliationIntegrationTest extends ReportFlowIntegrationSupport {
	@Autowired ReportReconciliationService reconcile;@Autowired ReportResultService results;@Autowired LocalReportArtifactStore store;
	@Test void countsMissingRequestsJobsAndArtifactsAndDetectsFileDamageAndOrphans()throws Exception {
		// given
		var accepted=requests.accept(users.getFirst(),company,"reconcile-"+UUID.randomUUID(),new com.aivle.project.company.reportrequest.model.CustomReportCommand(null,"대조 시험")).requestId();var failed=enqueue();var f=claim();claims.failOrRetry(f,"PDF_INVALID",false,clock.now());var success=enqueue();var lease=claim();results.storeAndComplete(lease,generate(lease));var ids=List.of(accepted,failed,success,UUID.randomUUID());
		// when / then
		var result=reconcile.reconcile(ids);assertThat(result.requested()).isEqualTo(4);assertThat(result.accepted()).isEqualTo(3);assertThat(result.nonterminal()).isEqualTo(1);assertThat(result.failed()).isEqualTo(1);assertThat(result.succeeded()).isEqualTo(1);assertThat(result.missingRequests()).isEqualTo(1);assertThat(result.missingJobs()).isZero();assertThat(result.missingFiles()).isZero();assertThat(result.invalidArtifacts()).isZero();assertThat(result.duplicateArtifacts()).isZero();
		String key=jdbc.queryForObject("SELECT storage_key FROM report_artifacts WHERE request_id=?",String.class,success.toString());Path file=store.path(key);byte[] original=Files.readAllBytes(file);try{Files.writeString(file,"damage");assertThat(reconcile.reconcile(ids).invalidArtifacts()).isEqualTo(1);Files.delete(file);assertThat(reconcile.reconcile(ids).missingFiles()).isEqualTo(1);}finally{Files.write(file,original);}
		Path orphan=file.resolveSibling(UUID.randomUUID()+".pdf");try{Files.writeString(orphan,"orphan");assertThat(reconcile.reconcile(ids).unlinkedFiles()).isEqualTo(1);}finally{Files.delete(orphan);}
		jdbc.update("DELETE FROM report_jobs WHERE request_id=?",failed.toString());assertThat(reconcile.reconcile(ids).missingJobs()).isEqualTo(1);
	}
	@Test void rejectsEmptyDuplicateNullAndOversizedScopes() {
		// given / when / then
		assertThatThrownBy(()->reconcile.reconcile(List.of())).isInstanceOf(IllegalArgumentException.class);
		UUID id=UUID.randomUUID();assertThatThrownBy(()->reconcile.reconcile(List.of(id,id))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->reconcile.reconcile(null)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->reconcile.reconcile(Collections.nCopies(1001,id))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->reconcile.reconcile(Arrays.asList(id,null))).isInstanceOf(IllegalArgumentException.class);
	}
	@Test void separateReadOnlyCliReportsOnlyRequestedScope()throws Exception {
		// given
		var id=requests.accept(users.getFirst(),company,"cli-reconcile-"+UUID.randomUUID(),new com.aivle.project.company.reportrequest.model.CustomReportCommand(null,"읽기 대조 시험")).requestId();String classpath=Files.readString(Path.of("build/portfolio-report-classpath.txt")).strip();Path log=Files.createTempFile("sentinel-reconcile-cli-", ".log");Process process=null;
		try{
			// when
			var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx128m","-cp",classpath,"com.aivle.project.company.reportjob.cli.ReportReconciliationCommand",id.toString());var env=builder.environment();env.clear();for(String name:List.of("PATH","HOME","USER","LANG","JAVA_HOME"))if(System.getenv(name)!=null)env.put(name,System.getenv(name));env.put("SPRING_PROFILES_ACTIVE","local");env.put("PORTFOLIO_SECRET_DIR",com.aivle.project.support.PortfolioSecretPaths.directory().toString());env.put("PORTFOLIO_DATA_DIR",System.getenv().getOrDefault("PORTFOLIO_DATA_DIR",Path.of(System.getProperty("user.home"),".local/share/sentinel/portfolio").toString()));process=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
			// then
			assertThat(process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();assertThat(Files.readString(log)).contains("\"accepted\":1","\"nonterminal\":1","\"missingRequests\":0").doesNotContain("읽기 대조 시험");assertThat(jdbc.queryForObject("SELECT status FROM report_requests WHERE id=?",String.class,id.toString())).isEqualTo("ACCEPTED");
		}finally{if(process!=null&&process.isAlive()){process.destroyForcibly();process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);}Files.deleteIfExists(log);}
	}
}
