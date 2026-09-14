package kmu.conditions.ui.picker.model;

import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.KmuConditionSpec;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.domain.StarsectorEditableMarket;
import kmu.conditions.ui.picker.tooltip.KmuConditionTooltipRenderer;
import kmu.conditions.ui.picker.tooltip.StarsectorConditionTooltipRenderer;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static kmu.util.KmuValues.getTextOrEmpty;
import static kmu.util.KmuValues.hasText;

public final class KmuConditionPickerModelFactory {
    private final KmuConditionService conditionService;
    private final StarsectorConditionPickerLocationFactory locationFactory;

    public KmuConditionPickerModelFactory(KmuConditionService conditionService) {
        this(conditionService, new StarsectorConditionPickerLocationFactory());
    }

    KmuConditionPickerModelFactory(
            KmuConditionService conditionService,
            StarsectorConditionPickerLocationFactory locationFactory) {
        this.conditionService = Objects.requireNonNull(conditionService, "conditionService");
        this.locationFactory = Objects.requireNonNull(locationFactory, "locationFactory");
    }

    public KmuConditionPickerModel create(KmuEditableMarket market) {
        Objects.requireNonNull(market, "market");
        var currentConditionIds = conditionService.getCurrentConditionIds(market);
        var specs = conditionService.listConditionSpecsVisibleForMarket(market);

        return new KmuConditionPickerModel(
            specs.stream()
                .map(spec -> toEntry(spec, currentConditionIds, market))
                .collect(Collectors.toUnmodifiableList()),
            locationFactory.create(market));
    }

    private KmuConditionPickerEntry toEntry(
            KmuConditionSpec spec,
            Set<String> currentConditionIds,
            KmuEditableMarket market) {

        var state = currentConditionIds.contains(spec.getId())
            ? KmuConditionPickerEntryState.PRESENT
            : KmuConditionPickerEntryState.ABSENT;

        var liveCondition = liveConditionFor(spec, state, market);

        return new KmuConditionPickerEntry(
            spec.getId(),
            spec.getName(),
            iconFor(spec, liveCondition),
            state,
            tooltipText(spec),
            spec.getSourceModName(),
            tooltipRendererFor(liveCondition).orElse(null),
            isSuppressed(spec, state, market),
            liveCondition.map(this::isHidden).orElse(false));
    }

    private String tooltipText(KmuConditionSpec spec) {
        return getTextOrEmpty(spec.getDescription());
    }

    private Optional<KmuConditionTooltipRenderer> tooltipRendererFor(Optional<MarketConditionAPI> liveCondition) {
        return liveCondition.map(StarsectorConditionTooltipRenderer::new);
    }

    private String iconFor(
            KmuConditionSpec spec,
            Optional<MarketConditionAPI> liveCondition) {

        return liveCondition
            .map(this::liveIconName)
            .filter(icon -> hasText(icon))
            .orElse(spec.getIcon());
    }

    private boolean isSuppressed(
            KmuConditionSpec spec,
            KmuConditionPickerEntryState state,
            KmuEditableMarket market) {

        if (state != KmuConditionPickerEntryState.PRESENT) {
            return false;
        }
        try {
            return market.isConditionSuppressed(spec.getId());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Optional<MarketConditionAPI> liveConditionFor(
            KmuConditionSpec spec,
            KmuConditionPickerEntryState state,
            KmuEditableMarket market) {

        if (state != KmuConditionPickerEntryState.PRESENT
                || !(market instanceof StarsectorEditableMarket)) {
            return Optional.empty();
        }
        try {
            return ((StarsectorEditableMarket) market).findCondition(spec.getId());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String liveIconName(MarketConditionAPI condition) {
        try {
            var plugin = condition.getPlugin();
            if (plugin == null) {
                return null;
            }
            return plugin.getIconName();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isHidden(MarketConditionAPI condition) {
        try {
            var plugin = condition.getPlugin();
            return plugin != null && !plugin.showIcon();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
