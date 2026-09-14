package kmu.conditions.ui.picker.model;

import java.awt.Color;
import java.util.Objects;
import java.util.Optional;

import static kmu.util.KmuValues.convertToOptionalText;

/**
 * Display-only snapshot of how a controlling faction appears to the player
 * when they open the MCM. All fields come from one {@code FactionAPI} +
 * relationship formatter call and are always either all-present or all-absent,
 * so they are represented as a unit rather than parallel Optionals on the
 * location model.
 *
 * <p>{@code name} is required - it is the condition for creating this object.
 * All other fields may be absent when the Starsector API cannot provide them.</p>
 */
public final class KmuPickerFaction {
    private final String name;
    private final Color colour;
    private final String crestSprite;
    private final String relationshipDescription;
    private final Color relationshipColour;

    public KmuPickerFaction(
            String name,
            Color colour,
            String crestSprite,
            String relationshipDescription,
            Color relationshipColour) {
        this.name = Objects.requireNonNull(name, "name");
        this.colour = colour;
        this.crestSprite = crestSprite;
        this.relationshipDescription = relationshipDescription;
        this.relationshipColour = relationshipColour;
    }

    public String getName() {
        return name;
    }

    public Optional<Color> getColour() {
        return Optional.ofNullable(colour);
    }

    public Optional<String> getCrestSprite() {
        return convertToOptionalText(crestSprite);
    }

    public Optional<String> getRelationshipDescription() {
        return convertToOptionalText(relationshipDescription);
    }

    public Optional<Color> getRelationshipColour() {
        return Optional.ofNullable(relationshipColour);
    }
}
