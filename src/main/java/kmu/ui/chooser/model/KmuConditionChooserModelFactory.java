package kmu.ui.chooser.model;

import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
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

        return new KmuConditionChooserModel(
                specs.stream()
                        .map(spec -> toEntry(spec, currentConditionIds, market))
                        .collect(Collectors.toUnmodifiableList()),
                locationFor(market));
    }

    private KmuConditionChooserEntry toEntry(
            KmuConditionSpec spec,
            Set<String> currentConditionIds,
            KmuEditableMarket market) {
        KmuConditionChooserEntryState state = currentConditionIds.contains(spec.getId())
                ? KmuConditionChooserEntryState.PRESENT
                : KmuConditionChooserEntryState.ABSENT;
        Optional<MarketConditionAPI> liveCondition = liveConditionFor(spec, state, market);
        return new KmuConditionChooserEntry(
                spec.getId(),
                spec.getName(),
                iconFor(spec, liveCondition),
                state,
                tooltipText(spec, state),
                spec.getSourceModName(),
                tooltipRendererFor(liveCondition).orElse(null),
                isSuppressed(spec, state, market),
                liveCondition.map(this::isHidden).orElse(false));
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

    private Optional<KmuConditionTooltipRenderer> tooltipRendererFor(Optional<MarketConditionAPI> liveCondition) {
        return liveCondition
                .map(StarsectorConditionTooltipRenderer::new);
    }

    private String iconFor(
            KmuConditionSpec spec,
            Optional<MarketConditionAPI> liveCondition) {
        return liveCondition
                .map(this::liveIconName)
                .filter(icon -> icon != null && !icon.trim().isEmpty())
                .orElse(spec.getIcon());
    }

    private boolean isSuppressed(
            KmuConditionSpec spec,
            KmuConditionChooserEntryState state,
            KmuEditableMarket market) {
        if (state != KmuConditionChooserEntryState.PRESENT) {
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

    private boolean isHidden(MarketConditionAPI condition) {
        try {
            MarketConditionPlugin plugin = condition.getPlugin();
            return plugin != null && !plugin.showIcon();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private KmuConditionChooserLocation locationFor(KmuEditableMarket market) {
        if (!(market instanceof StarsectorEditableMarket)) {
            return KmuConditionChooserLocation.unknown();
        }

        MarketAPI starsectorMarket = ((StarsectorEditableMarket) market).getMarket();
        return new KmuConditionChooserLocation(
                marketName(starsectorMarket),
                starSystemName(starsectorMarket),
                constellationName(starsectorMarket));
    }

    private String marketName(MarketAPI market) {
        String marketName = safeString(market::getName);
        if (marketName != null) {
            return marketName;
        }
        return KmuConditionChooserLocation.unknown().getMarketName();
    }

    private String starSystemName(MarketAPI market) {
        StarSystemAPI system = safeValue(market::getStarSystem);
        String systemName = system == null ? null : safeString(system::getNameWithTypeShort);
        if (systemName == null && system != null) {
            systemName = safeString(system::getName);
        }
        if (systemName != null) {
            return systemName;
        }

        LocationAPI location = safeValue(market::getContainingLocation);
        String locationName = location == null ? null : safeString(location::getNameWithTypeShort);
        if (locationName == null && location != null) {
            locationName = safeString(location::getName);
        }
        return locationName == null
                ? KmuConditionChooserLocation.unknown().getStarSystemName()
                : locationName;
    }

    private String constellationName(MarketAPI market) {
        StarSystemAPI system = safeValue(market::getStarSystem);
        Constellation constellation = system == null ? null : safeValue(system::getConstellation);
        if (constellation == null) {
            LocationAPI location = safeValue(market::getContainingLocation);
            constellation = location == null ? null : safeValue(location::getConstellation);
        }

        String constellationName = constellation == null ? null : safeString(constellation::getNameWithType);
        if (constellationName == null && constellation != null) {
            constellationName = safeString(constellation::getName);
        }
        return constellationName == null
                ? KmuConditionChooserLocation.unknown().getConstellationName()
                : constellationName;
    }

    private <T> T safeValue(SupplierWithRuntimeException<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String safeString(SupplierWithRuntimeException<String> supplier) {
        String value = safeValue(supplier);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @FunctionalInterface
    private interface SupplierWithRuntimeException<T> {
        T get();
    }
}
