package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.Objects;

public final class StarsectorConditionTooltipRenderer
        implements KmuConditionTooltipRenderer {

    private final MarketConditionAPI condition;

    public StarsectorConditionTooltipRenderer(MarketConditionAPI condition) {
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    @Override
    public boolean isTooltipExpandable() {

        var plugin = getPlugin();
        if (plugin == null) {
            return false;
        }

        try {
            return plugin.isTooltipExpandable();

        } catch (RuntimeException runtimeException) {
            return false;
        }
    }

    @Override
    public float getTooltipWidth() {

        var plugin = getPlugin();
        if (plugin == null) {
            return DEFAULT_TOOLTIP_WIDTH;
        }

        try {
            var width = plugin.getTooltipWidth();
            return width > 0f
                ? width
                : DEFAULT_TOOLTIP_WIDTH;

        } catch (RuntimeException runtimeException) {
            return DEFAULT_TOOLTIP_WIDTH;
        }
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {

        var plugin = getPlugin();
        if (plugin == null) {

            tooltip.addTitle(condition.getName());
            return;
        }

        plugin.createTooltip(tooltip, expanded);
    }

    private MarketConditionPlugin getPlugin() {
        try {
            return condition.getPlugin();

        } catch (RuntimeException runtimeException) {
            return null;
        }
    }
}
