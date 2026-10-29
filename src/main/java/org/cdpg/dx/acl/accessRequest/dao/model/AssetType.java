package org.cdpg.dx.acl.accessRequest.dao.model;

/*Do not allow potential applications as they dont have ownerUserId*/
public enum AssetType {
  DATA_BANK("adex:DataBank"),
  AI_MODEL("adex:AiModel"),
  // IUDX v1 catalogue aliases, kept distinct so the type is stored as sent
  IUDX_DATA_BANK("iudx:DataBank"),
  IUDX_AI_MODEL("iudx:AiModel");
  private final String assetType;

  AssetType(String assetType) {
    this.assetType = assetType;
  }

  public static AssetType fromString(String assetType) {
    for (AssetType assetTypeEnum : values()) {
      if (assetTypeEnum.getAssetType().equalsIgnoreCase(assetType)) {
        return assetTypeEnum;
      }
    }
    throw new IllegalArgumentException("Invalid assetType: " + assetType);
  }

  public String getAssetType() {
    return assetType;
  }

  public boolean isAiModel() {
    return this == AI_MODEL || this == IUDX_AI_MODEL;
  }
}
