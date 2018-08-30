package io.smallrye.reactive.messaging;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.logging.log4j.util.Strings;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class MediatorConfiguration {

  private final Method method;

  private final Class<?> beanClass;

  private Incoming incoming;

  private Outgoing outgoing;

  private boolean consumeAsStream;

  private Type consumedPayloadType;
  private Type producedPayloadType;
  private boolean isSubscriber;

  public MediatorConfiguration(Method method, Class<?> beanClass) {
    this.method = Objects.requireNonNull(method, "'method' must be set");
    this.beanClass =  Objects.requireNonNull(beanClass, "'beanClass' must be set");
  }

  public boolean isPlainSubscriber() {
    return isSubscriber;
  }

  public MediatorConfiguration setOutgoing(Outgoing outgoing) {
    this.outgoing = outgoing;
    if (outgoing != null  && isVoid()) {
      throw new IllegalStateException("The method " + methodAsString() + " does not return a result but is annotated with @Outgoing. " +
        "The method must return 'something'");
    }
    validateOutgoingSignature();
    return this;
  }

  public MediatorConfiguration setIncoming(Incoming incoming) {
    this.incoming = incoming;
    validateIncomingSignature();
    return this;
  }

  public String getOutgoing() {
    if (outgoing == null) {
      return null;
    }
    return outgoing.value();
  }

  public String getOutgoingProviderType() {
    if (outgoing == null) {
      return null;
    }
    // TODO Do we need to check if it's just MessagingProvider
    return outgoing.provider().getName();
  }

  private void validateOutgoingSignature() {
    if (outgoing == null) {
      return;
    }
    Class<?> type = method.getReturnType();
    ParameterizedType parameterizedType = null;
    if (method.getGenericReturnType() instanceof ParameterizedType) {
      parameterizedType = (ParameterizedType) method.getGenericReturnType();
    }

    // We know that the method cannot return null at the point.
    // TODO We should still check for CompletionStage<Void>, or Publisher<Void> which would be invalid.

    if (parameterizedType == null) {
      producedPayloadType = type;
      return;
    }

    if (ClassUtils.isAssignable(type, Publisher.class)
      || ClassUtils.isAssignable(type, Message.class)
      || ClassUtils.isAssignable(type, CompletionStage.class)
      || ClassUtils.isAssignable(type, PublisherBuilder.class)
      ) {
      // Extract the internal type - for all these type it's the first (unique) parameter
      producedPayloadType = parameterizedType.getActualTypeArguments()[0];
      return;
    }

    if (ClassUtils.isAssignable(type, ProcessorBuilder.class)
      || ClassUtils.isAssignable(type, Processor.class)) {
      // Extract the internal type - for all these type it's the second parameter
      producedPayloadType = parameterizedType.getActualTypeArguments()[1];
      return;
    }

    throw new IllegalStateException("Unable to determine the type of message returned by the method: " + methodAsString());
  }

  private void validateIncomingSignature() {
    if (incoming == null) {
      return;
    }
    if (method.getParameterCount() == 0) {
      // The method must returned a ProcessorBuilder or a Processor, in this case, the consumed type is the first parameter.
      Class<?> type = method.getReturnType();
      ParameterizedType parameterizedType = null;
      if (method.getGenericReturnType() instanceof  ParameterizedType) {
        parameterizedType = (ParameterizedType) method.getGenericReturnType();
      }

      // Supported types are: Processor, ProcessorBuilder, Subscriber, in all case the parameterized type must be set.
      if (parameterizedType == null) {
        throw new IllegalStateException("Unable to determine the consumed type for " + methodAsString() + " - expected a type parameter in the returned type");
      }

      if (
           !ClassUtils.isAssignable(ProcessorBuilder.class, type) && !ClassUtils.isAssignable(Processor.class, type)
        && !ClassUtils.isAssignable(Subscriber.class, type) && !ClassUtils.isAssignable(SubscriberBuilder.class, type)
        ) {
        throw new IllegalStateException("Invalid returned type for " + methodAsString() + ", supported types are Processor, ProcessorBuilder, and Subscriber");
      }

      consumedPayloadType = parameterizedType.getActualTypeArguments()[0];
      if (parameterizedType.getActualTypeArguments().length > 1) {
        // TODO this won't work for implementation having a single parameter, or more...
        producedPayloadType = parameterizedType.getActualTypeArguments()[1];
      }
      consumeAsStream = true;
      isSubscriber = ClassUtils.isAssignable(Subscriber.class, type)
        && ! ClassUtils.isAssignable(ProcessorBuilder.class, type) && ! ClassUtils.isAssignable(Processor.class, type);
    }

    if (method.getParameterCount() == 1) {
      // we need to check the parameter.
      Class<?> type = method.getParameterTypes()[0];
      Type paramType = method.getGenericParameterTypes()[0];
      consumeAsStream = ClassUtils.isAssignable(type, Publisher.class)  || ClassUtils.isAssignable(type, PublisherBuilder.class);
      if (paramType instanceof ParameterizedType) {
        consumedPayloadType = ((ParameterizedType) paramType).getActualTypeArguments()[0];
      } else {
        consumedPayloadType = type;
      }
    }

    // TODO validate the converters,
    // TODO validate the types in the parameters
  }

  public String getIncoming() {
    if (incoming == null) {
      return null;
    }
    if (Strings.isBlank(incoming.value())) {
      // TODO is that true?
     throw new IllegalArgumentException("The @Incoming annotation must contain a non-blank value");
    }
    return incoming.value();
  }

  public String getIncomingProviderType() {
    if (incoming == null) {
      return null;
    }
    return incoming.provider().getName();
  }

  public boolean isPublisher() {
    return outgoing != null;
  }

  public boolean isSubscriber() {
    return incoming != null;
  }

  public Class<?> getReturnType() {
    if (! isVoid()) {
      return method.getReturnType();
    }
    return null;
  }

  public Class<?> getParameterType() {
    if (method.getParameterCount() == 1) {
      return method.getParameterTypes()[0];
    }
    return null;
  }

  public String methodAsString() {
    return beanClass.getName() + "#" + method.getName();
  }

  private boolean isVoid() {
    return method.getReturnType().equals(Void.TYPE);
  }

  public Method getMethod() {
    return method;
  }

  public static boolean isClassASubTypeOf(Class<?> maybeChild, Class<?> maybeParent) {
    return maybeParent.isAssignableFrom(maybeChild);
  }

  public Class<?> getBeanClass() {
    return beanClass;
  }

  public boolean consumeAsStream() {
    return consumeAsStream;
  }

  public boolean isConsumingPayloads() {
    return consumedPayloadType != null && !TypeUtils.isAssignable(consumedPayloadType, Message.class);
  }

  public boolean isProducingPayloads() {
    return producedPayloadType != null && !TypeUtils.isAssignable(producedPayloadType, Message.class);
  }

  public boolean isReturningCompletionStageOfMessage() {
    Class<?> type = method.getReturnType();
    if (! isClassASubTypeOf(type, CompletionStage.class)) {
      return false;
    }
    Type grt = method.getGenericReturnType();
    if (grt instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) grt;
      Type param = pt.getActualTypeArguments()[0];
      return TypeUtils.isAssignable(param, Message.class);
    }
    return false;
  }
}