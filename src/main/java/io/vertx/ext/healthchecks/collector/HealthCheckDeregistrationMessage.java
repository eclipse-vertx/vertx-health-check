package io.vertx.ext.healthchecks.collector;

import java.util.Objects;

class HealthCheckDeregistrationMessage {

  private final String healthCheckName;

  HealthCheckDeregistrationMessage(String healthCheckName) {
    this.healthCheckName = healthCheckName;
  }

  public String getHealthCheckName() {
    return healthCheckName;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    HealthCheckDeregistrationMessage that = (HealthCheckDeregistrationMessage) o;
    return Objects.equals(healthCheckName, that.healthCheckName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(healthCheckName);
  }

}
