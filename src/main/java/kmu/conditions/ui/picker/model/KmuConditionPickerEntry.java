package kmu.conditions.ui.picker.model;

import kmu.conditions.ui.picker.tooltip.KmuConditionTooltipRenderer;

import java.util.Objects;
import java.util.Optional;

import static kmu.KmuValues.getTextOrEmpty;
import static kmu.KmuValues.normalizeText;
import static kmu.KmuValues.requireNonBlankText;

public final class KmuConditionPickerEntry {
    private final String conditionId;
    private final String name;
    private final String icon;
    private final KmuConditionPickerEntryState state;
    private final String tooltipText;
    private final String sourceModName;
    private final KmuConditionTooltipRenderer tooltipRenderer;
    private final boolean suppressed;
    private final boolean hidden;

    public KmuConditionPickerEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionPickerEntryState state,
            String tooltipText) {
        this(conditionId, name, icon, state, tooltipText, null);
    }

    public KmuConditionPickerEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionPickerEntryState state,
            String tooltipText,
            String sourceModName) {
        this(conditionId, name, icon, state, tooltipText, sourceModName, false, false);
    }

    public KmuConditionPickerEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionPickerEntryState state,
            String tooltipText,
            String sourceModName,
            boolean suppressed,
            boolean hidden) {
        this(conditionId, name, icon, state, tooltipText, sourceModName, null, suppressed, hidden);
    }

    KmuConditionPickerEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionPickerEntryState state,
            String tooltipText,
            String sourceModName,
            KmuConditionTooltipRenderer tooltipRenderer) {
        this(conditionId, name, icon, state, tooltipText, sourceModName, tooltipRenderer, false, false);
    }

    KmuConditionPickerEntry(
            String conditionId,
            String name,
            String icon,
            KmuConditionPickerEntryState state,
            String tooltipText,
            String sourceModName,
            KmuConditionTooltipRenderer tooltipRenderer,
            boolean suppressed,
            boolean hidden) {
        this.conditionId = requireNonBlankText(conditionId, "conditionId");
        this.name = requireNonBlankText(name, "name");
        this.icon = normalizeText(icon);
        this.state = Objects.requireNonNull(state, "state");
        this.tooltipText = getTextOrEmpty(tooltipText);
        this.sourceModName = normalizeText(sourceModName);
        this.tooltipRenderer = tooltipRenderer;
        this.suppressed = suppressed;
        this.hidden = hidden;
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
        return suppressed;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String descriptionLine() {
        return conditionId + " - " + state.getDisplayName();
    }
}
