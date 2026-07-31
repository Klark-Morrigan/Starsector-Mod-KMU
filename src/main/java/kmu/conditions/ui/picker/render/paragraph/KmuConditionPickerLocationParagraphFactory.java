package kmu.conditions.ui.picker.render.paragraph;

import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.highlight.Highlight;
import kmlib.starsector.ui.highlight.HighlightedParagraph;

import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.util.KmuStrings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static kmu.util.KmuTextFormats.joinWithParenthetical;

/**
 * Produces the location paragraphs: one {@link HighlightedParagraph} per
 * displayable location segment, returned as an ordered list for stacked
 * rendering.
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
public final class KmuConditionPickerLocationParagraphFactory {
    private KmuConditionPickerLocationParagraphFactory() {
    }

    public static List<HighlightedParagraph> get(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        var location = model.getLocation();
        var highlightColor = StarsectorUiColor.VANILLA_HIGHLIGHT_GOLD.resolve();
        var defaultTextColor = StarsectorUiColor.VANILLA_TEXT.resolve();

        var planetLine = buildPlanetLine(location, highlightColor, defaultTextColor);
        var systemLine = buildSystemLine(location, highlightColor);
        var constellationLine = buildConstellationLine(location, highlightColor);

        var result = new ArrayList<HighlightedParagraph>();
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

    private static HighlightedParagraph buildHeaderLine() {
        return new HighlightedParagraph(
            KmuStrings.get(KmuStrings.CONDITION_MANAGER_LOCATION),
            StarsectorUiColor.VANILLA_GRAY.resolve());
    }

    private static HighlightedParagraph buildUnknownLine(Color highlightColor) {
        var unknown = KmuStrings.get(KmuStrings.CONDITION_MANAGER_LOCATION_UNKNOWN);
        return new HighlightedParagraph(
            unknown,
            new Highlight(unknown, highlightColor));
    }

    private static Optional<HighlightedParagraph> buildPlanetLine(
            KmuConditionPickerLocation location,
            Color highlightColor,
            Color defaultTextColor) {

        var segments = new ArrayList<String>();
        var highlights = new ArrayList<Highlight>();

        if (location.getPlanetName().isPresent()) {

            joinWithParenthetical(location.getPlanetName(), location.getPlanetType())
                .ifPresent(segments::add);

            highlights.add(new Highlight(
                location.getPlanetName().get(),
                highlightColor));
        }

        location.getFaction().ifPresent(faction -> {

            buildOwnershipSegment(faction).ifPresent(segments::add);

            highlights.add(new Highlight(
                faction.getName(),
                faction.getColor().orElse(defaultTextColor)));

            faction.getRelationshipDescription().ifPresent(rel ->
                highlights.add(new Highlight(
                    rel,
                    faction.getRelationshipColor().orElse(defaultTextColor))));
        });

        if (segments.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new HighlightedParagraph(
            String.join(" - ", segments),
            highlights.toArray(new Highlight[0])));
    }

    private static Optional<HighlightedParagraph> buildSystemLine(
            KmuConditionPickerLocation location,
            Color highlightColor) {

        if (!location.getStarSystemName().isPresent()) {
            return Optional.empty();
        }

        var gravityWellName = getDisplayableGravityWellName(location);
        var highlights = new ArrayList<Highlight>();
        var text = joinWithParenthetical(
            location.getStarSystemName(),
            buildSystemParenthetical(location, gravityWellName));

        highlights.add(new Highlight(
            location.getStarSystemName().get(),
            highlightColor));

        gravityWellName.ifPresent(name -> highlights.add(new Highlight(
            name,
            highlightColor)));

        return text.map(t -> new HighlightedParagraph(
            t,
            highlights.toArray(new Highlight[0])));
    }

    private static Optional<HighlightedParagraph> buildConstellationLine(
            KmuConditionPickerLocation location,
            Color highlightColor) {

        return location.getConstellationName().map(name ->
            new HighlightedParagraph(name, new Highlight(name, highlightColor)));
    }

    private static Optional<String> buildOwnershipSegment(KmuPickerFaction faction) {
        var text = "owned by " + faction.getName();
        return Optional.of(faction.getRelationshipDescription()
            .map(rel -> text + " (" + rel + ")")
            .orElse(text));
    }

    private static Optional<String> buildSystemParenthetical(
            KmuConditionPickerLocation location,
            Optional<String> gravityWellName) {

        var parts = new ArrayList<String>();

        gravityWellName.ifPresent(parts::add);
        location.getGravityWellTypeName().ifPresent(parts::add);

        return parts.isEmpty()
            ? Optional.empty()
            : Optional.of(String.join(", ", parts));
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
}
