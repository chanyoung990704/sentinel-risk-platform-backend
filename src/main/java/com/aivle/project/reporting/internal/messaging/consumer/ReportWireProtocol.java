package com.aivle.project.reporting.internal.messaging.consumer;
import com.aivle.project.reporting.api.CustomReportTopics;
import com.aivle.project.reporting.internal.messaging.outbox.*;
import com.aivle.project.reporting.api.ReportEventType;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
@Component public class ReportWireProtocol {
	public static final int MAX_BODY=65536,MAX_KEY=1024;
	private final ObjectReader reader;
	public ReportWireProtocol(ObjectMapper mapper){
		var copy=mapper.copy();copy.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(MAX_BODY).maxNumberLength(64).build());copy.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
		reader=copy.readerFor(ReportEvent.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
	}
	public ReportEvent decode(ConsumerRecord<byte[],byte[]> record){
		if(!CustomReportTopics.REQUEST.equals(record.topic())||record.partition()<0||record.offset()<0)throw new PoisonReportMessage("SOURCE_INVALID");
		if(record.value()==null||record.value().length==0)throw new PoisonReportMessage("MESSAGE_EMPTY");
		if(record.value().length>MAX_BODY)throw new PoisonReportMessage("MESSAGE_SIZE");
		if(record.key()==null||record.key().length>MAX_KEY)throw new PoisonReportMessage("KEY_INVALID");
		ReportEvent event;
		try{event=reader.readValue(strict(record.value()));}catch(JsonProcessingException invalid){throw new PoisonReportMessage("MESSAGE_JSON");}
		if(event==null||event.eventId()==null||event.requestId()==null||event.occurredAt()==null||event.schemaVersion()!=1||event.eventType()!=ReportEventType.REQUEST_ACCEPTED||event.payload()==null)throw new PoisonReportMessage("ENVELOPE_INVALID");
		if(!strict(record.key()).equals(event.requestId().toString()))throw new PoisonReportMessage("KEY_MISMATCH");
		int count=0;byte[] id=null;for(var header:record.headers().headers("id")){count++;id=header.value();}
		if(count!=1||id==null||id.length>128||!strict(id).equals(event.eventId().toString()))throw new PoisonReportMessage("HEADER_MISMATCH");
		return event;
	}
	private String strict(byte[] bytes){try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException invalid){throw new PoisonReportMessage("MESSAGE_UTF8");}}
	public static UUID guess(byte[] bytes){if(bytes==null||bytes.length>128)return null;try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();UUID value=UUID.fromString(text);return value.toString().equals(text)?value:null;}catch(IllegalArgumentException|CharacterCodingException invalid){return null;}}
}
