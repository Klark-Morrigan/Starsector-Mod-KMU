package kmu.ui.chooser.model;

import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import kmu.conditions.KmuConditionService;
import kmu.conditions.KmuConditionSpec;
import kmu.conditions.KmuEditableMarket;
import kmu.conditions.StarsectorEditableMarket;
import kmu.ui.chooser.tooltip.KmuConditionTooltipRenderer;
import kmu.ui.chooser.tooltip.StarsectorConditionTooltipRenderer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class KmuConditionChooserModelFactory {
    private final KmuConditionService conditionService;

    public KmuConditionChooserModelFactory(KmuConditionService conditionService) {
        this.conditionService = Objects.requireNonNull(conditionService, "conditionService");
    }

    public KmuConditionChooserModel create(KmuEditableMarket market) {
        Objects.requireNonNull(market, "market");
        Set<String> currentConditionIds = conditionService.getCurrentConditionIds(market);
        List<KmuConditionSpec> specs = conditionService.listConditionSpecsVisibleForMarket(market);

        return new KmuConditionChooserModel(specs.stream()
                .map(spec -> toEntry(spec, currentConditionIds, market))
                .collect(Collectors.toUnmodifiableList()));
    }

    private KmuConditionChooserEntry toEntry(
            KmuConditionSpec spec,
            Set<String> currentConditionIds,
            KmuEditableMarket market) {
        KmuConditionChooserEntryState state = currentConditionIds.contains(spec.getId())
                ? KmuConditionChooserEntryState.PRESENT
                : KmuConditionChooserEntryState.ABSENT;
        return new KmuConditionChooserEntry(
                spec.getId(),
                spec.getName(),
                iconFor(spec, state, market),
                state,
                tooltipText(spec, state),
                spec.getSourceModName(),
                tooltipRendererFor(spec, state, market).orElse(null));
    }

    private String tooltipText(KmuConditionSpec spec, KmuConditionChooserEntryState state) {
        if (state == KmuConditionChooserEntryState.PRESENT) {
            return normalizeTooltipText(spec);
        }
        return normalizeTooltipText(spec);
    }

    private String normalizeTooltipText(KmuConditionSpec spec) {
        String description = spec.getDescription();
        if (description == null || description.trim().isEmpty()) {
            return "";
        }
        return description.trim();
    }

    private Optional<KmuConditionTooltipRenderer> tooltipRendererFor(
            KmuConditionSpec spec,
            KmuConditionChooserEntryState state,
            KmuEditableMarket market) {
        return liveConditionFor(spec, state, market)
                .map(StarsectorConditionTooltipRenderer::new);
    }

    private String iconFor(
            KmuConditionSpec spec,
            KmuConditionChooserEntryState state,
            KmuEditableMarket market) {
        return liveConditionFor(spec, state, market)
                .map(this::liveIconName)
                .filter(icon -> icon != null && !icon.trim().isEmpty())
                .orElse(spec.getIcon());
    }

    private Optional<MarketConditionAPI> liveConditionFor(
            KmuConditionSpec spec,
            KmuConditionChooserEntryState state,
            KmuEditableMarket market) {
        if (state != KmuConditionChooserEntryState.PRESENT || !(market instanceof StarsectorEditableMarket)) {
            return Optional.empty();
        }
        try {
            return ((StarsectorEditableMarket) market)
                    .findCondition(spec.getId());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String liveIconName(MarketConditionAPI condition) {
        try {
            MarketConditionPlugin plugin = condition.getPlugin();
            if (plugin == null) {
                return null;
            }
            return plugin.getIconName();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
