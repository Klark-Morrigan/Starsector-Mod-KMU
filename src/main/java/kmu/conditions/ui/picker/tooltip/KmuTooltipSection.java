package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmlib.starsector.ui.tooltip.TooltipSpacing;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static kmlib.text.KmlibStrings.hasText;

public final class KmuTooltipSection {
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
            style.titleColour(),
            style.backgroundColour(),
            Alignment.MID,
            TooltipSpacing.OUTER_PAD);

        for (var line : lines) {
            if (hasText(line)) {
                tooltip.addPara(line, style.bodyColour(), LINE_PAD);
            }
        }
    }
}
