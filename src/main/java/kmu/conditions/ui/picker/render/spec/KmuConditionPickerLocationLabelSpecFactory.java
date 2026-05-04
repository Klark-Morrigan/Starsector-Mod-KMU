package kmu.conditions.ui.picker.render.spec;

import kmu.KmuStrings;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Produces the location label spec: the formatted location line together with
 * highlights for named entities (planet, faction, relationship, system,
 * constellation). Text segments and their highlight entries are accumulated
 * in a single pass so that any change to the text format is immediately
 * reflected in what gets highlighted.
 */
public final class KmuConditionPickerLocationLabelSpecFactory {
    private KmuConditionPickerLocationLabelSpecFactory() {
    }

    public static KmuLabelSpec get(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        KmuConditionPickerLocation location = model.getLocation();
        Color highlightColor = StarsectorUiColorProvider.get(StarsectorUiColor.GOLD);
        Color defaultTextColor = StarsectorUiColorProvider.get(StarsectorUiColor.WHITE);

        List<String> segments = new ArrayList<>();
        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();

        if (location.getPlanetName().isPresent()) {
            joinWithParenthetical(location.getPlanetName(), location.getPlanetType())
                    .ifPresent(segments::add);
            addHighlight(highlights, colors, location.getPlanetName().get(), highlightColor);
        }

        location.getFaction().ifPresent(faction -> {
            buildOwnershipSegment(faction).ifPresent(segments::add);
            addHighlight(highlights, colors, faction.getName(),
                    faction.getColor().orElse(defaultTextColor));
            faction.getRelationshipDescription().ifPresent(rel ->
                    addHighlight(highlights, colors, rel,
                            faction.getRelationshipColor().orElse(defaultTextColor)));
        });

        if (location.getStarSystemName().isPresent()) {
            joinWithParenthetical(location.getStarSystemName(), buildSystemParenthetical(location))
                    .ifPresent(segments::add);
            addHighlight(highlights, colors, location.getStarSystemName().get(), highlightColor);
            location.getGravityWellName()
                    .filter(name -> !name.equals(location.getGravityWellTypeName().orElse(null)))
                    .filter(name -> !location.getStarSystemName().map(s -> s.contains(name)).orElse(false))
                    .ifPresent(name -> addHighlight(highlights, colors, name, highlightColor));
        }

        location.getConstellationName().ifPresent(name -> {
            segments.add(name);
            addHighlight(highlights, colors, name, highlightColor);
        });

        if (segments.isEmpty()) {
            return new KmuLabelSpec("", new String[0], new Color[0]);
        }

        String prefix = KmuStrings.get(KmuStrings.CONDITION_PICKER_LOCATION);
        String text = prefix + " " + String.join(" - ", segments) + ".";
        return new KmuLabelSpec(
                text,
                highlights.toArray(new String[0]),
                colors.toArray(new Color[0]));
    }

    public static String getText(KmuConditionPickerModel model) {
        return get(model).getText();
    }

    private static Optional<String> buildOwnershipSegment(KmuPickerFaction faction) {
        String text = "owned by " + faction.getName();
        return Optional.of(faction.getRelationshipDescription()
                .map(rel -> text + " (" + rel + ")")
                .orElse(text));
    }

    private static Optional<String> buildSystemParenthetical(KmuConditionPickerLocation location) {
        List<String> parts = new ArrayList<>();
        // Suppress entity name when:
        // - it duplicates the type label (e.g. barycenter: both fields resolve to the same string)
        // - it is already implied by the system name (e.g. "Agreus" in "Agreus System")
        // Binary-star planets orbit a named individual star ("Kumari A") whose name is NOT
        // contained in "Kumari System", so it is preserved in that case.
        location.getGravityWellName()
                .filter(name -> !name.equals(location.getGravityWellTypeName().orElse(null)))
                .filter(name -> !location.getStarSystemName().map(s -> s.contains(name)).orElse(false))
                .ifPresent(parts::add);
        location.getGravityWellTypeName().ifPresent(parts::add);
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", parts));
    }

    private static Optional<String> joinWithParenthetical(
            Optional<String> main,
            Optional<String> parenthetical) {
        if (!main.isPresent() && !parenthetical.isPresent()) {
            return Optional.empty();
        }
        if (!main.isPresent()) {
            return parenthetical;
        }
        if (!parenthetical.isPresent()) {
            return main;
        }
        return Optional.of(main.get() + " (" + parenthetical.get() + ")");
    }

    private static void addHighlight(List<String> highlights, List<Color> colors, String token, Color color) {
        highlights.add(token);
        colors.add(color);
    }
}
