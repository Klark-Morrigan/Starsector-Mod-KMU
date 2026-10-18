package kmu.conditions.ui.picker.model;

import kmu.conditions.ui.picker.tooltip.KmuConditionTooltipRenderer;

import java.util.Objects;
import java.util.Optional;

import static kmlib.text.KmlibStrings.getTextOrEmpty;
import static kmlib.text.KmlibStrings.normaliseText;
import static kmlib.text.KmlibStrings.requireFieldText;

public final class KmuConditionPickerEntry {

    private final String conditionId;
    private final String name;
    private final String icon;
    private final KmuConditionPickerEntryState state;
    private final String tooltipText;
    private final String sourceModName;
    private final KmuConditionTooltipRenderer tooltipRenderer;
    private final boolean isSuppressed;
    private final boolean isHidden;

    private KmuConditionPickerEntry(Builder builder) {

        this.conditionId = requireFieldText(builder.conditionId, "conditionId");
        this.name = requireFieldText(builder.name, "name");
        this.icon = normaliseText(builder.icon);
        this.state = Objects.requireNonNull(builder.state, "state");
        this.tooltipText = getTextOrEmpty(builder.tooltipText);
        this.sourceModName = normaliseText(builder.sourceModName);
        this.tooltipRenderer = builder.tooltipRenderer;
        this.isSuppressed = builder.isSuppressed;
        this.isHidden = builder.isHidden;
    }

    public String getConditionId() {
        return conditionId;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getIcon() {
        return Optional.ofNullable(icon);
    }

    public KmuConditionPickerEntryState getState() {
        return state;
    }

    public String getTooltipText() {
        return tooltipText;
    }

    public Optional<String> getSourceModName() {
        return Optional.ofNullable(sourceModName);
    }

    public Optional<KmuConditionTooltipRenderer> getTooltipRenderer() {
        return Optional.ofNullable(tooltipRenderer);
    }

    public boolean isPresent() {
        return state == KmuConditionPickerEntryState.PRESENT;
    }

    public boolean isSuppressed() {
        return isSuppressed;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public String descriptionLine() {
        return conditionId + " - " + state.getDisplayName();
    }

    /**
     * Builds an entry from the three parts every entry has, plus whichever of the rest are known.
     *
     * <p>The rest are named where they are set. Two of them are flags and three are strings, which a
     * positional list cannot tell apart at the call site.
     */
    public static final class Builder {

        private final String conditionId;
        private final String name;
        private final KmuConditionPickerEntryState state;

        private String icon;
        private String tooltipText;
        private String sourceModName;
        private KmuConditionTooltipRenderer tooltipRenderer;
        private boolean isSuppressed;
        private boolean isHidden;

        public Builder(String conditionId, String name, KmuConditionPickerEntryState state) {

            this.conditionId = conditionId;
            this.name = name;
            this.state = state;
        }

        public Builder withIcon(String icon) {

            this.icon = icon;
            return this;
        }

        public Builder withTooltipText(String tooltipText) {

            this.tooltipText = tooltipText;
            return this;
        }

        public Builder withSourceModName(String sourceModName) {

            this.sourceModName = sourceModName;
            return this;
        }

        public Builder withSuppressed(boolean isSuppressed) {

            this.isSuppressed = isSuppressed;
            return this;
        }

        public Builder withHidden(boolean isHidden) {

            this.isHidden = isHidden;
            return this;
        }

        public KmuConditionPickerEntry build() {
            return new KmuConditionPickerEntry(this);
        }

        // Package-private: only the model factory holds a live condition to render from.
        Builder withTooltipRenderer(KmuConditionTooltipRenderer tooltipRenderer) {

            this.tooltipRenderer = tooltipRenderer;
            return this;
        }
    }
}
