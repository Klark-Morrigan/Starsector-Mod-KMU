package kmu.ui.chooser.model;

import java.util.Objects;

public final class KmuConditionChooserLocation {
    private final String marketName;
    private final String starSystemName;
    private final String constellationName;

    public KmuConditionChooserLocation(
            String marketName,
            String starSystemName,
            String constellationName) {
        this.marketName = requireNonBlank(marketName, "marketName");
        this.starSystemName = requireNonBlank(starSystemName, "starSystemName");
        this.constellationName = requireNonBlank(constellationName, "constellationName");
    }

    public static KmuConditionChooserLocation unknown() {
        return new KmuConditionChooserLocation(
                "Unknown market",
                "Unknown star system",
                "Unknown constellation");
    }

    public String getMarketName() {
        return marketName;
    }

    public String getStarSystemName() {
        return starSystemName;
    }

    public String getConstellationName() {
        return constellationName;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
