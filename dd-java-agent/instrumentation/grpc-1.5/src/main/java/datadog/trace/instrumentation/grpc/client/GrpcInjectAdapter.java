package datadog.trace.instrumentation.grpc.client;

import datadog.trace.instrumentation.api.Propagation;
import io.grpc.Metadata;

public final class GrpcInjectAdapter implements Propagation.Setter<Metadata> {

  public static final GrpcInjectAdapter SETTER = new GrpcInjectAdapter();

  @Override
  public void set(final Metadata carrier, final String key, final String value) {
    carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);
  }
}
