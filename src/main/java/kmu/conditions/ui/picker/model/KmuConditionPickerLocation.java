package kmu.conditions.ui.picker.model;

import java.util.Optional;

import static kmlib.text.KmlibStrings.convertToOptionalText;

public final class KmuConditionPickerLocation {

    private final Optional<String> planetName;
    private final Optional<String> planetType;
    private final Optional<KmuPickerFaction> faction;
    private final Optional<String> starSystemName;
    private final Optional<String> gravityWellTypeName;
    private final Optional<String> gravityWellName;
    private final Optional<String> constellationName;

    private KmuConditionPickerLocation(Builder builder) {

        this.planetName = convertToOptionalText(builder.planetName);
        this.planetType = convertToOptionalText(builder.planetType);
        this.faction = Optional.ofNullable(builder.faction);
        this.starSystemName = convertToOptionalText(builder.starSystemName);
        this.gravityWellTypeName = convertToOptionalText(builder.gravityWellTypeName);
        this.gravityWellName = convertToOptionalText(builder.gravityWellName);
        this.constellationName = convertToOptionalText(builder.constellationName);
    }

    /**
     * @return a location with every part absent, for a market the picker could read nothing from
     */
    public static KmuConditionPickerLocation createUnknownLocation() {
        return new Builder().build();
    }

    public Optional<String> getPlanetName() {
        return planetName;
    }

    public Optional<String> getPlanetType() {
        return planetType;
    }

    public Optional<KmuPickerFaction> getFaction() {
        return faction;
    }

    public Optional<String> getStarSystemName() {
        return starSystemName;
    }

    public Optional<String> getGravityWellTypeName() {
        return gravityWellTypeName;
    }

    public Optional<String> getGravityWellName() {
        return gravityWellName;
    }

    public Optional<String> getConstellationName() {
        return constellationName;
    }

    /**
     * Builds a location part by part.
     *
     * <p>Every part is optional and six of the seven are strings, so each is named where it is set.
     * A part left unset, null or blank is absent.
     */
    public static final class Builder {

        private String planetName;
        private String planetType;
        private KmuPickerFaction faction;
        private String starSystemName;
        private String gravityWellTypeName;
        private String gravityWellName;
        private String constellationName;

        public Builder withPlanetName(String planetName) {

            this.planetName = planetName;
            return this;
        }

        public Builder withPlanetType(String planetType) {

            this.planetType = planetType;
            return this;
        }

        public Builder withFaction(KmuPickerFaction faction) {

            this.faction = faction;
            return this;
        }

        public Builder withStarSystemName(String starSystemName) {

            this.starSystemName = starSystemName;
            return this;
        }

        public Builder withGravityWellTypeName(String gravityWellTypeName) {

            this.gravityWellTypeName = gravityWellTypeName;
            return this;
        }

        public Builder withGravityWellName(String gravityWellName) {

            this.gravityWellName = gravityWellName;
            return this;
        }

        public Builder withConstellationName(String constellationName) {

            this.constellationName = constellationName;
            return this;
        }

        public KmuConditionPickerLocation build() {
            return new KmuConditionPickerLocation(this);
        }
    }
}
