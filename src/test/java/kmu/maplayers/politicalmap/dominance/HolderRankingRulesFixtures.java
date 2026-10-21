package kmu.maplayers.politicalmap.dominance;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Ranking rules for suites that pose hand-built footprints, with no system geometry to settle a tie
 * by. A dead heat falls to the lowest ID, so map iteration order never decides a winner.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class HolderRankingRulesFixtures {

    /**
     * Nobody barred and a dead heat to the lowest ID - the ranking of a case about weights rather
     * than about who is allowed to win.
     */
    public static final HolderRankingRules OPEN_CONTEST_BY_LOWEST_ID =
        createByLowestId(blocId -> true);

    private HolderRankingRulesFixtures() {
        // fixture of static wiring, no instances.
    }

    /**
     * Rules breaking a dead heat by lowest ID under the given bar.
     *
     * @param candidacy which IDs may win the system
     * @return those rules
     */
    public static HolderRankingRules createByLowestId(Predicate<String> candidacy) {
        return new HolderRankingRules(Comparator.naturalOrder(), candidacy);
    }
}
