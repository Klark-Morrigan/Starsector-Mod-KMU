package kmu.conditions.ui.picker.model;

import java.awt.Color;
import java.util.Optional;

import static kmu.KmuValues.convertToOptionalText;

public final class KmuConditionPickerLocation {
    private final Optional<String> planetName;
    private final Optional<String> planetType;
    private final Optional<String> factionName;
    private final Optional<Color> factionColor;
    private final Optional<String> relationshipDescription;
    private final Optional<Color> relationshipColor;
    private final Optional<String> starSystemName;
    private final Optional<String> gravityWellName;
    private final Optional<String> constellationName;

    public KmuConditionPickerLocation(
            String planetName,
            String planetType,
            String factionName,
            Color factionColor,
            String relationshipDescription,
            Color relationshipColor,
            String starSystemName,
            String gravityWellName,
            String constellationName) {
        this.planetName = convertToOptionalText(planetName);
        this.planetType = convertToOptionalText(planetType);
        this.factionName = convertToOptionalText(factionName);
        this.factionColor = Optional.ofNullable(factionColor);
        this.relationshipDescription = convertToOptionalText(relationshipDescription);
        this.relationshipColor = Optional.ofNullable(relationshipColor);
        this.starSystemName = convertToOptionalText(starSystemName);
        this.gravityWellName = convertToOptionalText(gravityWellName);
        this.constellationName = convertToOptionalText(constellationName);
    }

    public Optional<String> getPlanetName() {
        return planetName;
    }

    public Optional<String> getPlanetType() {
        return planetType;
    }

    public Optional<String> getFactionName() {
        return factionName;
    }

    public Optional<Color> getFactionColor() {
        return factionColor;
    }

    public Optional<String> getRelationshipDescription() {
        return relationshipDescription;
    }

    public Optional<Color> getRelationshipColor() {
        return relationshipColor;
    }

    public Optional<String> getStarSystemName() {
        return starSystemName;
    }

    public Optional<String> getGravityWellName() {
        return gravityWellName;
    }

    public Optional<String> getConstellationName() {
        return constellationName;
    }
}
