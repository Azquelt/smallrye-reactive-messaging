package acme;

import io.smallrye.reactive.messaging.annotations.Channel;
import io.smallrye.reactive.messaging.annotations.Emitter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class BeanUsingAnEmitter {

  @Inject
  @Channel("test-messages")
  Emitter<String> emitter;

  public void periodicallySendMqttMessage() {
    AtomicInteger counter = new AtomicInteger();
    Executors.newSingleThreadScheduledExecutor()
      .scheduleAtFixedRate(() -> {
          emitter.send("Hello " + counter.getAndIncrement());
        },
        1, 1, TimeUnit.SECONDS);
  }

}
