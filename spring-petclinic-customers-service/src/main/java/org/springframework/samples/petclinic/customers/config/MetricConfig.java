package org.springframework.samples.petclinic.customers.config;

import brave.Tracer;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricConfig {

  @Bean
  public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
    return registry -> registry.config()
        .commonTags("application", "customers-service");
  }

  @Bean
  public MeterFilter traceIdDynamicTagFilter(Tracer tracer) {
    return new MeterFilter() {
      @Override
      public Meter.Id map(Meter.Id id) {
        String traceId = "none";
        var span = tracer.currentSpan();
        if (span != null && span.context() != null) {
          traceId = span.context().traceIdString();
        }
        return id.withTag(Tag.of("traceId", traceId));
      }
    };
  }

  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }
}
