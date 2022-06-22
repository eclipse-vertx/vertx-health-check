package io.vertx.ext.healthchecks.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;

import java.util.Objects;

import static io.vertx.ext.healthchecks.impl.StatusHelper.isUp;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HealthCheckHandlerImpl implements HealthCheckHandler {

  //TODO Event Bus support

  private static Logger log = LoggerFactory.getLogger(HealthCheckHandler.class);

  private HealthChecks healthChecks;
  private final AuthProvider authProvider;

  public HealthCheckHandlerImpl(Vertx vertx, AuthProvider provider) {
    this.healthChecks = new HealthChecksImpl(vertx);
    this.authProvider = provider;
  }

  public HealthCheckHandlerImpl(HealthChecks hc, AuthProvider provider) {
    this.healthChecks = Objects.requireNonNull(hc);
    this.authProvider = provider;
  }

  @Override
  public HealthCheckHandler register(String name, Handler<Promise<Status>> procedure) {
    healthChecks.register(name, procedure);
    return this;
  }

  @Override
  public HealthCheckHandler register(String name, long timeout, Handler<Promise<Status>> procedure) {
    healthChecks.register(name, timeout, procedure);
    return this;
  }


  @Override
  public void handle(RoutingContext rc) {
    // ensure that we get the right offset to the router, either root or sub-router
    String path = Utils.pathOffset(rc.normalisedPath(), rc);

    String id;

    // remove the leading slash to extract the id
    if (path.length() > 0) {
      id = path.substring(1);
    } else {
      id = path;
    }

    if (authProvider != null) {
      // Copy all HTTP header in a json array and params
      JsonObject authData = new JsonObject();
      rc.request().headers().forEach(entry -> authData.put(entry.getKey(), entry.getValue()));
      rc.request().params().forEach(entry -> authData.put(entry.getKey(), entry.getValue()));
      if (rc.request().method() == HttpMethod.POST
        && rc.request().getHeader(HttpHeaders.CONTENT_TYPE) != null
        && rc.request().getHeader(HttpHeaders.CONTENT_TYPE).contains("application/json")) {
        JsonObject json;
        try {
          json = rc.getBodyAsJson();
          if (json != null) {
            authData.mergeIn(json);
          }
        } catch (Exception err) {
          log.error("Invalid authentication json body", err);
        }
      }
      authProvider.authenticate(authData, ar -> {
        if (ar.failed()) {
          rc.response().setStatusCode(403).end();
        } else {
          healthChecks.invoke(id, healthReportHandler(rc));
        }
      });
    } else {
      healthChecks.invoke(id, healthReportHandler(rc));
    }
  }

  private Handler<AsyncResult<JsonObject>> healthReportHandler(RoutingContext rc) {
    return json -> {
      HttpServerResponse response = rc.response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
      if (json.failed()) {
        if (json.cause().getMessage().toLowerCase().contains("not found")) {
          response.setStatusCode(404);
        } else {
          response.setStatusCode(400);
        }
        response.end("{\"message\": \"" + json.cause().getMessage() + "\"}");
      } else {
        buildResponse(json.result(), response);
      }
    };
  }

  private void buildResponse(JsonObject json, HttpServerResponse response) {
    int status = isUp(json) ? 200 : 503;

    if (status == 503 && hasProcedureError(json)) {
      status = 500;
    }

    JsonArray checks = json.getJsonArray("checks");
    if (status == 200 && checks != null && checks.isEmpty()) {
      // Special case, no procedure installed.
      response.setStatusCode(204).end();
      return;
    }

    response
      .setStatusCode(status)
      .end(transform(json));
  }

  @Override
  public synchronized HealthCheckHandler unregister(String name) {
    healthChecks.unregister(name);
    return this;
  }

  private boolean hasProcedureError(JsonObject json) {
    JsonObject data = json.getJsonObject("data");
    if (data != null && data.getBoolean("procedure-execution-failure", false)) {
      return true;
    }

    JsonArray checks = json.getJsonArray("checks");
    if (checks != null) {
      for (int i = 0; i < checks.size(); i++) {
        JsonObject check = checks.getJsonObject(i);
        if (hasProcedureError(check)) {
          return true;
        }
      }
    }

    return false;
  }

  private String transform(JsonObject json) {
    String status = json.getString("status");
    String outcome = json.getString("outcome");
    if (status != null && outcome == null) {
      json.put("outcome", status);
    }
    return json.encode();
  }
}
