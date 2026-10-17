package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface KmuConditionTooltipRenderer {

    /**
     * The width a condition's tooltip opens at where nothing says otherwise: a condition with no
     * renderer, and one whose plugin names no width of its own.
     */
    float DEFAULT_TOOLTIP_WIDTH = 420f;

    boolean isTooltipExpandable();

    float getTooltipWidth();

    void createTooltip(TooltipMakerAPI tooltip, boolean expanded);
}
