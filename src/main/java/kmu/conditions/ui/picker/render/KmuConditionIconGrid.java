package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import kmu.conditions.ui.picker.action.KmuConditionPickerAction;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.ui.KmuUiPlacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class KmuConditionIconGrid {
    public static final int DEFAULT_COLUMNS = 12;
    static final float CELL_GAP = 8f;
    // Space reserved on the right so the scroll bar does not overlap grid content.
    static final float SCROLLBAR_RIGHT_PAD = 32f;

    // Width of one column slot: button width plus the gap that follows it.
    static float computeColumnUnit() {
        return KmuConditionIconButton.Sizing.squareButtonWidth() + CELL_GAP;
    }

    // Largest grid width that fits within the scrollable area of a panel of
    // containerWidth, snapped down to a whole number of square-icon columns so
    // no column is partially visible. Always returns at least 1.
    static float computeGridWidth(float containerWidth) {
        float availableWidth = Math.max(1f, containerWidth - SCROLLBAR_RIGHT_PAD);
        float squareButtonWidth = KmuConditionIconButton.Sizing.squareButtonWidth();
        if (availableWidth < squareButtonWidth) {
            return availableWidth;
        }
        float units = (float) Math.floor((availableWidth + CELL_GAP) / computeColumnUnit());
        return Math.max(squareButtonWidth, units * computeColumnUnit() - CELL_GAP);
    }

    // N buttons wide with a gap between each pair, but no trailing gap.
    // Clamps to 1 so callers can pass zero without getting a zero-width grid.
    static float computeWidthForSquareColumns(int columns) {
        int safeColumns = Math.max(1, columns);
        return safeColumns * KmuConditionIconButton.Sizing.squareButtonWidth()
                + (safeColumns - 1) * CELL_GAP;
    }

    // Total width a surrounding panel must be to show exactly the given number
    // of square-icon columns, including the scroll bar clearance on the right.
    public static float computeTotalWidthForSquareColumns(int columns) {
        return computeWidthForSquareColumns(columns) + SCROLLBAR_RIGHT_PAD;
    }

    // Total panel width for the default column count.
    public static float computeDefaultTotalWidth() {
        return computeTotalWidthForSquareColumns(DEFAULT_COLUMNS);
    }

    // Measures each entry, lays out buttons in rows, creates the grid panel,
    // places all buttons into it, and adds the panel to the tooltip body.
    // Returns a GridHandle for post-render entry updates.
    public GridHandle addTo(
            CustomPanelAPI parentPanel,
            TooltipMakerAPI tooltip,
            KmuConditionPickerModel model,
            float width,
            float pad,
            Consumer<KmuConditionPickerAction> actionConsumer) {
        Objects.requireNonNull(parentPanel, "parentPanel");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(actionConsumer, "actionConsumer");

        List<KmuConditionPickerEntry> entries = model.getEntries();
        List<KmuConditionIconButton.ButtonMetrics> metrics = measure(entries);
        List<KmuUiPlacement> placements = computeLayout(metrics, width);
        float height = computeHeightForPlacements(placements);
        CustomPanelAPI gridPanel = parentPanel.createCustomPanel(
                width,
                height,
                new BaseCustomUIPanelPlugin());
        Map<String, KmuConditionIconButton> buttonsByConditionId = new LinkedHashMap<>();

        for (int index = 0; index < entries.size(); index++) {
            KmuConditionPickerEntry entry = entries.get(index);
            KmuUiPlacement placement = placements.get(index);
            KmuConditionIconButton button = new KmuConditionIconButton(entry, actionConsumer);

            button.addTo(gridPanel, tooltip, placement.getX(), placement.getY(), metrics.get(index));
            buttonsByConditionId.put(entry.getConditionId(), button);
        }

        tooltip.addCustom(gridPanel, pad);
        return new GridHandle(Collections.singletonList(gridPanel), buttonsByConditionId);
    }

    // Left-to-right, top-to-bottom row-wrapping layout. A button that would
    // overflow the current row starts a new one. The first button in a row is
    // never wrapped even if it is wider than the available width.
    static List<KmuUiPlacement> computeLayout(List<KmuConditionIconButton.ButtonMetrics> metrics, float width) {
        Objects.requireNonNull(metrics, "metrics");

        List<KmuUiPlacement> placements = new ArrayList<>();
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

            placements.add(new KmuUiPlacement(x, y, metric.getButtonWidth(), metric.getButtonHeight()));
            x += metric.getButtonWidth() + CELL_GAP;
            rowHeight = Math.max(rowHeight, metric.getButtonHeight());
        }

        return Collections.unmodifiableList(placements);
    }

    // Bottom edge of the last placement, which equals the total grid height.
    static float computeHeightForPlacements(List<KmuUiPlacement> placements) {
        Objects.requireNonNull(placements, "placements");
        if (placements.isEmpty()) {
            return 0f;
        }
        KmuUiPlacement last = placements.get(placements.size() - 1);
        return last.getY() + last.getHeight();
    }

    private static List<KmuConditionIconButton.ButtonMetrics> measure(List<KmuConditionPickerEntry> entries) {
        List<KmuConditionIconButton.ButtonMetrics> metrics = new ArrayList<>();
        for (KmuConditionPickerEntry entry : entries) {
            metrics.add(KmuConditionIconButton.measure(entry));
        }
        return metrics;
    }

    // Returned by addTo; holds live button references so the caller can push
    // updated entry state into the grid after the initial render.
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

        // Pushes updated entry state to the button with a matching condition ID.
        // Returns false if no such button exists in this grid.
        boolean updateEntry(KmuConditionPickerEntry entry) {
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
