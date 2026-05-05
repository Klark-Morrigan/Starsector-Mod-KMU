package kmu.conditions.ui.picker.render.spec;

import kmu.util.KmuLocalisation;
import static kmu.util.KmuTextFormats.joinWithParenthetical;
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
 * Produces the location label specs: one {@link KmuLabelSpec} per displayable
 * location segment, returned as an ordered list for stacked rendering.
 *
 * <p>Line order when location data is present:
 * <ol>
 *   <li>"Location:" header</li>
 *   <li>Planet name (+ type) and ownership/relationship, if present</li>
 *   <li>Star system (+ gravity-well info), if present</li>
 *   <li>Constellation name, if present</li>
 * </ol>
 *
 * <p>Returns an empty list when the location has no displayable fields.
 */
public final class KmuConditionPickerLocationLabelSpecFactory {
    private KmuConditionPickerLocationLabelSpecFactory() {
    }

    public static List<KmuLabelSpec> get(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        KmuConditionPickerLocation location = model.getLocation();
        Color highlightColor = StarsectorUiColorProvider.get(StarsectorUiColor.GOLD);
        Color defaultTextColor = StarsectorUiColorProvider.get(StarsectorUiColor.TEXT_WHITE);

        Optional<KmuLabelSpec> planetLine = buildPlanetLine(location, highlightColor, defaultTextColor);
        Optional<KmuLabelSpec> systemLine = buildSystemLine(location, highlightColor);
        Optional<KmuLabelSpec> constellationLine = buildConstellationLine(location, highlightColor);

        List<KmuLabelSpec> result = new ArrayList<>();
        result.add(buildHeaderLine());

        if (!planetLine.isPresent() && !systemLine.isPresent() && !constellationLine.isPresent()) {
            result.add(buildUnknownLine(highlightColor));
            return result;
        }

        planetLine.ifPresent(result::add);
        systemLine.ifPresent(result::add);
        constellationLine.ifPresent(result::add);

        return result;
    }

    private static KmuLabelSpec buildHeaderLine() {
        return new KmuLabelSpec(
                KmuLocalisation.get(KmuLocalisation.CONDITION_PICKER_LOCATION),
                new String[0],
                new Color[0]);
    }

    private static KmuLabelSpec buildUnknownLine(Color highlightColor) {
        String unknown = KmuLocalisation.get(KmuLocalisation.CONDITION_PICKER_LOCATION_UNKNOWN);
        return new KmuLabelSpec(unknown, new String[]{unknown}, new Color[]{highlightColor});
    }

    private static Optional<KmuLabelSpec> buildPlanetLine(
            KmuConditionPickerLocation location, Color highlightColor, Color defaultTextColor) {
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

        if (segments.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new KmuLabelSpec(
                String.join(" - ", segments),
                highlights.toArray(new String[0]),
                colors.toArray(new Color[0])));
    }

    private static Optional<KmuLabelSpec> buildSystemLine(
            KmuConditionPickerLocation location, Color highlightColor) {
        if (!location.getStarSystemName().isPresent()) {
            return Optional.empty();
        }

        Optional<String> gravityWellName = getDisplayableGravityWellName(location);
        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();

        Optional<String> text = joinWithParenthetical(
                location.getStarSystemName(), buildSystemParenthetical(location, gravityWellName));
        addHighlight(highlights, colors, location.getStarSystemName().get(), highlightColor);
        gravityWellName.ifPresent(name -> addHighlight(highlights, colors, name, highlightColor));

        return text.map(t -> new KmuLabelSpec(
                t,
                highlights.toArray(new String[0]),
                colors.toArray(new Color[0])));
    }

    private static Optional<KmuLabelSpec> buildConstellationLine(
            KmuConditionPickerLocation location, Color highlightColor) {
        return location.getConstellationName().map(name ->
                new KmuLabelSpec(name, new String[]{name}, new Color[]{highlightColor}));
    }

    private static Optional<String> buildOwnershipSegment(KmuPickerFaction faction) {
        String text = "owned by " + faction.getName();
        return Optional.of(faction.getRelationshipDescription()
                .map(rel -> text + " (" + rel + ")")
                .orElse(text));
    }

    private static Optional<String> buildSystemParenthetical(
            KmuConditionPickerLocation location, Optional<String> gravityWellName) {
        List<String> parts = new ArrayList<>();
        gravityWellName.ifPresent(parts::add);
        location.getGravityWellTypeName().ifPresent(parts::add);
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", parts));
    }

    // Suppress entity name when it duplicates the type label (e.g. barycenter
    // fields are identical) or is already implied by the system name (e.g.
    // "Agreus" in "Agreus System"). Binary-star planets orbit a named star
    // ("Kumari A") not contained in "Kumari System", so that case is preserved.
    private static Optional<String> getDisplayableGravityWellName(
            KmuConditionPickerLocation location) {
        return location.getGravityWellName()
                .filter(name -> !name.equals(location.getGravityWellTypeName().orElse(null)))
                .filter(name -> !location.getStarSystemName().map(s -> s.contains(name)).orElse(false));
    }

    private static void addHighlight(
            List<String> highlights, List<Color> colors, String token, Color color) {
        highlights.add(token);
        colors.add(color);
    }
}
