package io.vertx.ext.healthchecks.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.CheckResult;
import io.vertx.ext.healthchecks.Status;

import java.util.Objects;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HealthChecksImpl implements HealthChecks {

  private final Vertx vertx;
  private CompositeProcedure root = new DefaultCompositeProcedure();

  public HealthChecksImpl(Vertx vertx) {
    this.vertx = Objects.requireNonNull(vertx);
  }

  @Override
  public HealthChecks register(String name, Handler<Promise<Status>> procedure) {
    return register(name, 1000L, procedure);
  }

  @Override
  public HealthChecks register(String name, long timeout, Handler<Promise<Status>> procedure) {
    Objects.requireNonNull(name);
    if (timeout <= 0) {
      throw new IllegalArgumentException("The timeout must be strictly positive");
    }

    if (name.isEmpty()) {
      throw new IllegalArgumentException("The name must not be empty");
    }
    Objects.requireNonNull(procedure);
    String[] segments = name.split("/");
    CompositeProcedure parent = traverseAndCreate(segments);
    String lastSegment = segments[segments.length - 1];
    parent.add(lastSegment,
      new DefaultProcedure(vertx, lastSegment, timeout, procedure));
    return this;
  }

  private CompositeProcedure traverseAndCreate(String[] segments) {
    int i;
    CompositeProcedure parent = root;
    for (i = 0; i < segments.length - 1; i++) {
      Procedure c = parent.get(segments[i]);
      if (c == null) {
        DefaultCompositeProcedure composite = new DefaultCompositeProcedure();
        parent.add(segments[i], composite);
        parent = composite;
      } else if (c instanceof CompositeProcedure) {
        parent = (CompositeProcedure) c;
      } else {
        // Illegal.
        throw new IllegalArgumentException("Unable to find the procedure `" + segments[i] + "`, `"
          + segments[i] + "` is not a composite.");
      }
    }

    return parent;
  }

  @Override
  public HealthChecks unregister(String name) {
    Objects.requireNonNull(name);
    if (name.isEmpty()) {
      throw new IllegalArgumentException("The name must not be empty");
    }

    String[] segments = name.split("/");
    CompositeProcedure parent = findLastParent(segments);
    if (parent != null) {
      String lastSegment = segments[segments.length - 1];
      parent.remove(lastSegment);
    }
    return this;
  }

  @Override
  public HealthChecks invoke(Handler<JsonObject> resultHandler) {
    checkStatus().onComplete(ar -> {
      if (ar.succeeded()) {
        resultHandler.handle(ar.result().toJson());
      } else {
        resultHandler.handle(null);
      }
    });
    return this;
  }

  @Override
  public Future<JsonObject> invoke(String name) {
    return checkStatus(name).map(CheckResult::toJson);
  }

  @Override
  public Future<CheckResult> checkStatus() {
    Promise<CheckResult> promise = ((ContextInternal)vertx.getOrCreateContext()).promise();
    checkStatus(promise);
    return promise.future();
  }

  public void checkStatus(Promise<CheckResult> resultHandler) {
    Promise<CheckResult> promise = ((ContextInternal)vertx.getOrCreateContext()).promise(resultHandler);
    compute(root, promise);
  }

  @Override
  public Future<CheckResult> checkStatus(String name) {
    ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    Promise<CheckResult> promise = ctx.promise();
    if (name == null || name.isEmpty() || name.equals("/")) {
      checkStatus(promise);
    } else {
      String[] segments = name.split("/");
      Procedure check = root;
      for (String segment : segments) {
        if (segment.trim().isEmpty()) {
          continue;
        }
        if (check instanceof CompositeProcedure) {
          check = ((CompositeProcedure) check).get(segment);
          if (check == null) {
            // Not found
            return ctx.failedFuture("Not found");
          }
          // Else continue...
        } else {
          // Not a composite
          return ctx.failedFuture("'" + segment + "' is not a composite");
        }
      }

      if (check == null) {
        // ????
        promise.handle(null);
        return ctx.succeededFuture();
      }
      compute(check, promise);
    }
    return promise.future();
  }

  private CompositeProcedure findLastParent(String[] segments) {
    int i;
    CompositeProcedure parent = root;
    for (i = 0; i < segments.length - 1; i++) {
      Procedure c = parent.get(segments[i]);
      if (c instanceof CompositeProcedure) {
        parent = (CompositeProcedure) c;
      } else {
        return null;
      }
    }
    return parent;
  }

  private void compute(Procedure procedure, Promise<CheckResult> resultHandler) {
    procedure.check(resultHandler::complete);
  }
}
