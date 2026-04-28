package kmu.ui.chooser;

import kmu.conditions.KmuConditionService;
import kmu.conditions.KmuConditionSpec;
import kmu.conditions.KmuEditableMarket;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class KmuConditionChooserModelFactory {
    private final KmuConditionService conditionService;

    public KmuConditionChooserModelFactory(KmuConditionService conditionService) {
        this.conditionService = Objects.requireNonNull(conditionService, "conditionService");
    }

    public KmuConditionChooserModel create(KmuEditableMarket market) {
        Objects.requireNonNull(market, "market");
        List<KmuConditionSpec> specs = conditionService.listPlanetaryConditionSpecs();
        Set<String> currentConditionIds = conditionService.getCurrentConditionIds(market);

        return new KmuConditionChooserModel(specs.stream()
                .map(spec -> toEntry(spec, currentConditionIds))
                .collect(Collectors.toUnmodifiableList()));
    }

    private KmuConditionChooserEntry toEntry(
            KmuConditionSpec spec,
            Set<String> currentConditionIds) {
        KmuConditionChooserEntryState state = currentConditionIds.contains(spec.getId())
                ? KmuConditionChooserEntryState.PRESENT
                : KmuConditionChooserEntryState.ABSENT;
        return new KmuConditionChooserEntry(
                spec.getId(),
                spec.getName(),
                spec.getIcon(),
                state,
                tooltipText(spec, state));
    }

    private String tooltipText(KmuConditionSpec spec, KmuConditionChooserEntryState state) {
        if (state == KmuConditionChooserEntryState.PRESENT) {
            return "This condition is already present. Starsector can show the live condition tooltip.";
        }
        return "This condition is absent. Showing spec data only until the condition exists on the market.";
    }
}
