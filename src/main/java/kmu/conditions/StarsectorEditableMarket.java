package kmu.conditions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class StarsectorEditableMarket implements KmuEditableMarket {
    private final MarketAPI market;

    public StarsectorEditableMarket(MarketAPI market) {
        this.market = Objects.requireNonNull(market, "market");
    }

    @Override
    public Set<String> getConditionIds() {
        List<MarketConditionAPI> conditions = market.getConditions();
        if (conditions == null) {
            return new LinkedHashSet<>();
        }
        return conditions.stream()
                .filter(Objects::nonNull)
                .map(MarketConditionAPI::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public boolean hasCondition(String conditionId) {
        return market.hasCondition(conditionId);
    }

    @Override
    public void addCondition(String conditionId) {
        market.addCondition(conditionId);
    }

    @Override
    public void markConditionSurveyed(String conditionId) {
        MarketConditionAPI condition = market.getFirstCondition(conditionId);
        if (condition != null) {
            condition.setSurveyed(true);
        }
    }

    @Override
    public void reapplyConditions() {
        market.reapplyConditions();
    }
}
