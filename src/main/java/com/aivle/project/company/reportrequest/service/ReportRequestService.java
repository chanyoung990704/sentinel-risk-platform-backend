package com.aivle.project.company.reportrequest.service;
import com.aivle.project.company.reportrequest.model.*;
import org.springframework.stereotype.Service;
import com.aivle.project.company.reportrequest.entity.ReportRequestEntity;
import com.aivle.project.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.messaging.outbox.*;
import com.aivle.project.dataimport.service.ImportPreflightService;
import com.aivle.project.quarter.support.QuarterCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
@Service public class ReportRequestService {
	private final ReportRequestRepository repository;private final ReportAdmissionService admission;private final OutboxWriter outbox;private final ObjectMapper mapper;private final TransactionTemplate transaction;
	public ReportRequestService(ReportRequestRepository repository,ReportAdmissionService admission,OutboxWriter outbox,ObjectMapper mapper,PlatformTransactionManager manager){this.repository=repository;this.admission=admission;this.outbox=outbox;this.mapper=mapper;transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
	public AcceptedReportRequest accept(Long userId,Long companyId,String key,CustomReportCommand command) {
		if(userId==null||companyId==null||key==null||!key.matches("[A-Za-z0-9._:-]{8,128}")||command==null||command.additionalInstructions()==null)throw invalid();
		String text=command.additionalInstructions().replace("\r\n","\n").strip();int length=text.codePointCount(0,text.length());
		if(length<1||length>2000||!StandardCharsets.UTF_8.newEncoder().canEncode(text))throw invalid();
		repository.requireUser(userId);repository.requireCompany(companyId);
		var body=new TreeMap<String,Object>();body.put("additionalInstructions",text);body.put("targetQuarterKey",command.targetQuarterKey());
		String inputHash;try{inputHash=hash(mapper.writeValueAsString(body));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw invalid();}
		String keyHash=hash(key);var old=repository.findByKey(userId,keyHash);if(old.isPresent())return replay(old.get(),companyId,inputHash);
		try{return transaction.execute(tx->{
			var capacity=admission.lock(userId);
			// 최초 consistent read는 GLOBAL 잠금 뒤에 수행한다. 기존 요청에는 행 잠금/슬롯을 추가하지 않는다.
			var existing=repository.findByKey(userId,keyHash);if(existing.isPresent())return replay(existing.get(),companyId,inputHash);
			var source=repository.active();int quarter=command.targetQuarterKey()==null?source.baseQuarter():command.targetQuarterKey();
			try{QuarterCalculator.parseQuarterKey(quarter);}catch(IllegalArgumentException invalid){throw invalid();}
			repository.requireValues(source.id(),companyId,quarter);admission.acquire(userId,capacity);
			var request=new ReportRequestEntity(UUID.randomUUID(),userId,companyId,quarter,source.id(),keyHash,inputHash,text,ReportRequestStatus.ACCEPTED,1,true);
			repository.insert(request);outbox.append(outbox.event(request,ReportEventType.REQUEST_ACCEPTED));return accepted(request);
		});}catch(DuplicateKeyException race){var committed=repository.findByKey(userId,keyHash);if(committed.isPresent())return replay(committed.get(),companyId,inputHash);throw race;}
	}
	private AcceptedReportRequest replay(ReportRequestEntity r,long company,String hash){if(r.companyId()!=company||!r.inputHash().equals(hash))throw new ReportRequestException(ReportRequestException.Code.CONFLICT);return accepted(r);}
	private AcceptedReportRequest accepted(ReportRequestEntity r){return new AcceptedReportRequest(r.id(),r.status(),"/api/report-requests/"+r.id());}
	private String hash(String text){return ImportPreflightService.sha256(text.getBytes(StandardCharsets.UTF_8));}
	private ReportRequestException invalid(){return new ReportRequestException(ReportRequestException.Code.INVALID);}
}
