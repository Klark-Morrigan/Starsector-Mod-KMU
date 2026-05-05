package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.conditions.ui.picker.render.spec.KmuLabelSpec;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;

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
        int totalLines = locationLineCount + summaryLineCount;
        float sectionGap = (locationLineCount > 0 && summaryLineCount > 0) ? SECTION_PAD : 0f;
        float height = Math.max(ICON_SIZE, totalLines * LINE_HEIGHT + sectionGap);
        return topPad + height;
    }

    static KmuConditionPickerInfoRow render(
            CustomPanelAPI panel,
            List<KmuLabelSpec> locationSpecs,
            List<KmuLabelSpec> summarySpecs,
            Optional<KmuPickerFaction> faction,
            float width) {
        Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(locationSpecs, "locationSpecs");
        Objects.requireNonNull(summarySpecs, "summarySpecs");
        Objects.requireNonNull(faction, "faction");

        int totalLines = locationSpecs.size() + summarySpecs.size();
        float sectionGap = (!locationSpecs.isEmpty() && !summarySpecs.isEmpty()) ? SECTION_PAD : 0f;
        float panelHeight = Math.max(ICON_SIZE, totalLines * LINE_HEIGHT + sectionGap);
        CustomPanelAPI row = panel.createCustomPanel(width, panelHeight, new BaseCustomUIPanelPlugin());

        Optional<String> crestSprite = faction.flatMap(KmuPickerFaction::getCrestSprite);
        float textX = crestSprite.isPresent() ? renderIcon(row, crestSprite.get()) : 0f;
        float textWidth = width - textX;

        float y = 0f;
        for (KmuLabelSpec spec : locationSpecs) {
            renderText(row, spec, textX, y, textWidth);
            y += LINE_HEIGHT;
        }

        if (!locationSpecs.isEmpty() && !summarySpecs.isEmpty()) {
            y += SECTION_PAD;
        }

        // Render all summary lines; the last one is the live counts label.
        LabelAPI conditionsLabel = null;
        for (KmuLabelSpec spec : summarySpecs) {
            conditionsLabel = renderText(row, spec, textX, y, textWidth);
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

    private static float renderIcon(CustomPanelAPI row, String sprite) {
        TooltipMakerAPI iconEl = row.createUIElement(ICON_SIZE, ICON_SIZE, false);
        iconEl.addImage(sprite, ICON_SIZE, ICON_SIZE, 0f);
        row.addUIElement(iconEl).inTL(0f, 0f);
        return ICON_SIZE + ICON_PAD;
    }

    private static LabelAPI renderText(
            CustomPanelAPI row, KmuLabelSpec spec, float x, float y, float width) {
        TooltipMakerAPI textEl = row.createUIElement(width, LINE_HEIGHT, false);
        LabelAPI label = textEl.addPara(
                spec.getText(),
                StarsectorUiColorProvider.get(StarsectorUiColor.TEXT_WHITE),
                0f);
        spec.applyTo(label);
        row.addUIElement(textEl).inTL(x, y);
        return label;
    }
}
