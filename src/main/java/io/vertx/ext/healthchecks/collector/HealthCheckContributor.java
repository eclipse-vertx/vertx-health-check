package io.vertx.ext.healthchecks.collector;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class HealthCheckContributor {

  private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckContributor.class);

  private static final int REGISTRATION_RETRY_DELAY = 100;
  private static final int REGISTRATION_RETRY_COUNT = 50;
  private static final int DEFAULT_TIMEOUT_MS = 2000;

  private final HashMap<String, Integer> registerRetryCounters = new HashMap<>();
  private final Set<String> healthChecks = new HashSet<>();
  private final Vertx vertx;
  private final String healthCheckCollectorIdentifier;

  public static HealthCheckContributor create(Vertx vertx, String healthCheckCollectorIdentifier) {
    return new HealthCheckContributor(vertx, healthCheckCollectorIdentifier);
  }

  private HealthCheckContributor(Vertx vertx, String healthCheckCollectorIdentifier) {
    this.vertx = vertx;
    this.healthCheckCollectorIdentifier = healthCheckCollectorIdentifier;
  }

  public void register(String healthCheckName, Handler<Promise<Status>> procedure) {
    register(healthCheckName, DEFAULT_TIMEOUT_MS, procedure);
  }

  public void register(String healthCheckName, int timeoutMs, Handler<Promise<Status>> procedure) {
    // setup health check
    var myHealthCheckAddress =
        HealthEventBusAddresses.forHealthCheck(healthCheckCollectorIdentifier, healthCheckName);
    registerHealthCheck(myHealthCheckAddress, timeoutMs, procedure);

    // register at collector
    var registerAddress =
        HealthEventBusAddresses.forHealthCheckContributorRegistration(
            healthCheckCollectorIdentifier);
    var registerMsg = new HealthCheckRegistrationMessage(healthCheckName, myHealthCheckAddress);
    registerHealthCheckAtCollector(registerAddress, registerMsg);
  }

  private void registerHealthCheck(
      String myHealthCheckAddress, int timeoutMs, Handler<Promise<Status>> procedure) {
    vertx
        .eventBus()
        .consumer(
            myHealthCheckAddress,
            msg -> {
              Future<String> timeoutFuture =
                  vertx
                      .timer(timeoutMs)
                      .compose(
                          v ->
                              Future.failedFuture(
                                  new TimeoutException(
                                      "Health check timed out after " + timeoutMs + "ms")));

              Promise<Status> timeCheckPromise = Promise.promise();
              Future.any(timeCheckPromise.future(), timeoutFuture)
                  .map(HealthCheckContributor::extractAnyResult)
                  .onComplete(
                      status -> msg.reply(status.toJson()), err -> msg.fail(500, err.getMessage()));
              procedure.handle(timeCheckPromise);
            });
  }

  private void registerHealthCheckAtCollector(
      String address, HealthCheckRegistrationMessage registerMsg) {
    vertx
      .eventBus()
      .request(address, JsonObject.mapFrom(registerMsg))
      .onSuccess(msg -> {
        healthChecks.add(registerMsg.getHealthCheckName());
        LOGGER.debug("Health check registered at " + healthCheckCollectorIdentifier + ": " + registerMsg.getHealthCheckName());
      })
      .onFailure(err -> {
        // retry logic
        int retries = registerRetryCounters.getOrDefault(registerMsg.getHealthCheckName(), 0);
        if (retries <= REGISTRATION_RETRY_COUNT) {
          retries++;
          registerRetryCounters.put(registerMsg.getHealthCheckName(), retries);
          vertx.setTimer(
            REGISTRATION_RETRY_DELAY,
            ignored -> registerHealthCheckAtCollector(address, registerMsg));
        }
      });
  }

  public void unregister(String healthCheckName) {
    var deregisterMsg = new HealthCheckDeregistrationMessage(healthCheckName);
    var address = HealthEventBusAddresses.forHealthCheckContributorDeregistration(healthCheckCollectorIdentifier);
    vertx
        .eventBus()
        .request(address, JsonObject.mapFrom(deregisterMsg))
        .onSuccess(msg -> {
          healthChecks.remove(healthCheckName);
          LOGGER.debug("Health check deregistered at " + healthCheckCollectorIdentifier + ": " + healthCheckName);
        });
  }

  public void unregisterAll() {
    healthChecks.forEach(this::unregister);
  }

  private static Status extractAnyResult(CompositeFuture compositeFuture) {
    for (int i = 0; i < compositeFuture.size(); i++) {
      Status result = compositeFuture.resultAt(i);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
