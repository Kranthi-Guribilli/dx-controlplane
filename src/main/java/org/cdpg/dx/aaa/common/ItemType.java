package org.cdpg.dx.aaa.common;

import static org.cdpg.dx.aaa.common.Constants.*;

import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum ItemType {
  AI_MODEL(ITEM_TYPE_AI_MODEL, ITEM_TYPE_IUDX_AI_MODEL),
  DATA_BANK(ITEM_TYPE_DATA_BANK, ITEM_TYPE_IUDX_DATA_BANK),
  APPS(ITEM_TYPE_APPS, ITEM_TYPE_IUDX_APPS);

  private final String typeValue;
  private final String iudxTypeValue;

  ItemType(String typeValue, String iudxTypeValue) {
    this.typeValue = typeValue;
    this.iudxTypeValue = iudxTypeValue;
  }

  public static ItemType fromTypeValue(String value) {
    for (ItemType type : ItemType.values()) {
      if (type.matches(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("No matching ItemType for: " + value);
  }

  /**
   * Adds the other-prefix variant of every platform item type in {@code values}, so a filter on
   * adex:DataBank also matches iudx:DataBank and vice versa. Other values pass through unchanged.
   */
  public static List<Object> withAliases(List<?> values) {
    Set<Object> expanded = new LinkedHashSet<>();
    for (Object value : values) {
      expanded.add(value);
      for (ItemType type : ItemType.values()) {
        if (value != null && type.matches(value.toString())) {
          expanded.add(type.typeValue);
          expanded.add(type.iudxTypeValue);
        }
      }
    }
    return new ArrayList<>(expanded);
  }

  /** Applies {@link #withAliases} to the list-valued filter under {@code key}, if present. */
  public static void expandFilterAliases(Map<String, Object> filters, String key) {
    filters.computeIfPresent(key, (k, v) -> v instanceof List<?> types ? withAliases(types) : v);
  }

  /**
   * Folds per-type counts keyed by iudx: types into their adex: keys, so each platform type is
   * reported once regardless of prefix.
   */
  public static JsonObject mergeAliasCounts(JsonObject counts) {
    JsonObject merged = new JsonObject();
    for (String key : counts.fieldNames()) {
      Object value = counts.getValue(key);
      String mergedKey = key;
      for (ItemType type : ItemType.values()) {
        if (type.matches(key)) {
          mergedKey = type.typeValue;
        }
      }
      if (value instanceof Number count && merged.getValue(mergedKey) instanceof Number existing) {
        merged.put(mergedKey, existing.longValue() + count.longValue());
      } else {
        merged.put(mergedKey, value);
      }
    }
    return merged;
  }

  public boolean matches(String value) {
    return typeValue.equalsIgnoreCase(value) || iudxTypeValue.equalsIgnoreCase(value);
  }

  public String getTypeValue() {
    return typeValue;
  }
}
