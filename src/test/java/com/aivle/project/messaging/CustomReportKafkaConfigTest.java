package com.aivle.project.messaging;
import static org.assertj.core.api.Assertions.*;
import com.aivle.project.messaging.consumer.CustomReportKafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.kafka.listener.ContainerProperties;
class CustomReportKafkaConfigTest {
	@Test void factoryUsesCommitAfterListenerAndNeverAutoCommits() {
		// given / when
		var factory=new CustomReportKafkaConfig().customReportKafkaListenerContainerFactory(new KafkaProperties(),new StaticListableBeanFactory().getBeanProvider(SslBundles.class),"cdc",false,false);
		// then
		assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);assertThat(factory.getContainerProperties().isSyncCommits()).isTrue();assertThat(factory.getContainerProperties().isAsyncAcks()).isFalse();assertThat(factory.getConsumerFactory().getConfigurationProperties()).containsEntry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,1);
	}
	@Test void unselectedRelayOrConcurrentPollingCannotStart() {
		// given / when / then
		var config=new CustomReportKafkaConfig();var bundles=new StaticListableBeanFactory().getBeanProvider(SslBundles.class);
		assertThatThrownBy(()->config.customReportKafkaListenerContainerFactory(new KafkaProperties(),bundles,"polling",false,false)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(()->config.customReportKafkaListenerContainerFactory(new KafkaProperties(),bundles,"cdc",true,false)).isInstanceOf(IllegalStateException.class);
	}
}
