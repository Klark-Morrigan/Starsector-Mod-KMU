package kmu.conditions.ui.picker.model;

import java.util.Optional;

import static kmu.KmuValues.convertToOptionalText;

public final class KmuConditionPickerLocation {
    private final Optional<String> planetName;
    private final Optional<String> planetType;
    private final Optional<KmuPickerFaction> faction;
    private final Optional<String> starSystemName;
    private final Optional<String> gravityWellTypeName;
    private final Optional<String> gravityWellName;
    private final Optional<String> constellationName;

    public KmuConditionPickerLocation(
            String planetName,
            String planetType,
            KmuPickerFaction faction,
            String starSystemName,
            String gravityWellTypeName,
            String gravityWellName,
            String constellationName) {
        this.planetName = convertToOptionalText(planetName);
        this.planetType = convertToOptionalText(planetType);
        this.faction = Optional.ofNullable(faction);
        this.starSystemName = convertToOptionalText(starSystemName);
        this.gravityWellTypeName = convertToOptionalText(gravityWellTypeName);
        this.gravityWellName = convertToOptionalText(gravityWellName);
        this.constellationName = convertToOptionalText(constellationName);
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
}
