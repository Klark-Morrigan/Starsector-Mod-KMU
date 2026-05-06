package kmu.conditions.ui.picker.model;

import java.awt.Color;
import java.util.Objects;
import java.util.Optional;

import static kmu.util.KmuValues.convertToOptionalText;

/**
 * Display-only snapshot of how a controlling faction appears to the player
 * when they open the PCP. All fields come from one {@code FactionAPI} +
 * relationship formatter call and are always either all-present or all-absent,
 * so they are represented as a unit rather than parallel Optionals on the
 * location model.
 *
 * <p>{@code name} is required - it is the condition for creating this object.
 * All other fields may be absent when the Starsector API cannot provide them.</p>
 */
public final class KmuPickerFaction {
    private final String name;
    private final Color color;
    private final String crestSprite;
    private final String relationshipDescription;
    private final Color relationshipColor;

    public KmuPickerFaction(
            String name,
            Color color,
            String crestSprite,
            String relationshipDescription,
            Color relationshipColor) {
        this.name = Objects.requireNonNull(name, "name");
        this.color = color;
        this.crestSprite = crestSprite;
        this.relationshipDescription = relationshipDescription;
        this.relationshipColor = relationshipColor;
    }

    public String getName() {
        return name;
    }

    public Optional<Color> getColor() {
        return Optional.ofNullable(color);
    }

    public Optional<String> getCrestSprite() {
        return convertToOptionalText(crestSprite);
    }

    public Optional<String> getRelationshipDescription() {
        return convertToOptionalText(relationshipDescription);
    }

    public Optional<Color> getRelationshipColor() {
        return Optional.ofNullable(relationshipColor);
    }
}
