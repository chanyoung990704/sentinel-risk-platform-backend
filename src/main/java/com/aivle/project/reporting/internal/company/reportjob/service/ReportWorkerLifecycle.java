package com.aivle.project.reporting.internal.company.reportjob.service;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
/** Kafka 전달과 독립적으로 DB READY 및 만료 lease를 주기적으로 재확인한다. */
@Component @ConditionalOnProperty(name="custom-report.worker.enabled",havingValue="true")
public class ReportWorkerLifecycle implements SmartLifecycle {
	private final ReportJobWorker worker;private final ReportJobRecovery recovery;private final ReportClock clock;private final com.aivle.project.reporting.internal.company.reportjob.config.ReportWorkerProperties properties;private volatile boolean running;private ScheduledExecutorService timer;
	public ReportWorkerLifecycle(ReportJobWorker worker,ReportJobRecovery recovery,ReportClock clock,com.aivle.project.reporting.internal.company.reportjob.config.ReportWorkerProperties properties){this.worker=worker;this.recovery=recovery;this.clock=clock;this.properties=properties;}
	public synchronized void start(){if(running)return;running=true;worker.startAccepting();timer=Executors.newSingleThreadScheduledExecutor();timer.scheduleWithFixedDelay(()->{try{recovery.recover(clock.now());worker.runOnce();}catch(RuntimeException ignored){/* 다음 scan이 DB 장애 뒤 재개한다. 입력/SQL은 기록하지 않는다. */}},0,properties.getRecoveryScanMillis(),TimeUnit.MILLISECONDS);}
	public synchronized void stop(){running=false;worker.stopClaiming();if(timer!=null)timer.shutdownNow();worker.close();}
	public boolean isRunning(){return running;}
}
