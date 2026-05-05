package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;
import kmu.util.KmuLocalisation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static kmu.util.KmuValues.hasText;

public final class KmuConditionEntryTooltipCreator implements TooltipMakerAPI.TooltipCreator {
    private static final float METADATA_PAD = 4f;
    private static final float TOOLTIP_WIDTH = 420f;

    private final Supplier<KmuConditionPickerEntry> entrySupplier;

    public KmuConditionEntryTooltipCreator(Supplier<KmuConditionPickerEntry> entrySupplier) {
        this.entrySupplier = Objects.requireNonNull(entrySupplier, "entrySupplier");
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        KmuConditionPickerEntry entry = entrySupplier.get();
        Optional<KmuConditionTooltipRenderer> renderer = entry.getTooltipRenderer();
        return renderer.isPresent() && renderer.get().isTooltipExpandable();
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        KmuConditionPickerEntry entry = entrySupplier.get();
        Optional<KmuConditionTooltipRenderer> renderer = entry.getTooltipRenderer();
        if (renderer.isPresent()) {
            return renderer.get().getTooltipWidth();
        }
        return TOOLTIP_WIDTH;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        KmuConditionPickerEntry entry = entrySupplier.get();
        Optional<KmuConditionTooltipRenderer> renderer = entry.getTooltipRenderer();
        if (renderer.isPresent() && renderLiveTooltip(tooltip, expanded, renderer.get())) {
            addStatusSections(tooltip, entry);
            addMetadataFooter(tooltip, entry);
            return;
        }

        tooltip.addTitle(entry.getName());
        if (hasText(entry.getTooltipText())) {
            tooltip.addPara(
                    entry.getTooltipText(),
                    StarsectorUiColorProvider.get(StarsectorUiColor.TEXT_WHITE),
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
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void addStatusSections(TooltipMakerAPI tooltip, KmuConditionPickerEntry entry) {
        if (entry.isSuppressed()) {
            KmuTooltipSection.add(
                    tooltip,
                    KmuTooltipSectionStyle.WARNING,
                    KmuLocalisation.get(KmuLocalisation.CONDITION_PICKER_TOOLTIP_SUPPRESSED_TITLE),
                    KmuLocalisation.get(KmuLocalisation.CONDITION_PICKER_TOOLTIP_SUPPRESSED_BODY));
        }
        if (entry.isHidden()) {
            KmuTooltipSection.add(
                    tooltip,
                    KmuTooltipSectionStyle.WARNING,
                    KmuLocalisation.get(KmuLocalisation.CONDITION_PICKER_TOOLTIP_HIDDEN_TITLE),
                    KmuLocalisation.get(KmuLocalisation.CONDITION_PICKER_TOOLTIP_HIDDEN_BODY));
        }
    }

    private void addMetadataFooter(TooltipMakerAPI tooltip, KmuConditionPickerEntry entry) {
        List<String> metadataLines = new ArrayList<>();
        metadataLines.add(metadataText("source", entry.getSourceModName().orElse("Starsector")));
        metadataLines.add(metadataText("id", entry.getConditionId()));
        entry.getIcon().ifPresent(icon -> metadataLines.add(metadataText("icon", icon)));
        if (entry.isPresent()) {
            metadataLines.add(metadataText("hidden", String.valueOf(entry.isHidden())));
            metadataLines.add(metadataText("suppressed", String.valueOf(entry.isSuppressed())));
        }
        KmuTooltipSection.add(tooltip, KmuTooltipSectionStyle.MUTED, "Metadata", metadataLines);
    }

    private String metadataText(String label, String value) {
        return label + ": " + value;
    }
}
