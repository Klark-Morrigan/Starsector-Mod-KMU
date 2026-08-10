package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityMapIcon;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.StationFactor;
import kmu.maplayers.politicalmap.base.dominance.UnweighedColony;
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

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which lines a colony breaks down into and what hangs beneath what: the colonies ranked under
 * the faction holding them, the factors under the colony they moved, and the tiers under the patrol
 * factor they are part of.
 *
 * <p>What a factor that did not run looks like is most of what is asserted here, because it is the
 * difference between an account and a form: a switched-off factor has no line at all, rather than a
 * line insisting it counted for nothing.
 *
 * <p>A colony the pass never weighed is the other half: it is on the list at all, its nought reads as
 * the account's own statement rather than as a weight it lost on, and it sits below every colony that
 * was weighed - including one weighed at nought, which is the pair the ordering has to keep apart.
 *
 * <p>Where a mark may appear is pinned here too, since it is a statement about which line is about a
 * thing on the map: a colony's own line leads with the glyph the map marks it by, weighed or not, and
 * nothing beneath it carries one.
 *
 * <p>How a line's numbers read is stood apart from and pinned by {@link MarketFactorTextTest}; the
 * values asserted below are read only where the case is about which line carries which.
 */
final class MarketWeightRowResolverTest {

    // A plain colony's parts: size four at full worth, no station, no patrols. The baseline the
    // cases below add one factor at a time to.
    private static final BaseSizeFactor PLAIN_SIZE = new BaseSizeFactor(4, 4.0, 4.0, 0.0);

    // A faction every one of whose colonies here the economy lists, which is the ordinary system
    // and so every case bar the ones about the colonies it does not.
    private static final List<UnweighedColony> NO_UNWEIGHED_COLONIES = List.of();

    // The glyph the sector map marks a colony's entity with, and a colony whose entity carries none.
    // Vanilla draws a family of colonies from one sprite and tells the types apart by the colour, so
    // the pair travels together and a case reading one reads both.
    private static final Optional<EntityMapIcon> COLONY_ICON = Optional.of(
        new EntityMapIcon("graphics/warroom/icon_planet.png", new Color(120, 200, 90)));
        
    private static final Optional<EntityMapIcon> NO_ICON = Optional.empty();

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
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(readLabelTexts(rows))
                .containsExactly("Jangala", "Culann");
        }

        @Test
        void resolveMarketRowsBreaksATieByNameSoTheOrderNeverDependsOnTheEconomyWalk() {
            // Two colonies of a bloc can weigh exactly the same, and left to the order the economy
            // handed them over the box would list them one way on one hover and the other on the next.
            var evenSize = new BaseSizeFactor(4, 4.0, 4.0, 0.0);

            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(
                    buildBreakdown("Jangala", evenSize),
                    buildBreakdown("Culann", evenSize)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(readLabelTexts(rows))
                .containsExactly("Culann", "Jangala");
        }

        @Test
        void resolveMarketRowsStatesTheColonysOwnWeightBesideIt() {
            // The colony's line carries the number its factors below add up to, so the account can be
            // checked one level at a time rather than only at the bloc.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(rows.get(0).line().valueText())
                .isEqualTo("4,000");
        }

        @Test
        void resolveMarketRowsLeadsAColonyWithTheGlyphTheMapMarksItBy() {
            // The reader has a list of names and a map, and the glyph is the one thing the two share
            // at a glance. Its colour comes with it because vanilla draws a whole family of colonies
            // from one sprite and tells the types apart by nothing else.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildMarkedBreakdown(COLONY_ICON)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(rows.get(0).line().iconSpritePath())
                .isEqualTo("graphics/warroom/icon_planet.png");
            assertThat(rows.get(0).line().iconTintColour())
                .isEqualTo(new Color(120, 200, 90));
        }

        @Test
        void resolveMarketRowsOpensAColonyOnItsNameWhereTheMapMarksItWithNoGlyph() {
            // An entity carrying no authored icon hands the absence straight over, so the line is
            // built from its words rather than from an image run with nothing to load.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildMarkedBreakdown(NO_ICON)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(rows.get(0).line().hasMark())
                .isFalse();
        }

        @Test
        void resolveMarketRowsMarksNoLineBeneathAColony() {
            // A stability, a size or a patrol tier is a term of arithmetic with nothing on the map to
            // point at, so a glyph there would be standing in for a number.
            var factors = MarketWeightRowResolver
                .resolveMarketRows(
                    List.of(buildMarkedBreakdown(COLONY_ICON)),
                    NO_UNWEIGHED_COLONIES,
                    buildRules())
                .get(0)
                .children();

            assertThat(factors)
                .allSatisfy(factor -> assertThat(factor.line().hasMark()).isFalse());
            assertThat(factors.get(2).children())
                .allSatisfy(tier -> assertThat(tier.line().hasMark()).isFalse());
        }

        @Test
        void resolveMarketRowsOpensAColonyOnTheStabilityBehindItsCuts() {
            // Stability heads the factors because it is the cause of every cut beneath it; read after
            // them it would explain deductions the reader has already passed.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(readLabelTexts(rows.get(0).children()))
                .containsExactly("Stability", "Size");
        }

        @Test
        void resolveMarketRowsDropsTheStabilityLineWhenStabilityWeighsNothing() {
            // With the master weighting off stability moves no factor, so a line for it would state a
            // cause of cuts that are all zero.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                NO_UNWEIGHED_COLONIES,
                new DominanceRules(
                    false,
                    buildBaseSizeWeighting(HiddenMarketScalingChoice.NORMAL),
                    buildStationWeighting(),
                    buildPatrolWeighting()));

            assertThat(readLabelTexts(rows.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveMarketRowsCallsOutAHiddenColonyOnItsSizeLine() {
            // Being hidden is not a further factor but the reason two of them rate the colony as they
            // do, so it is said on the line it changes rather than on one of its own.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(new MarketWeightBreakdown(
                    "Selkie Station",
                    NO_ICON,
                    true,
                    FULL_STABILITY,
                    PLAIN_SIZE,
                    Optional.empty(),
                    Optional.empty())),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(readSizeLine(rows).qualifierText())
                .isEqualTo("hidden");
        }

        @Test
        void resolveMarketRowsMarksAFixedRatingAsNotTheColonysSize() {
            // Under fixed scaling the rating is a token the player pinned hidden colonies to, and an
            // unmarked one reads as a size this colony has.
            var rows = resolveFixedRatedHiddenMarketRows();

            assertThat(readSizeLine(rows).valueText())
                .isEqualTo("2.5 (fixed) :: 2,500");
        }

        @Test
        void resolveMarketRowsOpensAFixedRatingOnTheColonysRealSize() {
            // The token says what the colony counted as and nothing about how big it is, which is the
            // one case a player is most likely to read as the map miscounting a large secret base.
            var rows = resolveFixedRatedHiddenMarketRows();

            assertThat(readSizeLine(rows).valueWorkingText())
                .isEqualTo("7 ::");
        }

        @Test
        void resolveMarketRowsStatesNoRealSizeWhereTheColonyCountedByItsOwn() {
            // An openly held colony's rating is its size, so opening the line on it would state the
            // same number twice and imply a change that never happened.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(readSizeLine(rows).valueWorkingText())
                .isNull();
        }

        @Test
        void resolveMarketRowsNamesTheStationThatEarnedTheBonus() {
            // The station's own name is what ties the number to something the player can find on the
            // map, which a line reading "Station" would not.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildStationedBreakdown("Fort Ludd")),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(readLabelTexts(rows.get(0).children()))
                .containsExactly("Stability", "Size", "Fort Ludd");
        }

        @Test
        void resolveMarketRowsListsAColonysFieldedTiersBeneathThePatrolLine() {
            // One heavy patrol and four light ones can be worth the same and are not the same force
            // fielded, so the tiers are what make the total explicable.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildPatrollingBreakdown(2, 1, 0)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            var patrolEntry = rows.get(0).children().get(2);

            assertThat(patrolEntry.line().labelText())
                .isEqualTo("Patrols");
            assertThat(readLabelTexts(patrolEntry.children()))
                .containsExactly("Small: 2", "Medium: 1");
        }

        @Test
        void resolveMarketRowsStatesATiersRateApartFromWhatItCameTo() {
            // The two halves reach the box separately so it can draw the rate quieter than the total
            // it explains; run together they would read as one number with a stray separator in it.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildPatrollingBreakdown(2, 0, 0)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            var tierLine = rows.get(0).children().get(2).children().get(0).line();

            assertThat(tierLine.valueWorkingText())
                .isEqualTo("0.25 /");
            assertThat(tierLine.valueText())
                .isEqualTo("500");
        }

        @Test
        void resolveMarketRowsStatesThePatrolFactorsOwnValueWhole() {
            // Only the tiers split. The patrol line is the weight alone - there is no working
            // behind it worth drawing quieter, since a headcount summed over tiers that count for
            // different amounts explains nothing about the number beside it.
            var patrolLine = MarketWeightRowResolver
                .resolveMarketRows(
                    List.of(buildPatrollingBreakdown(2, 0, 0)),
                    NO_UNWEIGHED_COLONIES,
                    buildRules())
                .get(0)
                .children()
                .get(2)
                .line();

            assertThat(patrolLine.valueWorkingText())
                .isNull();
            assertThat(patrolLine.valueText())
                .isEqualTo("500");
        }

        @Test
        void resolveMarketRowsListsNoTierTheColonyFieldsNoneOf() {
            // A tier line for patrols that do not exist states a force the colony does not field.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildPatrollingBreakdown(0, 0, 3)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(readLabelTexts(rows.get(0).children().get(2).children()))
                .containsExactly("Large: 3");
        }

        @Test
        void resolveMarketRowsGivesAFactorThatNeverRanNoLine() {
            // The station and patrol factors are absent from the parts when the player has them off,
            // and a colony explained by lines for both would say they counted for nothing instead.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Jangala", PLAIN_SIZE)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(rows.get(0).children())
                .hasSize(2);
        }

        @Test
        void resolveMarketRowsListsNothingForABlocHoldingNoColony() {
            assertThat(MarketWeightRowResolver.resolveMarketRows(
                    List.of(),
                    NO_UNWEIGHED_COLONIES,
                    buildRules()))
                .isEmpty();
        }

        @Test
        void resolveMarketRowsNamesAColonyTheEconomyDoesNotListAtNought() {
            // The player can see the station on the map in the faction's colours, so an account
            // omitting it would withhold something they are looking straight at. Nought is what it
            // brought to the score - it is present, and it moved nothing.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(),
                List.of(new UnweighedColony("Galatia Academy", NO_ICON)),
                buildRules());

            assertThat(rows.get(0).line().labelText())
                .isEqualTo("Galatia Academy");
            assertThat(rows.get(0).line().valueText())
                .isEqualTo("0");
        }

        @Test
        void resolveMarketRowsCallsNothingOutBesideAnUnweighedColonysNought() {
            // The nought is the whole of what the account has to say about it, exactly as on the
            // claims side: a word for why it was passed over would raise a question about the rule
            // that the box would then owe an answer to.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(),
                List.of(new UnweighedColony("Galatia Academy", NO_ICON)),
                buildRules());

            assertThat(rows.get(0).line().qualifierText())
                .isNull();
        }

        @Test
        void resolveMarketRowsDrawsAnUnweighedColonysNoughtInTheQuietShade() {
            // The nought is the pass's statement about the colony rather than anything the colony
            // scored; in the list's own colour it would pass for a weight competed with and lost on.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(),
                List.of(new UnweighedColony("Galatia Academy", NO_ICON)),
                buildRules());

            assertThat(rows.get(0).line().isValueUncounted())
                .isTrue();
        }

        @Test
        void resolveMarketRowsBreaksAnUnweighedColonyDownIntoNoFactors() {
            // None of the three factors ran for it - there is nothing beneath the line to state, and
            // factor lines at nought would invite adding up to a total nobody computed.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(),
                List.of(new UnweighedColony("Galatia Academy", NO_ICON)),
                buildRules());

            assertThat(rows.get(0).children())
                .isEmpty();
        }

        @Test
        void resolveMarketRowsListsAnUnweighedColonyBelowEveryWeighedOne() {
            // Including one that weighed nothing: that colony was weighed and came to nought, which
            // is a different finding from one that was never weighed, and ranking them together by a
            // number only one of them earned would put the unweighed above it.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildBreakdown("Culann", new BaseSizeFactor(0, 0.0, 0.0, 1.0))),
                List.of(new UnweighedColony("Galatia Academy", NO_ICON)),
                buildRules());

            assertThat(readLabelTexts(rows))
                .containsExactly("Culann", "Galatia Academy");
        }

        @Test
        void resolveMarketRowsLeadsAColonyTheEconomyDoesNotListWithItsGlyphToo() {
            // The map's glyph is the only trace of such a colony beside its name - no score above
            // accounts for it - so the line the reader has most trouble placing is the last one that
            // should be left without it.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(),
                List.of(new UnweighedColony("Galatia Academy", COLONY_ICON)),
                buildRules());

            assertThat(rows.get(0).line().iconSpritePath())
                .isEqualTo("graphics/warroom/icon_planet.png");
            assertThat(rows.get(0).line().iconTintColour())
                .isEqualTo(new Color(120, 200, 90));
        }

        @Test
        void resolveMarketRowsRanksUnweighedColoniesByName() {
            // They have no weight to be ranked by, so they take the rule the weighed ones fall back
            // on at a tie - one order down the whole list rather than two.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(),
                List.of(
                    new UnweighedColony("Tibicena", NO_ICON),
                    new UnweighedColony("Galatia Academy", NO_ICON)),
                buildRules());

            assertThat(readLabelTexts(rows))
                .containsExactly("Galatia Academy", "Tibicena");
        }

        @Test
        void resolveMarketRowsStatesNoListingPlaceOnAnyLine() {
            // The shared line vocabulary can state where something falls in an ordering, and this box
            // has no use for one: dominance is settled by weight and distance, with no tie rule a
            // listing order could explain. A place stated here would be a number meaning nothing.
            var rows = MarketWeightRowResolver.resolveMarketRows(
                List.of(buildPatrollingBreakdown(2, 1, 0)),
                NO_UNWEIGHED_COLONIES,
                buildRules());

            assertThat(rows.get(0).line().indexPlace())
                .isNull();
            assertThat(rows.get(0).children())
                .allSatisfy(factor -> assertThat(factor.line().indexPlace()).isNull());
        }
    }

    // The size line of the sole colony's parts, which every hidden-colony case reads.
    private static CellTooltipEntryLine readSizeLine(List<CellTooltipEntry> rows) {
        return rows.get(0).children().get(1).line();
    }

    // A plain colony's parts - no station, no patrols - at full stability.
    private static MarketWeightBreakdown buildBreakdown(String marketName, BaseSizeFactor baseSize) {
        return new MarketWeightBreakdown(
            marketName,
            NO_ICON,
            false,
            FULL_STABILITY,
            baseSize,
            Optional.empty(),
            Optional.empty());
    }

    // A colony the map marks with the given glyph, fielding one small patrol so its account runs two
    // levels deep - a factor line and a tier line beneath it. Both levels are what the cases about
    // where a mark may appear have to read, the rule being that only the colony's own line takes one.
    private static MarketWeightBreakdown buildMarkedBreakdown(Optional<EntityMapIcon> marketIcon) {
        return new MarketWeightBreakdown(
            "Jangala",
            marketIcon,
            false,
            FULL_STABILITY,
            PLAIN_SIZE,
            Optional.empty(),
            Optional.of(new PatrolFactor(
                new PatrolTierFactor(1, 0.25, 0.25),
                new PatrolTierFactor(0, 0.5, 0.0),
                new PatrolTierFactor(0, 1.0, 0.0),
                0.0)));
    }

    // A hidden colony under Fixed scaling: the one case where the size that entered the weight and the
    // size the colony actually is part company, which is what both halves of its size line are read
    // from. Built here rather than at each case, since neither is about how the colony was composed.
    private static List<CellTooltipEntry> resolveFixedRatedHiddenMarketRows() {
        return MarketWeightRowResolver.resolveMarketRows(
            List.of(new MarketWeightBreakdown(
                "Selkie Station",
                NO_ICON,
                true,
                FULL_STABILITY,
                new BaseSizeFactor(7, 2.5, 2.5, 0.0),
                Optional.empty(),
                Optional.empty())),
            NO_UNWEIGHED_COLONIES,
            new DominanceRules(
                true,
                buildBaseSizeWeighting(HiddenMarketScalingChoice.FIXED),
                buildStationWeighting(),
                buildPatrolWeighting()));
    }

    // A colony whose station factor ran, named for the case asserting the line names it.
    private static MarketWeightBreakdown buildStationedBreakdown(String stationName) {
        return new MarketWeightBreakdown(
            "Jangala",
            NO_ICON,
            false,
            FULL_STABILITY,
            PLAIN_SIZE,
            Optional.of(new StationFactor(stationName, 3.0, 0.0, 0.0, 3.0)),
            Optional.empty());
    }

    // A colony whose patrol factor ran, fielding the given tiers. The tier weights are the settings'
    // own defaults, so a case reads the counts it set rather than arithmetic of its own.
    private static MarketWeightBreakdown buildPatrollingBreakdown(int small, int medium, int large) {
        return new MarketWeightBreakdown(
            "Jangala",
            NO_ICON,
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
