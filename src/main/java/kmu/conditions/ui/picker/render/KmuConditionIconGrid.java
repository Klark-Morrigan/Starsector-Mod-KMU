package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;

import kmlib.math.geometry.Rectangle;

import kmu.conditions.ui.picker.action.KmuConditionPickerAction;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;

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
    /** Space reserved on the right so the scroll bar does not overlap grid content. */
    static final float SCROLLBAR_RIGHT_PAD = 18f;

    /** Width of one column slot: button width plus the gap that follows it. */
    static float computeColumnUnit() {
        return KmuConditionIconButtonSizing.computeSquareButtonWidth() + CELL_GAP;
    }

    /** Largest grid width that fits within the scrollable area of a panel of
     *  {@code containerWidth}, snapped down to a whole number of square-icon columns
     *  so no column is partially visible. Always returns at least 1. */
    static float computeGridWidth(float containerWidth) {
        var availableWidth = Math.max(1f, containerWidth - SCROLLBAR_RIGHT_PAD);
        var squareButtonWidth = KmuConditionIconButtonSizing.computeSquareButtonWidth();
        if (availableWidth < squareButtonWidth) {
            return availableWidth;
        }
        var units = (float) Math.floor((availableWidth + CELL_GAP) / computeColumnUnit());
        return Math.max(squareButtonWidth, units * computeColumnUnit() - CELL_GAP);
    }

    /** N buttons wide with a gap between each pair, but no trailing gap.
     *  Clamps to 1 so callers can pass zero without getting a zero-width grid. */
    static float computeWidthForSquareColumns(int columns) {
        var safeColumns = Math.max(1, columns);
        return safeColumns * KmuConditionIconButtonSizing.computeSquareButtonWidth()
                + (safeColumns - 1) * CELL_GAP;
    }

    /** Total width a surrounding panel must be to show exactly the given number
     *  of square-icon columns, including the scroll bar clearance on the right. */
    public static float computeTotalWidthForSquareColumns(int columns) {
        return computeWidthForSquareColumns(columns) + SCROLLBAR_RIGHT_PAD;
    }

    /** Total panel width for the default column count. */
    public static float computeDefaultTotalWidth() {
        return computeTotalWidthForSquareColumns(DEFAULT_COLUMNS);
    }

    /** Measures each entry, lays out buttons in rows, creates the grid panel,
     *  places all buttons into it, and adds the panel to the tooltip body.
     *  Returns a {@link GridHandle} for post-render entry updates. */
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

        var entries = model.getEntries();
        var metrics = computeKmuConditionIconButtonLayouts(entries);
        var placements = computeLayout(metrics, width);
        var height = computeHeightForPlacements(placements);
        var gridPanel = parentPanel.createCustomPanel(
                width,
                height,
                new BaseCustomUIPanelPlugin());
        var buttonsByConditionId = new LinkedHashMap<String, KmuConditionIconButton>();

        for (var index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            var placement = placements.get(index);
            var button = new KmuConditionIconButton(entry, actionConsumer);

            button.addTo(gridPanel, tooltip, placement.x(), placement.y(), metrics.get(index));
            buttonsByConditionId.put(entry.getConditionId(), button);
        }

        tooltip.addCustom(gridPanel, pad);
        return new GridHandle(Collections.singletonList(gridPanel), buttonsByConditionId);
    }

    /** Left-to-right, top-to-bottom row-wrapping layout. A button that would
     *  overflow the current row starts a new one. The first button in a row is
     *  never wrapped even if it is wider than the available width. */
    static List<Rectangle> computeLayout(List<KmuConditionIconButtonLayout> metrics, float width) {
        Objects.requireNonNull(metrics, "metrics");

        var placements = new ArrayList<Rectangle>();
        var safeWidth = Math.max(1f, width);
        var x = 0f;
        var y = 0f;
        var rowHeight = 0f;

        for (var metric : metrics) {
            Objects.requireNonNull(metric, "metric");
            if (x > 0f && x + metric.getButtonWidth() > safeWidth) {
                x = 0f;
                y += rowHeight + CELL_GAP;
                rowHeight = 0f;
            }

            placements.add(new Rectangle(x, y, metric.getButtonWidth(), metric.getButtonHeight()));
            x += metric.getButtonWidth() + CELL_GAP;
            rowHeight = Math.max(rowHeight, metric.getButtonHeight());
        }

        return Collections.unmodifiableList(placements);
    }

    /** Bottom edge of the last placement, which equals the total grid height. */
    static float computeHeightForPlacements(List<Rectangle> placements) {
        Objects.requireNonNull(placements, "placements");
        if (placements.isEmpty()) {
            return 0f;
        }
        var last = placements.get(placements.size() - 1);
        return last.y() + last.height();
    }

    private static List<KmuConditionIconButtonLayout> computeKmuConditionIconButtonLayouts(List<KmuConditionPickerEntry> entries) {
        var metrics = new ArrayList<KmuConditionIconButtonLayout>();
        for (var entry : entries) {
            metrics.add(KmuConditionIconButton.computeKmuConditionIconButtonLayout(entry));
        }
        return metrics;
    }

    /** Holds live button references so the caller can push updated entry state
     *  into the grid after the initial render. */
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

        /** Pushes updated entry state to the button with a matching condition ID.
         *  Returns false if no such button exists in this grid. */
        boolean updateEntry(KmuConditionPickerEntry entry) {
            Objects.requireNonNull(entry, "entry");
            var button = buttonsByConditionId.get(entry.getConditionId());
            if (button == null) {
                return false;
            }
            button.updateEntry(entry);
            return true;
        }
    }
}
