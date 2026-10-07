package io.vertx.ext.healthchecks.collector;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.ext.healthchecks.Status;

public interface HealthProcedures {

  static Handler<Promise<Status>> checkPromiseSucceeded(Promise<?> promiseToCheck) {
    final var futureToCheck = promiseToCheck.future();
    return promise -> {
      var status = futureToCheck.isComplete() ? Status.OK() : Status.KO();
      promise.complete(status);
    };
  }

  static Handler<Promise<Status>> checkEventLoopFunctional() {
    return promise -> promise.complete(Status.OK());
  }

}
