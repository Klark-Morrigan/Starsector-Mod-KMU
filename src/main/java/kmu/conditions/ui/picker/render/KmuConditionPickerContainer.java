package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import kmu.KmuStrings;
import kmu.conditions.ui.picker.action.KmuConditionPickerAction;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class KmuConditionPickerContainer {
    public static final int DEFAULT_SQUARE_ICON_COLUMNS = 12;

    private static final float ENTRY_PAD = 8f;
    static final float GRID_SCROLLBAR_RIGHT_PAD = 32f;
    private static final float SQUARE_ICON_ROW_UNIT =
            KmuConditionIconButton.Sizing.squareButtonWidth() + KmuConditionIconGrid.CELL_GAP;

    private final CustomPanelAPI panel;
    private final KmuConditionIconGrid grid;

    public KmuConditionPickerContainer(CustomPanelAPI panel) {
        this(panel, new KmuConditionIconGrid());
    }

    KmuConditionPickerContainer(CustomPanelAPI panel, KmuConditionIconGrid grid) {
        this.panel = Objects.requireNonNull(panel, "panel");
        this.grid = Objects.requireNonNull(grid, "grid");
    }

    public KmuConditionPickerRenderResult render(
            TooltipMakerAPI body,
            KmuConditionPickerModel model,
            Consumer<KmuConditionPickerAction> actionConsumer,
            float width) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(actionConsumer, "actionConsumer");

        KmuLabelSpec locationSpec = locationLabelSpec(model);
        if (!locationSpec.getText().isEmpty()) {
            LabelAPI locationLabel = body.addPara(
                    locationSpec.getText(),
                    StarsectorUiColorProvider.get(StarsectorUiColor.WHITE),
                    ENTRY_PAD);
            locationSpec.applyTo(locationLabel);
        }

        KmuLabelSpec summarySpec = summaryLabelSpec(model);
        LabelAPI summaryLabel = body.addPara(
                summarySpec.getText(),
                StarsectorUiColorProvider.get(StarsectorUiColor.GRAY),
                ENTRY_PAD);
        summarySpec.applyTo(summaryLabel);

        // Defensive empty state: the opener should usually avoid empty pickers,
        // but if no entries are renderable, show localized UI copy and skip the grid.
        if (model.isEmpty()) {
            body.addPara(
                    KmuStrings.get(KmuStrings.CONDITION_PICKER_EMPTY),
                    ENTRY_PAD,
                    StarsectorUiColorProvider.get(StarsectorUiColor.GRAY));
            return new KmuConditionPickerRenderResult(summaryLabel, Collections.emptyList(), null);
        }

        KmuConditionIconGrid.GridHandle gridHandle = grid.addTo(
                panel,
                body,
                model,
                gridWidth(width),
                ENTRY_PAD,
                actionConsumer);
        return new KmuConditionPickerRenderResult(summaryLabel, gridHandle.getComponents(), gridHandle);
    }

    static float gridWidth(float containerWidth) {
        float availableWidth = Math.max(1f, containerWidth - GRID_SCROLLBAR_RIGHT_PAD);
        float squareButtonWidth = KmuConditionIconButton.Sizing.squareButtonWidth();
        if (availableWidth < squareButtonWidth) {
            return availableWidth;
        }

        float units = (float) Math.floor((availableWidth + KmuConditionIconGrid.CELL_GAP) / SQUARE_ICON_ROW_UNIT);
        return Math.max(squareButtonWidth, units * SQUARE_ICON_ROW_UNIT - KmuConditionIconGrid.CELL_GAP);
    }

    public static float defaultContainerWidth() {
        return containerWidthForSquareIconColumns(DEFAULT_SQUARE_ICON_COLUMNS);
    }

    public static float containerWidthForSquareIconColumns(int squareIconColumns) {
        return gridWidthForSquareIconColumns(squareIconColumns) + GRID_SCROLLBAR_RIGHT_PAD;
    }

    static float gridWidthForSquareIconColumns(int squareIconColumns) {
        int safeColumns = Math.max(1, squareIconColumns);
        return safeColumns * KmuConditionIconButton.Sizing.squareButtonWidth()
                + (safeColumns - 1) * KmuConditionIconGrid.CELL_GAP;
    }

    /**
     * Builds the summary label spec.
     *
     * The base label color is white; grey items must be highlighted explicitly:
     * - "Conditions:" in white
     * - "N visible"   in green      (omitted when zero)
     * - "N suppressed" in red       (omitted when zero)
     * - "N present"   in white      (omitted when zero)
     * - "N hidden"    in light blue (omitted when zero)
     * - "N available, N total." in grey (overrides white base)
     *
     * Separator rules:
     * - " - " separates categories; ", " separates items within the same section.
     * - The grey tail (" - N available, N total.") is always appended last.
     */
    public static KmuLabelSpec summaryLabelSpec(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        int visible = model.getVisibleCount();
        int suppressed = model.getSuppressedCount();
        int present = model.getPresentCount();
        int hidden = model.getHiddenCount();
        int available = model.getAvailableCount();
        int total = model.getEntryCount();

        Color white = StarsectorUiColorProvider.get(StarsectorUiColor.WHITE);
        Color green = StarsectorUiColorProvider.get(StarsectorUiColor.GREEN);
        Color red = StarsectorUiColorProvider.get(StarsectorUiColor.RED);
        Color lightBlue = StarsectorUiColorProvider.get(StarsectorUiColor.LIGHT_BLUE);

        String prefix = KmuStrings.get(KmuStrings.CONDITION_PICKER_SUMMARY);
        StringBuilder sb = new StringBuilder(prefix);
        List<String> highlightList = new ArrayList<>();
        List<Color> colorList = new ArrayList<>();

        addHighlight(highlightList, colorList, prefix, white);

        String visibleToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_VISIBLE, visible);
        String suppressedToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_SUPPRESSED, suppressed);
        String presentToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_PRESENT, present);
        String hiddenToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_HIDDEN, hidden);
        String availableToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_AVAILABLE, available);
        String totalToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_TOTAL, total);

        boolean hasSegment = false;

        if (visible > 0)
            hasSegment = appendSummaryToken(sb, highlightList, colorList, hasSegment, " ", visibleToken, green);
        if (suppressed > 0)
            hasSegment = appendSummaryToken(sb, highlightList, colorList, hasSegment, " - ", suppressedToken, red);
        if (present > 0)
            hasSegment = appendSummaryToken(sb, highlightList, colorList, hasSegment, ", ", presentToken, white);
        if (hidden > 0)
            hasSegment = appendSummaryToken(sb, highlightList, colorList, hasSegment, ", ", hiddenToken, lightBlue);

        // The grey tail includes the category divider so the hyphen is grey too.
        Color grey = StarsectorUiColorProvider.get(StarsectorUiColor.GRAY);
        String greyAvailableToken = (hasSegment ? " - " : "") + availableToken;

        sb.append(hasSegment ? " - " : " ").append(availableToken).append(", ").append(totalToken);

        addHighlight(highlightList, colorList, greyAvailableToken, grey);
        addHighlight(highlightList, colorList, ", " + totalToken, grey);

        return new KmuLabelSpec(
                sb.toString(),
                highlightList.toArray(new String[0]),
                colorList.toArray(new Color[0]));
    }

    /**
     * Builds the location label spec: the formatted location line together with
     * highlights for named entities (planet, faction, relationship, system, constellation).
     * Text segments and their highlight entries are accumulated in a single pass so that
     * any change to the text format is immediately reflected in what gets highlighted.
     */
    public static KmuLabelSpec locationLabelSpec(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        KmuConditionPickerLocation location = model.getLocation();
        Color highlightColor = StarsectorUiColorProvider.get(StarsectorUiColor.GOLD);
        Color defaultTextColor = StarsectorUiColorProvider.get(StarsectorUiColor.WHITE);

        List<String> segments = new ArrayList<>();
        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();

        if (location.getPlanetName().isPresent()) {
            optionalWithParenthetical(location.getPlanetName(), location.getPlanetType())
                    .ifPresent(segments::add);
            addHighlight(highlights, colors, location.getPlanetName().get(), highlightColor);
        }

        if (location.getFactionName().isPresent()) {
            ownershipSegment(location).ifPresent(segments::add);
            addHighlight(highlights, colors, location.getFactionName().get(),
                    location.getFactionColor().orElse(defaultTextColor));
            if (location.getRelationshipDescription().isPresent()) {
                addHighlight(highlights, colors, location.getRelationshipDescription().get(),
                        location.getRelationshipColor().orElse(defaultTextColor));
            }
        }

        if (location.getStarSystemName().isPresent()) {
            optionalWithParenthetical(location.getStarSystemName(), systemParenthetical(location))
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

    /** Convenience accessor for callers that only need the summary text string. */
    public static String summaryText(KmuConditionPickerModel model) {
        return summaryLabelSpec(model).getText();
    }

    /** Convenience accessor for callers that only need the location text string. */
    public static String locationText(KmuConditionPickerModel model) {
        return locationLabelSpec(model).getText();
    }

    private static void addHighlight(List<String> highlights, List<Color> colors, String token, Color color) {
        highlights.add(token);
        colors.add(color);
    }

    private static boolean appendSummaryToken(
            StringBuilder sb,
            List<String> highlights,
            List<Color> colors,
            boolean hasSegment,
            String separator,
            String token,
            Color color) {
        sb.append(hasSegment ? separator : " ").append(token);
        addHighlight(highlights, colors, token, color);
        return true;
    }

    private static Optional<String> ownershipSegment(KmuConditionPickerLocation location) {
        if (!location.getFactionName().isPresent()) {
            return Optional.empty();
        }

        String text = "owned by " + location.getFactionName().get();
        if (location.getRelationshipDescription().isPresent()) {
            text += " (" + location.getRelationshipDescription().get() + ")";
        }
        return Optional.of(text);
    }

    private static Optional<String> systemParenthetical(KmuConditionPickerLocation location) {
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

    private static Optional<String> optionalWithParenthetical(
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
}
