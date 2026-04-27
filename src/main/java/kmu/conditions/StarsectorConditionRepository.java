package kmu.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class StarsectorConditionRepository implements KmuConditionRepository {
    private final SettingsAPI settings;

    public StarsectorConditionRepository() {
        this(Global.getSettings());
    }

    public StarsectorConditionRepository(SettingsAPI settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public List<KmuConditionSpec> getAllConditionSpecs() {
        List<MarketConditionSpecAPI> specs = settings.getAllMarketConditionSpecs();
        if (specs == null) {
            return Collections.emptyList();
        }
        return specs.stream()
                .filter(Objects::nonNull)
                .map(this::toKmuSpecOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
        if (conditionId == null || conditionId.trim().isEmpty()) {
            return Optional.empty();
        }

        MarketConditionSpecAPI spec = settings.getMarketConditionSpec(conditionId.trim());
        if (spec == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toKmuSpecOrNull(spec));
    }

    private KmuConditionSpec toKmuSpecOrNull(MarketConditionSpecAPI spec) {
        try {
            return new KmuConditionSpec(
                    spec.getId(),
                    spec.getName(),
                    spec.getIcon(),
                    spec.isPlanetary());
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
