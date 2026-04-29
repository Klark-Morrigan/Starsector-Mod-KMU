package kmu.ui.chooser.tooltip;

import java.awt.Color;

public enum KmuTooltipSectionStyle {
    MUTED {
        @Override
        Color titleColor() {
            return KmuTooltipSectionPalette.standardBlueTitle();
        }

        @Override
        Color backgroundColor() {
            return KmuTooltipSectionPalette.standardSectionBackdrop();
        }

        @Override
        Color bodyColor() {
            return KmuTooltipSectionPalette.mutedText();
        }
    },
    WARNING {
        @Override
        Color titleColor() {
            return KmuTooltipSectionPalette.warningTitle();
        }

        @Override
        Color backgroundColor() {
            return KmuTooltipSectionPalette.WARNING_BACKDROP;
        }

        @Override
        Color bodyColor() {
            return KmuTooltipSectionPalette.standardText();
        }
    };

    abstract Color titleColor();

    abstract Color backgroundColor();

    abstract Color bodyColor();
}
