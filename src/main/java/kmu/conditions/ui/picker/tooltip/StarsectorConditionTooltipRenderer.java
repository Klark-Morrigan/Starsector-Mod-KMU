package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.Objects;

public final class StarsectorConditionTooltipRenderer implements KmuConditionTooltipRenderer {
    private static final float FALLBACK_WIDTH = 420f;

    private final MarketConditionAPI condition;

    public StarsectorConditionTooltipRenderer(MarketConditionAPI condition) {
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    @Override
    public boolean isTooltipExpandable() {
        MarketConditionPlugin plugin = getPlugin();
        if (plugin == null) {
            return false;
        }
        try {
            return plugin.isTooltipExpandable();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public float getTooltipWidth() {
        MarketConditionPlugin plugin = getPlugin();
        if (plugin == null) {
            return FALLBACK_WIDTH;
        }
        try {
            float width = plugin.getTooltipWidth();
            return width > 0f ? width : FALLBACK_WIDTH;
        } catch (RuntimeException exception) {
            return FALLBACK_WIDTH;
        }
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        MarketConditionPlugin plugin = getPlugin();
        if (plugin == null) {
            tooltip.addTitle(condition.getName());
            return;
        }
        plugin.createTooltip(tooltip, expanded);
    }

    private MarketConditionPlugin getPlugin() {
        try {
            return condition.getPlugin();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
