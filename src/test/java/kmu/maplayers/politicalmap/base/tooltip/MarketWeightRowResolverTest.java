package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.StationFactor;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.settings.HiddenMarketScalingChoice;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which lines a colony breaks down into and what hangs beneath what: the colonies ranked under
 * their bloc, the factors under the colony they moved, and the tiers under the garrison they are part
 * of.
 *
 * <p>What a factor that did not run looks like is most of what is asserted here, because it is the
 * difference between an account and a form: a switched-off factor has no line at all, rather than a
 * line insisting it counted for nothing.
 *
 * <p>How a line's numbers read is stood apart from and pinned by {@link MarketFactorTextTest}; the
 * values asserted below are read only where the case is about which line carries which.
 */
final class MarketWeightRowResolverTest {

    // A plain colony's parts: size four at full worth, no station, no garrison. The baseline the
    // cases below add one factor at a time to.
    private static final BaseSizeFactor PLAIN_SIZE = new BaseSizeFactor(4, 4.0, 4.0, 0.0);

    private static final double FULL_STABILITY = 10.0;

    @BeforeEach
    void installStrings() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveMarketRows {

        @Test
        void resolveMarketRowsRanksTheStrongestColonyFirst() {
            // The colonies read strongest first for the same reason the blocs above them do: the
            // account of a score opens on what most of it came from.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(
                    buildBreakdown("Culann", new BaseSizeFactor(3, 3.0, 3.0, 0.0)),
                    buildBreakdown("Jangala", new BaseSizeFactor(6, 6.0, 6.0, 0.0))),
                buildRules());

            assertThat(readLabels(rows))
                .containsExactly("Jangala", "Culann");
        }

        @Test
        void resolveMarketRowsStatesTheColonysOwnWeightBesideIt() {
            // The colony's line carries the number its factors below add up to, so the account can be
            // checked one level at a time rather than only at the bloc.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                buildRules());

            assertThat(rows.get(0).line().valueText())
                .isEqualTo("4,000");
        }

        @Test
        void resolveMarketRowsOpensAColonyOnTheStabilityBehindItsCuts() {
            // Stability heads the factors because it is the cause of every cut beneath it; read after
            // them it would explain deductions the reader has already passed.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                buildRules());

            assertThat(readLabels(rows.get(0).children()))
                .containsExactly("Stability", "Size");
        }

        @Test
        void resolveMarketRowsDropsTheStabilityLineWhenStabilityWeighsNothing() {
            // With the master weighting off stability moves no factor, so a line for it would state a
            // cause of cuts that are all zero.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                new DominanceRules(
                    false,
                    buildBaseSizeWeighting(HiddenMarketScalingChoice.NORMAL),
                    buildStationWeighting(),
                    buildPatrolWeighting()));

            assertThat(readLabels(rows.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveMarketRowsCallsOutAHiddenColonyOnItsSizeLine() {
            // Being hidden is not a further factor but the reason two of them rate the colony as they
            // do, so it is said on the line it changes rather than on one of its own.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(new MarketWeightBreakdown(
                    "Selkie Station",
                    true,
                    FULL_STABILITY,
                    PLAIN_SIZE,
                    Optional.empty(),
                    Optional.empty())),
                buildRules());

            assertThat(readSizeLine(rows).qualifierText())
                .isEqualTo("hidden");
        }

        @Test
        void resolveMarketRowsMarksAFixedRatingAsNotTheColonysSize() {
            // Under fixed scaling the rating is a token the player pinned hidden colonies to, and an
            // unmarked one reads as a size this colony has.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(new MarketWeightBreakdown(
                    "Selkie Station",
                    true,
                    FULL_STABILITY,
                    new BaseSizeFactor(7, 2.5, 2.5, 0.0),
                    Optional.empty(),
                    Optional.empty())),
                new DominanceRules(
                    true,
                    buildBaseSizeWeighting(HiddenMarketScalingChoice.FIXED),
                    buildStationWeighting(),
                    buildPatrolWeighting()));

            assertThat(readSizeLine(rows).valueText())
                .isEqualTo("2.5 (fixed) :: 2,500");
        }

        @Test
        void resolveMarketRowsNamesTheStationThatEarnedTheBonus() {
            // The station's own name is what ties the number to something the player can find on the
            // map, which a line reading "Station" would not.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildStationedBreakdown("Fort Ludd")),
                buildRules());

            assertThat(readLabels(rows.get(0).children()))
                .containsExactly("Stability", "Size", "Fort Ludd");
        }

        @Test
        void resolveMarketRowsListsAGarrisonsFieldedTiersBeneathIt() {
            // A garrison of one heavy patrol and one of four light ones can be worth the same and are
            // not the same garrison, so the tiers are what make the total explicable.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildGarrisonedBreakdown(2, 1, 0)),
                buildRules());

            var patrolEntry = rows.get(0).children().get(2);

            assertThat(patrolEntry.line().labelText())
                .isEqualTo("Patrols");
            assertThat(readLabels(patrolEntry.children()))
                .containsExactly("Small: 2", "Medium: 1");
        }

        @Test
        void resolveMarketRowsListsNoTierTheColonyFieldsNoneOf() {
            // A tier line for patrols that do not exist states a garrison the colony does not have.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildGarrisonedBreakdown(0, 0, 3)),
                buildRules());

            assertThat(readLabels(rows.get(0).children().get(2).children()))
                .containsExactly("Large: 3");
        }

        @Test
        void resolveMarketRowsGivesAFactorThatNeverRanNoLine() {
            // The station and patrol factors are absent from the parts when the player has them off,
            // and a colony explained by lines for both would say they counted for nothing instead.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                buildRules());

            assertThat(rows.get(0).children())
                .hasSize(2);
        }

        @Test
        void resolveMarketRowsListsNothingForABlocHoldingNoColony() {
            assertThat(MarketWeightRowResolver.resolveMarketRows(List.of(), buildRules()))
                .isEmpty();
        }
    }

    // The size line of the sole colony's parts, which every hidden-colony case reads.
    private static CellTooltipEntryLine readSizeLine(List<CellTooltipEntry> rows) {
        return rows.get(0).children().get(1).line();
    }

    private static List<String> readLabels(List<CellTooltipEntry> entries) {
        return entries
            .stream()
            .map(entry -> entry.line().labelText())
            .toList();
    }

    // A plain colony's parts - no station, no garrison - at full stability.
    private static MarketWeightBreakdown buildBreakdown(String marketName, BaseSizeFactor baseSize) {
        return new MarketWeightBreakdown(
            marketName,
            false,
            FULL_STABILITY,
            baseSize,
            Optional.empty(),
            Optional.empty());
    }

    // A colony whose station factor ran, named for the case asserting the line names it.
    private static MarketWeightBreakdown buildStationedBreakdown(String stationName) {
        return new MarketWeightBreakdown(
            "Jangala",
            false,
            FULL_STABILITY,
            PLAIN_SIZE,
            Optional.of(new StationFactor(stationName, 3.0, 0.0, 0.0, 3.0)),
            Optional.empty());
    }

    // A colony whose patrol factor ran, fielding the given tiers. The tier weights are the settings'
    // own defaults, so a case reads the counts it set rather than arithmetic of its own.
    private static MarketWeightBreakdown buildGarrisonedBreakdown(int small, int medium, int large) {
        return new MarketWeightBreakdown(
            "Jangala",
            false,
            FULL_STABILITY,
            PLAIN_SIZE,
            Optional.empty(),
            Optional.of(new PatrolFactor(
                new PatrolTierFactor(small, 0.25, small * 0.25),
                new PatrolTierFactor(medium, 0.5, medium * 0.5),
                new PatrolTierFactor(large, 1.0, large * 1.0),
                0.0)));
    }

    // The suite's default rule: stability weighed, hidden colonies counting by their real size. The
    // two optional factors' toggles never reach this resolver - whether they ran is already answered
    // by the parts it is handed - so they stand at the settings' defaults.
    private static DominanceRules buildRules() {
        return new DominanceRules(
            true,
            buildBaseSizeWeighting(HiddenMarketScalingChoice.NORMAL),
            buildStationWeighting(),
            buildPatrolWeighting());
    }

    private static BaseSizeWeighting buildBaseSizeWeighting(HiddenMarketScalingChoice scaling) {
        return new BaseSizeWeighting(1.0, scaling, 2.5, 1.0);
    }

    private static StationWeighting buildStationWeighting() {
        return new StationWeighting(true, 3.0, 0.5, 0.5);
    }

    private static PatrolWeighting buildPatrolWeighting() {
        return new PatrolWeighting(true, 0.25, 0.5, 1.0, 0.5);
    }
}
