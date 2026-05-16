package io.vertx.ext.healthchecks.collector;

import java.util.Objects;

class HealthCheckRegistrationMessage {

  private final String healthCheckName;
  private final String healthCheckAddress;

  HealthCheckRegistrationMessage(String healthCheckName, String healthCheckAddress) {
    this.healthCheckName = healthCheckName;
    this.healthCheckAddress = healthCheckAddress;
  }

  public String getHealthCheckName() {
    return healthCheckName;
  }

  public String getHealthCheckAddress() {
    return healthCheckAddress;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    HealthCheckRegistrationMessage that = (HealthCheckRegistrationMessage) o;
    return Objects.equals(healthCheckName, that.healthCheckName) && Objects.equals(healthCheckAddress, that.healthCheckAddress);
  }

  @Override
  public int hashCode() {
    return Objects.hash(healthCheckName, healthCheckAddress);
  }

}
