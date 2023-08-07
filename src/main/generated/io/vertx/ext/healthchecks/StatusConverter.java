package io.vertx.ext.healthchecks;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.impl.JsonUtil;
import java.util.Base64;
import java.util.Map;

import static java.util.Map.Entry;

/**
 * Converter and mapper for {@link io.vertx.ext.healthchecks.Status}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.ext.healthchecks.Status} original class using Vert.x codegen.
 */
public class StatusConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

  public static void fromJson(Iterable<Entry<String, Object>> json, Status obj) {
    for (Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "data":
          if (member.getValue() instanceof JsonObject) {
            obj.setData(((JsonObject)member.getValue()).copy());
          }
          break;
        case "ok":
          if (member.getValue() instanceof Boolean) {
            obj.setOk((Boolean)member.getValue());
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

  public static void toJson(Status obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(Status obj, Map<String, Object> json) {
    if (obj.getData() != null) {
      json.put("data", obj.getData());
    }
    json.put("ok", obj.isOk());
    json.put("procedureInError", obj.isProcedureInError());
  }
}
