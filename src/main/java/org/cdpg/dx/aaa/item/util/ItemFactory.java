package org.cdpg.dx.aaa.item.util;

import static org.cdpg.dx.aaa.common.Constants.*;

import io.vertx.core.json.JsonObject;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.item.model.AiModelItem;
import org.cdpg.dx.aaa.item.model.AppsItem;
import org.cdpg.dx.aaa.item.model.DataBankItem;
import org.cdpg.dx.aaa.item.model.Item;

public class ItemFactory {
  private static final Logger LOGGER = LogManager.getLogger(ItemFactory.class);

  public static Item parse(JsonObject body) {
    List<String> typeList = body.getJsonArray(TYPE).getList();

    if (typeList == null || typeList.isEmpty()) {
      throw new IllegalArgumentException("Missing 'type' field");
    }

    String type = typeList.getFirst();

    if (DATA_BANK_TYPES.contains(type)) {
      return DataBankItem.fromJson(body);
    } else if (APPS_TYPES.contains(type)) {
      return AppsItem.fromJson(body);
    } else if (AI_MODEL_TYPES.contains(type)) {
      return AiModelItem.fromJson(body);
    }
    throw new IllegalArgumentException("Unsupported type: " + type);
  }

  public static Item from(JsonObject json) {
    List<String> types = json.getJsonArray(TYPE).getList();
    if (types.stream().anyMatch(AI_MODEL_TYPES::contains)) {
      return AiModelItem.fromJson(json);
    } else if (types.stream().anyMatch(APPS_TYPES::contains)) {
      return AppsItem.fromJson(json);
    } else if (types.stream().anyMatch(DATA_BANK_TYPES::contains)) {
      return DataBankItem.fromJson(json);
    }
    throw new IllegalArgumentException("Unknown item type: " + types);
  }
}
