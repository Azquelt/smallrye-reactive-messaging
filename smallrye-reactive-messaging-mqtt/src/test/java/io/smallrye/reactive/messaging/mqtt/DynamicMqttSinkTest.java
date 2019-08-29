package io.smallrye.reactive.messaging.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.After;
import org.junit.Test;

public class DynamicMqttSinkTest extends MqttTestBase {

    private WeldContainer container;

    @After
    public void cleanup() {
        if (container != null) {
            container.close();
        }
    }

    @Test
    public void testABeanProducingMessagesSentToMQTT() throws InterruptedException {
        Weld weld = baseWeld(getConfig());
        weld.addBeanClass(DynamicTopicProducingBean.class);

        CountDownLatch latch = new CountDownLatch(10);
        final List<MqttMessage> rawMessages = new ArrayList<>(10);
        final List<String> topics = new ArrayList<>(10);
        usage.consumeRaw("#", 10, 10, TimeUnit.SECONDS, null,
                (topic, msg) -> {
                    latch.countDown();
                    topics.add(topic);
                    rawMessages.add(msg);
                });

        container = weld.initialize();

        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(topics.size()).isEqualTo(10);
        assertThat(rawMessages.size()).isEqualTo(10);
        MqttMessage firstMessage = rawMessages.get(0);
        assertThat(firstMessage.getQos()).isEqualTo(1);
        assertThat(firstMessage.isRetained()).isFalse();
    }

    private MapBasedConfig getConfig() {
        String prefix = "mp.messaging.outgoing.sink.";
        Map<String, Object> config = new HashMap<>();
        config.put(prefix + "topic", "sink");
        config.put(prefix + "connector", MqttConnector.CONNECTOR_NAME);
        config.put(prefix + "host", System.getProperty("mqtt-host"));
        config.put(prefix + "port", Integer.valueOf(System.getProperty("mqtt-port")));
        if (System.getProperty("mqtt-user") != null) {
            config.put(prefix + "username", System.getProperty("mqtt-user"));
            config.put(prefix + "password", System.getProperty("mqtt-pwd"));
        }
        return new MapBasedConfig(config);
    }
}
