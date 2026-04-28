package kmu.ui.chooser.render;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import kmu.ui.chooser.action.KmuConditionChooserAction;
import kmu.ui.chooser.model.KmuConditionChooserEntry;
import kmu.ui.chooser.model.KmuConditionChooserModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class KmuConditionIconGrid {
    private static final float CELL_GAP = 8f;

    public GridHandle addTo(
            CustomPanelAPI parentPanel,
            TooltipMakerAPI tooltip,
            KmuConditionChooserModel model,
            float width,
            float pad,
            Consumer<KmuConditionChooserAction> actionConsumer) {
        Objects.requireNonNull(parentPanel, "parentPanel");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(actionConsumer, "actionConsumer");

        List<KmuConditionChooserEntry> entries = model.getEntries();
        List<KmuConditionIconButton.ButtonMetrics> metrics = measure(entries);
        List<Placement> placements = layout(metrics, width);
        float height = heightForPlacements(placements);
        CustomPanelAPI gridPanel = parentPanel.createCustomPanel(
                width,
                height,
                new BaseCustomUIPanelPlugin());
        Map<String, KmuConditionIconButton> buttonsByConditionId = new LinkedHashMap<>();

        for (int index = 0; index < entries.size(); index++) {
            KmuConditionChooserEntry entry = entries.get(index);
            Placement placement = placements.get(index);
            KmuConditionIconButton button = new KmuConditionIconButton(entry, actionConsumer);

            button.addTo(gridPanel, tooltip, placement.getX(), placement.getY(), metrics.get(index));
            buttonsByConditionId.put(entry.getConditionId(), button);
        }

        tooltip.addCustom(gridPanel, pad);
        return new GridHandle(Collections.singletonList(gridPanel), buttonsByConditionId);
    }

    static List<Placement> layout(List<KmuConditionIconButton.ButtonMetrics> metrics, float width) {
        Objects.requireNonNull(metrics, "metrics");

        List<Placement> placements = new ArrayList<>();
        float safeWidth = Math.max(1f, width);
        float x = 0f;
        float y = 0f;
        float rowHeight = 0f;

        for (KmuConditionIconButton.ButtonMetrics metric : metrics) {
            Objects.requireNonNull(metric, "metric");
            if (x > 0f && x + metric.getButtonWidth() > safeWidth) {
                x = 0f;
                y += rowHeight + CELL_GAP;
                rowHeight = 0f;
            }

            placements.add(new Placement(x, y, metric.getButtonWidth(), metric.getButtonHeight()));
            x += metric.getButtonWidth() + CELL_GAP;
            rowHeight = Math.max(rowHeight, metric.getButtonHeight());
        }

        return Collections.unmodifiableList(placements);
    }

    static float heightForPlacements(List<Placement> placements) {
        Objects.requireNonNull(placements, "placements");
        if (placements.isEmpty()) {
            return 0f;
        }
        Placement last = placements.get(placements.size() - 1);
        return last.getY() + last.getHeight();
    }

    private static List<KmuConditionIconButton.ButtonMetrics> measure(List<KmuConditionChooserEntry> entries) {
        List<KmuConditionIconButton.ButtonMetrics> metrics = new ArrayList<>();
        for (KmuConditionChooserEntry entry : entries) {
            metrics.add(KmuConditionIconButton.measure(entry));
        }
        return metrics;
    }

    static final class Placement {
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        private Placement(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
        }

        float getWidth() {
            return width;
        }

        float getHeight() {
            return height;
        }
    }

    static final class GridHandle {
        private final List<UIComponentAPI> components;
        private final Map<String, KmuConditionIconButton> buttonsByConditionId;

        private GridHandle(
                List<UIComponentAPI> components,
                Map<String, KmuConditionIconButton> buttonsByConditionId) {
            this.components = Collections.unmodifiableList(new ArrayList<>(components));
            this.buttonsByConditionId = Collections.unmodifiableMap(new LinkedHashMap<>(buttonsByConditionId));
        }

        List<UIComponentAPI> getComponents() {
            return components;
        }

        boolean updateEntry(KmuConditionChooserEntry entry) {
            Objects.requireNonNull(entry, "entry");
            KmuConditionIconButton button = buttonsByConditionId.get(entry.getConditionId());
            if (button == null) {
                return false;
            }
            button.updateEntry(entry);
            return true;
        }
    }
}
