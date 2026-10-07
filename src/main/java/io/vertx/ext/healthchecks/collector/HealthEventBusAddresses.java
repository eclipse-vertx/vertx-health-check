package io.vertx.ext.healthchecks.collector;

class HealthEventBusAddresses {

  private HealthEventBusAddresses() {}

  static String forHealthCheckContributorRegistration(String identifier) {
    return "health.register." + identifier;
  }

  static String forHealthCheckContributorDeregistration(String identifier) {
    return "health.unregister." + identifier;
  }

  static String forHealthCheck(String identifier, String healthCheckName) {
    return "health.procedure." + identifier + "." + healthCheckName;
  }
}
