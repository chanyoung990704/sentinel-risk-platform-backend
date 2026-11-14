package com.aivle.project.messaging.quarantine;
import com.aivle.project.messaging.consumer.ReportWireProtocol;
import com.aivle.project.messaging.outbox.OutboxWriter;
import com.aivle.project.dataimport.service.ImportPreflightService;
import com.aivle.project.company.reportrequest.model.ReportRequestException;
import java.nio.*;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service public class QuarantineService {
	private final QuarantineRepository repository;private final OutboxWriter outbox;
	public QuarantineService(QuarantineRepository repository,OutboxWriter outbox){this.repository=repository;this.outbox=outbox;}
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public UUID quarantine(ConsumerRecord<byte[],byte[]> record,String reason){
		if(!reason.matches("[A-Z0-9_]{1,64}"))throw new IllegalArgumentException("격리 이유 코드 확인 필요");
		byte[] body=record.value()==null?new byte[0]:record.value(),key=record.key();String bodyHash=ImportPreflightService.sha256(body),keyHash=ImportPreflightService.sha256(key==null?new byte[]{0}:joinMarker(key));
		byte[] first=null;int count=0;java.security.MessageDigest digest;
		try{digest=java.security.MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
		for(var header:record.headers().headers("id")){count++;byte[] value=header.value();if(count==1)first=value;digest.update(ByteBuffer.allocate(4).putInt(value==null?-1:value.length).array());if(value!=null)digest.update(value);}
		String headerHash=java.util.HexFormat.of().formatHex(digest.digest());UUID claimed=count==1?ReportWireProtocol.guess(first):null;UUID id=UUID.randomUUID(),dlt=UUID.randomUUID();
		boolean inserted=repository.insert(id,record.topic(),record.partition(),record.offset(),Arrays.copyOf(body,Math.min(body.length,ReportWireProtocol.MAX_BODY)),body.length,bodyHash,body.length>ReportWireProtocol.MAX_BODY,key==null?null:Arrays.copyOf(key,Math.min(key.length,ReportWireProtocol.MAX_KEY)),keyHash,first==null?null:Arrays.copyOf(first,Math.min(first.length,1024)),headerHash,claimed,ReportWireProtocol.guess(key),reason,dlt);
		var stored=repository.lockSource(record.topic(),record.partition(),record.offset());
		if(!stored.bodyHash().equals(bodyHash)||!stored.keyHash().equals(keyHash)||!stored.headerHash().equals(headerHash))throw new ReportRequestException(ReportRequestException.Code.CONFLICT);
		if(inserted)outbox.appendQuarantine(id,dlt,Map.of("quarantineId",id,"reasonCode",reason,"sourceTopic",record.topic(),"sourcePartition",record.partition(),"sourceOffset",record.offset(),"bodySha256",bodyHash,"bodySize",body.length,"truncated",body.length>ReportWireProtocol.MAX_BODY));
		return stored.id();
	}
	private byte[] joinMarker(byte[] value){byte[] result=new byte[value.length+1];result[0]=1;System.arraycopy(value,0,result,1,value.length);return result;}
}
