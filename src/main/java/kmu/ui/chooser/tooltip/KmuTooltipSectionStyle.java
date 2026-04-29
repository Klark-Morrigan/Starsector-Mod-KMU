package kmu.ui.chooser.tooltip;

import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

public enum KmuTooltipSectionStyle {
    LOW_VIS {
        @Override
        Color titleColor() {
            return Misc.getGrayColor();
        }

        @Override
        Color backgroundColor() {
            return Misc.getDarkPlayerColor();
        }

        @Override
        Color bodyColor() {
            return Misc.getGrayColor();
        }
    },
    WARNING {
        @Override
        Color titleColor() {
            return Misc.getNegativeHighlightColor();
        }

        @Override
        Color backgroundColor() {
            return new Color(70, 20, 20);
        }

        @Override
        Color bodyColor() {
            return Misc.getGrayColor();
        }
    };

    abstract Color titleColor();

    abstract Color backgroundColor();

    abstract Color bodyColor();
}
