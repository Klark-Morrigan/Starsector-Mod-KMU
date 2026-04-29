package kmu.ui.chooser.tooltip;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class KmuTooltipSection {
    private static final float HEADING_PAD = 10f;
    private static final float LINE_PAD = 4f;

    private KmuTooltipSection() {
    }

    public static void add(
            TooltipMakerAPI tooltip,
            KmuTooltipSectionStyle style,
            String title,
            String line) {
        add(tooltip, style, title, Collections.singletonList(line));
    }

    public static void add(
            TooltipMakerAPI tooltip,
            KmuTooltipSectionStyle style,
            String title,
            List<String> lines) {
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lines, "lines");

        tooltip.addSectionHeading(
                title,
                style.titleColor(),
                style.backgroundColor(),
                Alignment.MID,
                HEADING_PAD);
        tooltip.setParaFontColor(style.bodyColor());
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                tooltip.addPara(line, LINE_PAD);
            }
        }
    }
}
