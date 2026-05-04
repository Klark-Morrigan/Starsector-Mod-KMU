package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import kmu.KmuStrings;
import kmu.conditions.ui.picker.action.KmuConditionPickerAction;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.render.spec.KmuConditionPickerLocationLabelSpecFactory;
import kmu.conditions.ui.picker.render.spec.KmuConditionPickerSummaryLabelSpecFactory;
import kmu.conditions.ui.picker.render.spec.KmuLabelSpec;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class KmuConditionPickerContainer {
    // Top padding applied before each element added to the header or grid body.
    private static final float ITEM_TOP_PAD = 8f;
    private final CustomPanelAPI panel;
    private final KmuConditionIconGrid grid;

    public KmuConditionPickerContainer(CustomPanelAPI panel) {
        this(panel, new KmuConditionIconGrid());
    }

    KmuConditionPickerContainer(CustomPanelAPI panel, KmuConditionIconGrid grid) {
        this.panel = Objects.requireNonNull(panel, "panel");
        this.grid = Objects.requireNonNull(grid, "grid");
    }

    /**
     * Renders the summary panel (location + conditions + optional faction icon)
     * into {@code headerBody} (non-scrollable), and the condition grid into
     * {@code gridBody} (scrollable). Keeping them in separate elements pins
     * the summary above the scroll area so it remains visible while the user
     * scrolls through conditions.
     */
    public KmuConditionPickerRenderResult render(
            TooltipMakerAPI headerBody,
            TooltipMakerAPI gridBody,
            KmuConditionPickerModel model,
            Consumer<KmuConditionPickerAction> actionConsumer,
            float width) {
        Objects.requireNonNull(headerBody, "headerBody");
        Objects.requireNonNull(gridBody, "gridBody");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(actionConsumer, "actionConsumer");

        KmuLabelSpec locationSpec = KmuConditionPickerLocationLabelSpecFactory.get(model);
        KmuLabelSpec summarySpec = KmuConditionPickerSummaryLabelSpecFactory.get(model);
        KmuConditionPickerInfoRow summaryRow = KmuConditionPickerInfoRow.render(
                panel, locationSpec, summarySpec, model.getLocation().getFaction(), width);
        LabelAPI summaryLabel = summaryRow.getConditionsLabel();
        headerBody.addCustom(summaryRow.getPanel(), ITEM_TOP_PAD);
        List<UIComponentAPI> summaryComponents = new ArrayList<>();
        summaryComponents.add(summaryRow.getPanel());

        // Defensive empty state: the opener should usually avoid empty pickers,
        // but if no entries are renderable, show localized UI copy and skip the grid.
        if (model.isEmpty()) {
            gridBody.addPara(
                    KmuStrings.get(KmuStrings.CONDITION_PICKER_EMPTY),
                    ITEM_TOP_PAD,
                    StarsectorUiColorProvider.get(StarsectorUiColor.GRAY));
            return new KmuConditionPickerRenderResult(summaryLabel, summaryComponents, null);
        }

        KmuConditionIconGrid.GridHandle gridHandle = grid.addTo(
                panel,
                gridBody,
                model,
                KmuConditionIconGrid.computeGridWidth(width),
                ITEM_TOP_PAD,
                actionConsumer);
        List<UIComponentAPI> allComponents = new ArrayList<>(summaryComponents);
        allComponents.addAll(gridHandle.getComponents());
        return new KmuConditionPickerRenderResult(summaryLabel, allComponents, gridHandle);
    }

    /**
     * Height the non-scrollable header element must be to fit the summary panel.
     * Includes the location line when the model has displayable location data.
     */
    public static float computeHeaderHeight(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");
        return KmuConditionPickerInfoRow.computeHeight(ITEM_TOP_PAD, hasDisplayableLocationFields(model));
    }

    private static boolean hasDisplayableLocationFields(KmuConditionPickerModel model) {
        KmuConditionPickerLocation loc = model.getLocation();
        return loc.getPlanetName().isPresent()
                || loc.getFaction().isPresent()
                || loc.getStarSystemName().isPresent()
                || loc.getConstellationName().isPresent();
    }
}
