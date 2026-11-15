package com.aivle.project.reporting.internal.messaging.consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
/** 기본 구현은 없으며 local 시험에서만 결정적 barrier를 주입한다. */
public interface ReportDeliveryObserver {
	default void onReceived(ConsumerRecord<byte[],byte[]> record) { }
	default void beforeAck(ConsumerRecord<byte[],byte[]> record) { }
	default void onRetry(ConsumerRecord<byte[],byte[]> record,String code) { }
}
