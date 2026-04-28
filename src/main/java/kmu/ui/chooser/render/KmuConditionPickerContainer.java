package kmu.ui.chooser.render;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import kmu.ui.chooser.action.KmuConditionChooserAction;
import kmu.ui.chooser.model.KmuConditionChooserModel;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;

public final class KmuConditionPickerContainer {
    private static final float ENTRY_PAD = 8f;
    static final float GRID_SCROLLBAR_RIGHT_PAD = 32f;

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
            KmuConditionChooserModel model,
            Consumer<KmuConditionChooserAction> actionConsumer,
            float width) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(actionConsumer, "actionConsumer");

        body.addTitle("Planetary Conditions");
        LabelAPI summaryLabel = body.addPara(summaryText(model), ENTRY_PAD);

        if (model.isEmpty()) {
            body.addPara("No planetary condition specs are available.", ENTRY_PAD, Misc.getGrayColor());
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
        return Math.max(1f, containerWidth - GRID_SCROLLBAR_RIGHT_PAD);
    }

    public static String summaryText(KmuConditionChooserModel model) {
        return model.getEntryCount()
                + " planetary conditions found; "
                + model.getPresentCount()
                + " already present on this market.";
    }
}
