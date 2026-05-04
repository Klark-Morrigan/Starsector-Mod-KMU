package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.conditions.ui.picker.render.spec.KmuLabelSpec;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;

import java.util.Objects;
import java.util.Optional;

/**
 * Renders the picker's info panel: an optional location line at the top,
 * followed by an optional faction crest icon flush left of the conditions
 * summary text. Always uses a fixed-height panel per line so positions stay
 * stable regardless of which elements are present.
 */
final class KmuConditionPickerInfoRow {
    /** Height of one text line, matching the approximate rendered font height
     *  so the icon and text sit at the same visual baseline. */
    static final float ICON_SIZE = 20f;
    /** Gap between the faction icon and the conditions text label to its right. */
    private static final float ICON_PAD = 4f;

    private final CustomPanelAPI rowPanel;
    private final LabelAPI conditionsLabel;

    private KmuConditionPickerInfoRow(CustomPanelAPI rowPanel, LabelAPI conditionsLabel) {
        this.rowPanel = rowPanel;
        this.conditionsLabel = conditionsLabel;
    }

    /** Total height of the info panel as added to the header tooltip body.
     *  One line when there is no location, two lines when there is. */
    static float computeHeight(float topPad, boolean hasLocation) {
        return topPad + (hasLocation ? 2 : 1) * ICON_SIZE;
    }

    static KmuConditionPickerInfoRow render(
            CustomPanelAPI panel,
            KmuLabelSpec locationSpec,
            KmuLabelSpec summarySpec,
            Optional<KmuPickerFaction> faction,
            float width) {
        Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(locationSpec, "locationSpec");
        Objects.requireNonNull(summarySpec, "summarySpec");
        Objects.requireNonNull(faction, "faction");

        boolean hasLocation = !locationSpec.getText().isEmpty();
        float panelHeight = (hasLocation ? 2 : 1) * ICON_SIZE;
        CustomPanelAPI row = panel.createCustomPanel(width, panelHeight, new BaseCustomUIPanelPlugin());

        if (hasLocation) {
            renderLocationText(row, locationSpec, width);
        }

        float conditionsY = hasLocation ? ICON_SIZE : 0f;
        Optional<String> crestSprite = faction.flatMap(KmuPickerFaction::getCrestSprite);
        float textX = crestSprite.isPresent() ? renderIcon(row, crestSprite.get(), conditionsY) : 0f;
        LabelAPI conditionsLabel = renderConditionsText(row, summarySpec, textX, conditionsY, width);
        return new KmuConditionPickerInfoRow(row, conditionsLabel);
    }

    CustomPanelAPI getPanel() {
        return rowPanel;
    }

    LabelAPI getConditionsLabel() {
        return conditionsLabel;
    }

    private static void renderLocationText(CustomPanelAPI row, KmuLabelSpec spec, float width) {
        TooltipMakerAPI textEl = row.createUIElement(width, ICON_SIZE, false);
        LabelAPI label = textEl.addPara(
                spec.getText(),
                StarsectorUiColorProvider.get(StarsectorUiColor.TEXT_WHITE),
                0f);
        spec.applyTo(label);
        row.addUIElement(textEl).inTL(0f, 0f);
    }

    private static float renderIcon(CustomPanelAPI row, String sprite, float y) {
        TooltipMakerAPI iconEl = row.createUIElement(ICON_SIZE, ICON_SIZE, false);
        iconEl.addImage(sprite, ICON_SIZE, ICON_SIZE, 0f);
        row.addUIElement(iconEl).inTL(0f, y);
        return ICON_SIZE + ICON_PAD;
    }

    private static LabelAPI renderConditionsText(
            CustomPanelAPI row, KmuLabelSpec spec, float textX, float y, float width) {
        TooltipMakerAPI textEl = row.createUIElement(width - textX, ICON_SIZE, false);
        LabelAPI label = textEl.addPara(
                spec.getText(),
                StarsectorUiColorProvider.get(StarsectorUiColor.TEXT_WHITE),
                0f);
        spec.applyTo(label);
        row.addUIElement(textEl).inTL(textX, y);
        return label;
    }
}
