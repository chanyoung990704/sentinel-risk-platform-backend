package com.aivle.project.reporting.internal.company.reportjob.service;
import com.aivle.project.reporting.api.CompletionDecision;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import com.aivle.project.reporting.internal.company.reportjob.port.*;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import com.aivle.project.reporting.internal.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.reporting.internal.company.reportjob.config.ReportWorkerProperties;
@Component public class ReportJobWorker implements AutoCloseable {
	private final ReportJobClaimService claims;private final ReportClock clock;private final ReportSourceReader sources;private final ReportGenerationPort generator;private final ObjectProvider<ReportResultPort> results;private final ReportRequestRepository requests;
	private final ExecutorService executor;private final ScheduledExecutorService timers=Executors.newScheduledThreadPool(2);private final Semaphore permits;private final ReportWorkerProperties properties;private volatile boolean accepting=true,closed=false;private final String owner="worker-"+UUID.randomUUID();
	public ReportJobWorker(ReportJobClaimService claims,ReportClock clock,ReportSourceReader sources,ReportGenerationPort generator,ObjectProvider<ReportResultPort> results,ReportRequestRepository requests,ReportWorkerProperties properties){this.claims=claims;this.clock=clock;this.sources=sources;this.generator=generator;this.results=results;this.requests=requests;this.properties=properties;executor=Executors.newFixedThreadPool(properties.getWorkers());permits=new Semaphore(properties.getWorkers());}
	public synchronized List<CompletableFuture<CompletionDecision>> runOnce(){
		if(!accepting||closed)return List.of();int reserved=0;while(permits.tryAcquire())reserved++;if(reserved==0)return List.of();
		List<JobLease> leases;try{leases=claims.claimReady(reserved,owner,clock.now());}catch(RuntimeException e){permits.release(reserved);throw e;}permits.release(reserved-leases.size());
		var futures=new ArrayList<CompletableFuture<CompletionDecision>>();for(var lease:leases){var result=new CompletableFuture<CompletionDecision>();futures.add(result);executor.execute(()->execute(lease,result));}return List.copyOf(futures);
	}
	private void execute(JobLease lease,CompletableFuture<CompletionDecision> result){
		Thread running=Thread.currentThread();
		var finished=new java.util.concurrent.atomic.AtomicBoolean();
		var expired=new java.util.concurrent.atomic.AtomicBoolean();
		var heartbeat=timers.scheduleAtFixedRate(()->{
			if(!finished.get()&&!expired.get())try{claims.heartbeat(lease,clock.now());}
			catch(RuntimeException ignored){/* DB 장애 시 갱신하지 않고 lease 복구에 맡긴다. */}
		},properties.getHeartbeatSeconds(),properties.getHeartbeatSeconds(),TimeUnit.SECONDS);
		var deadline=timers.schedule(()->{
			if(!finished.get()&&expired.compareAndSet(false,true)){
				running.interrupt();
				try{claims.failOrRetry(lease,"ATTEMPT_TIMEOUT",true,clock.now());}
				finally{result.complete(CompletionDecision.STALE);}
			}
		},properties.getAttemptTimeoutMillis(),TimeUnit.MILLISECONDS);
		try{
			var request=requests.find(lease.requestId()).orElseThrow();var source=sources.load(request.snapshotId(),request.companyId(),request.targetQuarterKey());
			var report=generator.generate(source,new CustomReportInput(request.instructions(),request.inputHash()),request.id());
			if(!expired.get()){
				var decision=results.getObject().storeAndComplete(lease,report);
				if(!expired.get())result.complete(decision);
			}
		}catch(RuntimeException failure){
			if(!expired.get()&&!result.isDone()){
				try{claims.failOrRetry(lease,failure instanceof ReportGenerationFailure f?f.code():"INTERNAL_ERROR",!(failure instanceof ReportGenerationFailure f)||f.retryable(),clock.now());result.complete(CompletionDecision.STALE);}
				catch(RuntimeException databaseFailure){result.completeExceptionally(databaseFailure);}
			}
		}finally{finished.set(true);heartbeat.cancel(false);deadline.cancel(false);Thread.interrupted();permits.release();}
	}
	public boolean isAccepting(){return accepting&&!closed;}
	public synchronized void stopClaiming(){accepting=false;}public synchronized void startAccepting(){if(!closed)accepting=true;}
	public void close(){synchronized(this){if(closed)return;closed=true;accepting=false;}executor.shutdown();try{if(!executor.awaitTermination(properties.getAttemptTimeoutMillis(),TimeUnit.MILLISECONDS))executor.shutdownNow();}catch(InterruptedException e){executor.shutdownNow();Thread.currentThread().interrupt();}finally{timers.shutdownNow();}}
}
