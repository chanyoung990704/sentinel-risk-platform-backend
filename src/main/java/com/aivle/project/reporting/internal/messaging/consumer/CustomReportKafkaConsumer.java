package com.aivle.project.reporting.internal.messaging.consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @ConditionalOnProperty(name="custom-report.messaging.enabled",havingValue="true")
public class CustomReportKafkaConsumer {
	private final ReportWireProtocol protocol;private final com.aivle.project.reporting.internal.messaging.inbox.ReportJobInbox inbox;private final com.aivle.project.reporting.internal.messaging.quarantine.QuarantineService quarantine;private final org.springframework.beans.factory.ObjectProvider<ReportDeliveryObserver> observers;
	public CustomReportKafkaConsumer(ReportWireProtocol protocol,com.aivle.project.reporting.internal.messaging.inbox.ReportJobInbox inbox,com.aivle.project.reporting.internal.messaging.quarantine.QuarantineService quarantine,org.springframework.beans.factory.ObjectProvider<ReportDeliveryObserver> observers){this.protocol=protocol;this.inbox=inbox;this.quarantine=quarantine;this.observers=observers;}
	@org.springframework.kafka.annotation.KafkaListener(id="customReportRequests",topics=com.aivle.project.reporting.api.CustomReportTopics.REQUEST,groupId="${custom-report.messaging.group-id:sentinel-custom-report-jobs-v1}",containerFactory="customReportKafkaListenerContainerFactory",autoStartup="${custom-report.messaging.auto-startup:${spring.kafka.listener.auto-startup:true}}")
	public void onRequest(ConsumerRecord<byte[],byte[]> record,Acknowledgment acknowledgment){
		try{
			observers.orderedStream().forEach(observer->observer.onReceived(record));
			try{inbox.accept(protocol.decode(record));}
			catch(PoisonReportMessage invalid){quarantine.quarantine(record,invalid.getMessage());}
			catch(com.aivle.project.reporting.api.ReportRequestException rejected){
				String code=rejected.getErrorCode().getCode();
				if(java.util.Set.of("CUSTOM_REPORT_INVALID","CUSTOM_REPORT_CONFLICT","CUSTOM_REPORT_NOT_FOUND").contains(code))quarantine.quarantine(record,code);else throw rejected;
			}
			// proxied 서비스가 commit한 다음에만 원본 offset을 전진시킨다.
			observers.orderedStream().forEach(observer->observer.beforeAck(record));acknowledgment.acknowledge();
		}catch(RuntimeException unavailable){observers.orderedStream().forEach(observer->observer.onRetry(record,"DELIVERY_RETRY"));throw new IllegalStateException("DELIVERY_RETRY");}
	}
}
