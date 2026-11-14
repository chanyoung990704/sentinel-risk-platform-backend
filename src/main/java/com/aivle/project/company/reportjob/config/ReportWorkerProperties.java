package com.aivle.project.company.reportjob.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
@Getter @Setter @ConfigurationProperties("custom-report.worker")
@Component @Validated
public class ReportWorkerProperties {
	private boolean enabled=false;
	@Min(1) @Max(4) private int workers=4;
	@Min(3) @Max(300) private int leaseSeconds=30;
	@Min(1) @Max(60) private int heartbeatSeconds=5;
	@Min(100) @Max(20000) private int attemptTimeoutMillis=20000;
	@Min(1) @Max(3) private int maxAttempts=3;
	@Min(0) @Max(5000) private int retryJitterMs=250;
	private int jitterSeed=17;
	@Min(100) @Max(60000) private int recoveryScanMillis=1000;
	@AssertTrue(message="lease는 시도 제한과 두 heartbeat 간격 이상의 여유가 필요합니다")
	public boolean isLeaseSafe(){return leaseSeconds*1000L>=attemptTimeoutMillis+heartbeatSeconds*2000L;}
}
