package org.cdpg.dx.aaa.common;

import static org.cdpg.dx.aaa.common.Constants.*;

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

  public boolean matches(String value) {
    return typeValue.equalsIgnoreCase(value) || iudxTypeValue.equalsIgnoreCase(value);
  }

  public String getTypeValue() {
    return typeValue;
  }
}
