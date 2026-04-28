package kmu.ui.chooser.tooltip;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

public interface KmuConditionTooltipRenderer {
    boolean isTooltipExpandable();

    float getTooltipWidth();

    void createTooltip(TooltipMakerAPI tooltip, boolean expanded);
}
