package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmlib.starsector.ui.colour.StarsectorUiColour;

import kmu.conditions.domain.StarsectorConditionRepository;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.util.KmuMarketConditionStringKeys;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

import static kmlib.text.KmlibStrings.hasText;

public final class KmuConditionEntryTooltipCreator
        implements TooltipMakerAPI.TooltipCreator {

    private static final float METADATA_PAD = 4f;

    private final Supplier<KmuConditionPickerEntry> entrySupplier;

    public KmuConditionEntryTooltipCreator(Supplier<KmuConditionPickerEntry> entrySupplier) {
        this.entrySupplier = Objects.requireNonNull(entrySupplier, "entrySupplier");
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {

        var entry = entrySupplier.get();
        var renderer = entry.getTooltipRenderer();

        return renderer.isPresent()
            && renderer.get().isTooltipExpandable();
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {

        var entry = entrySupplier.get();
        var renderer = entry.getTooltipRenderer();

        if (renderer.isPresent()) {
            return renderer.get().getTooltipWidth();
        }

        return KmuConditionTooltipRenderer.DEFAULT_TOOLTIP_WIDTH;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {

        var entry = entrySupplier.get();
        var renderer = entry.getTooltipRenderer();

        if (renderer.isPresent() && renderLiveTooltip(tooltip, expanded, renderer.get())) {

            addStatusSections(tooltip, entry);
            addMetadataFooter(tooltip, entry);
            return;
        }

        tooltip.addTitle(entry.getName());

        if (hasText(entry.getTooltipText())) {

            tooltip.addPara(
                entry.getTooltipText(),
                StarsectorUiColour.VANILLA_TEXT.resolve(),
                METADATA_PAD);
        }

        addStatusSections(tooltip, entry);
        addMetadataFooter(tooltip, entry);
    }

    private boolean renderLiveTooltip(
            TooltipMakerAPI tooltip,
            boolean expanded,
            KmuConditionTooltipRenderer renderer) {

        try {
            renderer.createTooltip(tooltip, expanded);
            return true;

        } catch (RuntimeException runtimeException) {
            return false;
        }
    }

    private void addStatusSections(TooltipMakerAPI tooltip, KmuConditionPickerEntry entry) {

        if (entry.isSuppressed()) {

            KmuTooltipSection.add(
                tooltip,
                KmuTooltipSectionStyle.WARNING,
                KmuStringKeys.get(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_SUPPRESSED_TITLE),
                KmuStringKeys.get(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_SUPPRESSED_BODY));
        }

        if (entry.isHidden()) {

            KmuTooltipSection.add(
                tooltip,
                KmuTooltipSectionStyle.WARNING,
                KmuStringKeys.get(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_HIDDEN_TITLE),
                KmuStringKeys.get(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_HIDDEN_BODY));
        }
    }

    private void addMetadataFooter(TooltipMakerAPI tooltip, KmuConditionPickerEntry entry) {

        var metadataLines = new ArrayList<String>();

        metadataLines.add(metadataText(
            KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_SOURCE,
            entry.getSourceModName().orElse(StarsectorConditionRepository.VANILLA_SOURCE_NAME)));

        metadataLines.add(metadataText(
            KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_ID,
            entry.getConditionId()));

        entry
            .getIcon()
            .ifPresent(icon -> metadataLines.add(metadataText(
                KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_ICON,
                icon)));

        if (entry.isPresent()) {

            metadataLines.add(metadataText(
                KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_HIDDEN,
                describeFlag(entry.isHidden())));

            metadataLines.add(metadataText(
                KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_SUPPRESSED,
                describeFlag(entry.isSuppressed())));
        }

        KmuTooltipSection.add(
            tooltip,
            KmuTooltipSectionStyle.MUTED,
            KmuStringKeys.get(KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_TITLE),
            metadataLines);
    }

    private String describeFlag(boolean isSet) {

        return KmuStringKeys.get(isSet
            ? KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_TRUE
            : KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_FALSE);
    }

    private String metadataText(String labelKey, String value) {

        return KmuStringKeys.format(
            KmuMarketConditionStringKeys.CONDITION_MANAGER_TOOLTIP_METADATA_LINE,
            KmuStringKeys.get(labelKey),
            value);
    }
}
