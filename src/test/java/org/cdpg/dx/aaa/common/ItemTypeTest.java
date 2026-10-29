package org.cdpg.dx.aaa.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemTypeTest {

  @Test
  void fromTypeValue_acceptsBothPrefixes() {
    assertThat(ItemType.fromTypeValue("adex:DataBank")).isEqualTo(ItemType.DATA_BANK);
    assertThat(ItemType.fromTypeValue("iudx:DataBank")).isEqualTo(ItemType.DATA_BANK);
    assertThat(ItemType.fromTypeValue("iudx:AiModel")).isEqualTo(ItemType.AI_MODEL);
    assertThat(ItemType.fromTypeValue("iudx:Apps")).isEqualTo(ItemType.APPS);
  }

  @Test
  void withAliases_addsOtherPrefixAndKeepsUnknownValues() {
    assertThat(ItemType.withAliases(List.of("adex:DataBank", "iudx:Resource")))
        .containsExactly("adex:DataBank", "iudx:DataBank", "iudx:Resource");
    assertThat(ItemType.withAliases(List.of("iudx:AiModel")))
        .containsExactlyInAnyOrder("adex:AiModel", "iudx:AiModel");
  }

  @Test
  void withAliases_doesNotDuplicateWhenBothPrefixesGiven() {
    assertThat(ItemType.withAliases(List.of("adex:Apps", "iudx:Apps")))
        .containsExactly("adex:Apps", "iudx:Apps");
  }

  @Test
  void expandFilterAliases_widensListFilterOnly() {
    Map<String, Object> filters = new HashMap<>();
    filters.put("asset_type", List.of("adex:DataBank"));
    filters.put("status", List.of("PENDING"));

    ItemType.expandFilterAliases(filters, "asset_type");

    assertThat(filters.get("asset_type")).isEqualTo(List.of("adex:DataBank", "iudx:DataBank"));
    assertThat(filters.get("status")).isEqualTo(List.of("PENDING"));
  }

  @Test
  void expandFilterAliases_ignoresMissingKey() {
    Map<String, Object> filters = new HashMap<>();
    ItemType.expandFilterAliases(filters, "asset_type");
    assertThat(filters).isEmpty();
  }

  @Test
  void mergeAliasCounts_foldsIudxCountsIntoAdexKeys() {
    JsonObject counts =
        new JsonObject()
            .put("adex:DataBank", 74)
            .put("iudx:DataBank", 6)
            .put("iudx:AiModel", 3)
            .put("adex:Apps", 76);

    JsonObject merged = ItemType.mergeAliasCounts(counts);

    assertThat(merged.getLong("adex:DataBank")).isEqualTo(80L);
    assertThat(merged.getLong("adex:AiModel")).isEqualTo(3L);
    assertThat(merged.getLong("adex:Apps")).isEqualTo(76L);
    assertThat(merged.fieldNames()).doesNotContain("iudx:DataBank", "iudx:AiModel");
  }
}
