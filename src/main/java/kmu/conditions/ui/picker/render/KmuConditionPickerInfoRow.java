package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;

import kmlib.starsector.ui.highlight.HighlightedParagraph;

import kmu.conditions.ui.picker.model.KmuPickerFaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders the picker's info panel: an optional faction crest icon flush left,
 * with location lines and the conditions summary stacked to its right.
 * Always uses a fixed-height panel so positions stay stable regardless of
 * which elements are present.
 */
final class KmuConditionPickerInfoRow {

    /** Height of one text line, matching the approximate rendered font height. */
    static final float LINE_HEIGHT = 20f;

    /** Side length of the faction crest icon. 4x LINE_HEIGHT for visual prominence. */
    static final float ICON_SIZE = 80f;

    /** Gap between the faction icon and the text column to its right. */
    private static final float ICON_PAD = 16f;

    /** Vertical gap inserted between the location and summary sections. */
    private static final float SECTION_PAD = 8f;

    private final CustomPanelAPI rowPanel;

    /** The label for the conditions count line - updated live when entries change. */
    private final LabelAPI conditionsLabel;

    private KmuConditionPickerInfoRow(CustomPanelAPI rowPanel, LabelAPI conditionsLabel) {
        this.rowPanel = rowPanel;
        this.conditionsLabel = conditionsLabel;
    }

    /**
     * Total height of the info panel as added to the header tooltip body.
     * The icon column sets a minimum height of {@link #ICON_SIZE} to avoid
     * the crest being clipped when line count is small.
     */
    static float computeHeight(float topPad, int locationLineCount, int summaryLineCount) {
        return topPad + computePanelHeight(locationLineCount, summaryLineCount);
    }

    static KmuConditionPickerInfoRow render(
            CustomPanelAPI panel,
            List<HighlightedParagraph> locationParagraphs,
            List<HighlightedParagraph> summaryParagraphs,
            Optional<KmuPickerFaction> faction,
            float width) {

        Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(locationParagraphs, "locationParagraphs");
        Objects.requireNonNull(summaryParagraphs, "summaryParagraphs");
        Objects.requireNonNull(faction, "faction");

        var panelHeight = computePanelHeight(locationParagraphs.size(), summaryParagraphs.size());
        var row = panel.createCustomPanel(width, panelHeight, new BaseCustomUIPanelPlugin());

        var crestSprite = faction.flatMap(KmuPickerFaction::getCrestSprite);
        var textX = crestSprite.isPresent() ? renderIcon(row, crestSprite.get()) : 0f;
        var textWidth = width - textX;

        var y = 0f;
        for (var paragraph : locationParagraphs) {
            renderText(row, paragraph, textX, y, textWidth);
            y += LINE_HEIGHT;
        }

        y += computeSectionGap(locationParagraphs.size(), summaryParagraphs.size());

        // Render all summary lines; the last one is the live counts label.
        LabelAPI conditionsLabel = null;
        for (var paragraph : summaryParagraphs) {
            conditionsLabel = renderText(row, paragraph, textX, y, textWidth);
            y += LINE_HEIGHT;
        }

        return new KmuConditionPickerInfoRow(row, conditionsLabel);
    }

    CustomPanelAPI getPanel() {
        return rowPanel;
    }

    LabelAPI getConditionsLabel() {
        return conditionsLabel;
    }

    // The panel's own height, stated once for the header that reserves it and the row that fills it,
    // so the two cannot disagree about where the grid below begins.
    private static float computePanelHeight(int locationLineCount, int summaryLineCount) {

        var totalLines = locationLineCount + summaryLineCount;

        return Math.max(
            ICON_SIZE,
            totalLines * LINE_HEIGHT + computeSectionGap(locationLineCount, summaryLineCount));
    }

    // The gap between the two sections, which only stands where there are two sections to part.
    private static float computeSectionGap(int locationLineCount, int summaryLineCount) {
        return locationLineCount > 0 && summaryLineCount > 0 ? SECTION_PAD : 0f;
    }

    private static float renderIcon(CustomPanelAPI row, String sprite) {
        var iconEl = row.createUIElement(ICON_SIZE, ICON_SIZE, false);
        iconEl.addImage(sprite, ICON_SIZE, ICON_SIZE, 0f);
        row.addUIElement(iconEl).inTL(0f, 0f);
        return ICON_SIZE + ICON_PAD;
    }

    private static LabelAPI renderText(
            CustomPanelAPI row,
            HighlightedParagraph paragraph,
            float x,
            float y,
            float width) {

        var textEl = row.createUIElement(width, LINE_HEIGHT, false);
        var label = paragraph.addTo(textEl);
        row.addUIElement(textEl).inTL(x, y);
        return label;
    }
}
