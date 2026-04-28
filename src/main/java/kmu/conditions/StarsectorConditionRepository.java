package kmu.conditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class StarsectorConditionRepository implements KmuConditionRepository {
    static final String VANILLA_SOURCE_NAME = "Starsector";

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
                    spec.getDesc(),
                    sourceModName(spec),
                    spec.isPlanetary());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String sourceModName(MarketConditionSpecAPI spec) {
        ModSpecAPI sourceMod = spec.getSourceMod();
        if (sourceMod == null) {
            return VANILLA_SOURCE_NAME;
        }
        String sourceModName = sourceMod.getName();
        if (sourceModName == null || sourceModName.trim().isEmpty()) {
            return VANILLA_SOURCE_NAME;
        }
        return sourceModName;
    }
}
