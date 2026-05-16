package io.vertx.ext.healthchecks.collector;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import java.util.HashSet;
import java.util.Set;

public class HealthCheckCollector {

  private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckCollector.class);

  private static final String DEFAULT_HEALTH_CHECK_NAME = "default";
  private static final int DEFAULT_TIMEOUT_MS = 2000;

  private final Set<String> registeredHealthChecks = new HashSet<>();
  private final HealthChecks healthChecks;
  private final EventBus eventBus;
  private final String identifier;

  public static HealthCheckCollector create(Vertx vertx, String identifier) {
    return create(HealthChecks.create(vertx), vertx.eventBus(), identifier);
  }

  public static HealthCheckCollector create(
      HealthChecks healthChecks, EventBus eventBus, String identifier) {
    return new HealthCheckCollector(healthChecks, eventBus, identifier);
  }

  private HealthCheckCollector(HealthChecks healthChecks, EventBus eventBus, String identifier) {
    this.healthChecks = healthChecks;
    this.eventBus = eventBus;
    this.identifier = identifier;
    createDefaultHealthCheck();
    setupEventBusCommunication();
  }

  public void register(String name, Handler<Promise<Status>> procedure) {
    register(name, DEFAULT_TIMEOUT_MS, procedure);
  }

  public void register(String name, long timeout, Handler<Promise<Status>> procedure) {
    if (registeredHealthChecks.contains(name)) {
      LOGGER.warn("Already registered health check '" + name + "'. Ignoring...");
      return;
    }
    unregister(DEFAULT_HEALTH_CHECK_NAME);

    healthChecks.register(name, timeout, procedure);
    registeredHealthChecks.add(name);
  }

  public HealthChecks unregister(String name) {
    registeredHealthChecks.remove(name);
    return healthChecks.unregister(name);
  }

  public void unregisterAll() {
    registeredHealthChecks.forEach(this::unregister);
  }

  private void createDefaultHealthCheck() {
    register(DEFAULT_HEALTH_CHECK_NAME, p -> p.complete(Status.OK()));
  }

  private void setupEventBusCommunication() {
    setupHandlingOfHealthCheckRegistrations();
    setupHandlingOfHealthCheckDeregistrations();
  }

  private void setupHandlingOfHealthCheckRegistrations() {
    eventBus.<JsonObject>consumer(
        HealthEventBusAddresses.forHealthCheckContributorRegistration(identifier),
        msg -> {
          HealthCheckRegistrationMessage registrationMsg =
              msg.body().mapTo(HealthCheckRegistrationMessage.class);

          // check if already registered
          if (registeredHealthChecks.contains(registrationMsg.getHealthCheckName())) {
            LOGGER.warn(
                "Already registered health check '" + registrationMsg.getHealthCheckName() + " '. Ignoring contributor...");
            return;
          }

          // register health check
          register(
              registrationMsg.getHealthCheckName(),
              DEFAULT_TIMEOUT_MS,
              promise -> {
                eventBus
                    .<JsonObject>request(
                        registrationMsg.getHealthCheckName(),
                        "ping",
                        new DeliveryOptions().setSendTimeout(DEFAULT_TIMEOUT_MS))
                    .onComplete(
                        resp -> {
                          var status = new Status(resp.body());
                          promise.complete(status);
                        },
                        promise::fail);
              });

          // reply to contributor
          msg.reply("ok");
          LOGGER.debug(
              "New health check registered for " + identifier + ": " + registrationMsg.getHealthCheckName());
        });
  }

  private void setupHandlingOfHealthCheckDeregistrations() {
    eventBus.<JsonObject>consumer(
        HealthEventBusAddresses.forHealthCheckContributorDeregistration(identifier),
        msg -> {
          HealthCheckDeregistrationMessage deregistrationMsg =
              msg.body().mapTo(HealthCheckDeregistrationMessage.class);
          unregister(deregistrationMsg.getHealthCheckName());
          LOGGER.debug("New health deregistered for " + identifier + ": " + msg);
          msg.reply("ok");
        });
  }
}
