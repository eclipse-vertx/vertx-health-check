package io.vertx.ext.healthchecks;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.ext.healthchecks.Status}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.ext.healthchecks.Status} original class using Vert.x codegen.
 */
public class StatusConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Status obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "ok":
          if (member.getValue() instanceof Boolean) {
            obj.setOk((Boolean)member.getValue());
          }
          break;
        case "data":
          if (member.getValue() instanceof JsonObject) {
            obj.setData(((JsonObject)member.getValue()).copy());
          }
          break;
        case "procedureInError":
          if (member.getValue() instanceof Boolean) {
            obj.setProcedureInError((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(Status obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(Status obj, java.util.Map<String, Object> json) {
    json.put("ok", obj.isOk());
    if (obj.getData() != null) {
      json.put("data", obj.getData());
    }
    json.put("procedureInError", obj.isProcedureInError());
  }
}
