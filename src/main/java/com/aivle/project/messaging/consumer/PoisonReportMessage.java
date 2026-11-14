package com.aivle.project.messaging.consumer;
/** 원문이나 parser 예외를 Kafka 오류 로그에 포함하지 않는다. */
public class PoisonReportMessage extends RuntimeException {
	public PoisonReportMessage(String code){super(code);}
}
