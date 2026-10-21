package kmu.conditions.domain;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

import java.util.Optional;
import java.util.Set;

public interface KmuEditableMarket {
    Set<String> getConditionIds();

    boolean hasCondition(String conditionId);

    default boolean isConditionSuppressed(String conditionId) {
        return false;
    }

    /**
     * @return the game market this one edits, which the picker reads its location from. Empty for a
     *         market with no game market behind it
     */
    Optional<MarketAPI> findStarsectorMarket();

    /**
     * @return the game's live condition for this ID, whose plugin draws the condition's icon and
     *         tooltip. Empty where the market does not hold it or has no game market behind it
     */
    Optional<MarketConditionAPI> findCondition(String conditionId);

    void addCondition(String conditionId);

    void markConditionSurveyed(String conditionId);

    void reapplyConditions();
}
