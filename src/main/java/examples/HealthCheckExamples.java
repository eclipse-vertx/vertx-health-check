package examples;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@SuppressWarnings("unused")
public class HealthCheckExamples {

  public void example1(Vertx vertx) {
    HealthChecks hc = HealthChecks.create(vertx);

    hc.register(
      "my-procedure",
      promise -> promise.complete(Status.OK()));

    // Register with a timeout. The check fails if it does not complete in time.
    // The timeout is given in ms.
    hc.register(
      "my-procedure",
      2000,
      promise -> promise.complete(Status.OK()));

  }

  public void example3(HealthChecks healthChecks) {
    healthChecks.register("a-group/my-procedure-name", promise -> {
      //....
    });
    // Groups can contain other groups
    healthChecks.register("a-group/a-second-group/my-second-procedure-name", promise -> {
      //....
    });
  }

  public void example4(HealthChecks healthChecks) {
    healthChecks.register("my-procedure-name", promise -> {
      // Status can provide additional data provided as JSON
      promise.complete(Status.OK(new JsonObject().put("available-memory", "2mb")));
    });

    healthChecks.register("my-second-procedure-name", promise -> {
      promise.complete(Status.KO(new JsonObject().put("load", 99)));
    });
  }

  static class SqlConnection {
    Future<Void> close() {
      return Future.succeededFuture();
    }
  }

  static class Pool {
    Future<SqlConnection> getConnection() {
      return Future.succeededFuture();
    }
  }

  public void pool(HealthChecks healthChecks, Pool pool) {
    healthChecks.register("database", promise ->
      pool.getConnection()
        .compose(SqlConnection::close)
        .<Status>mapEmpty()
        .onComplete(promise)
    );
  }

  public void eventbus(Vertx vertx, HealthChecks healthChecks) {
    healthChecks.register("receiver", promise ->
      vertx.eventBus().request("health", "ping")
        .onSuccess(msg -> {
          promise.complete(Status.OK());
        })
        .onFailure(err -> {
          promise.complete(Status.KO());
        })
    );
  }

  public void publishOnEventBus(Vertx vertx, HealthChecks healthChecks) {
    vertx.eventBus().consumer("health", message ->
      healthChecks.checkStatus()
        .onSuccess(message::reply)
        .onFailure(err -> message.fail(0, err.getMessage()))
    );
  }
}
