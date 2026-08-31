package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.CellTooltipBody;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;
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

import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.buildEmptySector;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the depths the hover box's density knobs are bound to against the depths the political map
 * actually draws its lines at. Two knobs name a tier by number - the room under a line two steps under
 * the box's own voice, and under one three steps under it - and nothing else in the mod holds those
 * numbers to the listing they were chosen for.
 *
 * <p>The chain they are chosen against runs across three classes, none of which states a depth: the
 * colonies a faction is accounted for by ({@link MarketWeightRowResolver}), the entry tree those hang
 * in ({@link StandingRowResolver}), and the walk that turns nesting into subordination
 * ({@code CellTooltipBody}). A level added anywhere along it - a colony gathered under something,
 * an account hung a step lower - moves every factor line to a depth no knob names, and the sliders then
 * tighten runs that are not there while the runs that make a box tall keep the box's own spacing. Every
 * unit test on either side goes on passing, because each states only its own step.
 *
 * <p>Read off a lone faction, which is the arrangement that puts a colony closest to the box's own
 * voice: an alliance gathers its members as peers rather than as an account, so the depths below are
 * the shallowest the box ever draws these lines at. A knob bound to them cannot then be a step out on
 * the flat faction view and right on the alliances view.
 */
final class TooltipDensityDepthIntegrationTest {

    // The depths the two tier gaps are bound to, restated here rather than read from the box that
    // binds them - which holds them private, and whose copy would be edited alongside any change and
    // so could never fail. These are the numbers a player's slider moves.
    private static final int FACTOR_TIER = 2;
    private static final int FACTOR_DETAIL_TIER = 3;

    // Where the colony sits and whether it is concealed, neither of which changes how deep the box
    // goes. Named so the pair of booleans its parts open with can be read rather than counted off
    // against the record's own order.
    private static final boolean VISIBLE_COLONY = false;
    private static final boolean PLANET_COLONY = false;

    // The depths above the tiers, so the assertions below read as one descent rather than as two
    // numbers picked out of the middle of a box.
    private static final int FACTION_TIER = 0;
    private static final int COLONY_TIER = 1;

    // Where each line of the block lands, the heading being the block's own first line. One colony
    // fielding one patrol, so the descent is a single line per depth and an extra line anywhere shows
    // up as the wrong row being read rather than as a passing coincidence.
    private static final int FACTION_ROW = 1;
    private static final int COLONY_ROW = 2;
    private static final int SIZE_FACTOR_ROW = 3;
    private static final int PATROL_FACTOR_ROW = 4;
    private static final int PATROL_TIER_ROW = 5;

    // Every factor weighted so the colony breaks all the way down: size for the shallower tier and
    // patrols for the one below it, which is the only factor in the box that breaks down further.
    private static final DominanceRules WEIGHING_RULES = new DominanceRules(
        false,
        new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.NORMAL, 2.5, 1.0),
        new StationWeighting(false, 3.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    private static final String FACTION_ID = "hegemony";

    @BeforeEach
    void installColoursAndStrings() {
        CellTooltipPaletteFake.installPalette();
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearColoursAndStrings() {
        StarsectorSettingsFake.clearSettings();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class FactorLineDepths {

        @Test
        void factorLineDepthsPutAColonysTermsAtTheTierTheirGapIsBoundTo() {
            // The run that makes a hover box tall: a colony's size, stability, station and patrols all
            // land here, and the tier 2 slider is what packs them down.
            var rows = drawStandingsBlock();

            assertThat(readSubordinationLevel(rows, SIZE_FACTOR_ROW))
                .isEqualTo(FACTOR_TIER);
            assertThat(readSubordinationLevel(rows, PATROL_FACTOR_ROW))
                .isEqualTo(FACTOR_TIER);
        }

        @Test
        void factorLineDepthsPutAPatrolTierAtTheTierItsOwnGapIsBoundTo() {
            // The deepest run the box lists, and the one the tier 3 slider is set hardest against.
            var rows = drawStandingsBlock();

            assertThat(readSubordinationLevel(rows, PATROL_TIER_ROW))
                .isEqualTo(FACTOR_DETAIL_TIER);
        }

        @Test
        void factorLineDepthsLeaveTheLinesAboveTheTiersOnTheBoxsOwnSpacing() {
            // The other half of the binding: the faction and the colony are what the tightened runs
            // hang from, so they keep the box's own line gap. A knob reaching either of them would
            // close up the whole box while claiming to touch one run.
            var rows = drawStandingsBlock();

            assertThat(readSubordinationLevel(rows, FACTION_ROW))
                .isEqualTo(FACTION_TIER);
            assertThat(readSubordinationLevel(rows, COLONY_ROW))
                .isEqualTo(COLONY_TIER);
        }

        @Test
        void factorLineDepthsRunNoDeeperThanTheTiersTheKnobsName() {
            // What says the two knobs cover the whole listing rather than most of it: nothing the box
            // draws sits below the deepest tier either names. A line that did would inherit that
            // tier's gap, which is the sane answer but not one anybody chose for it.
            var rows = drawStandingsBlock();

            assertThat(rows.stream().map(TooltipRow::subordinationLevel).toList())
                .allMatch(level -> level <= FACTOR_DETAIL_TIER);
        }
    }

    // The block one faction's standing is drawn as, from the ranked standing down to the lines of its
    // account - the whole chain the depths above are a property of, with only the ranking itself and
    // the economy read left out, neither of which touches how deep a line sits.
    private static List<TooltipRow> drawStandingsBlock() {

        var sectorMock = buildEmptySector();

        stubFaction(sectorMock, FACTION_ID, "The Hegemony", "graphics/hegemony_crest.png");

        var entries = StandingRowResolver.resolveRows(
            sectorMock,
            List.of(RoutedStanding.routeWhole(new GroupStanding(
                FACTION_ID,
                7,
                List.of(new WeighedFactionStanding(FACTION_ID, 7))))),
            HolderGrouping.identity(),
            standing -> MarketWeightRowResolver.resolveMarketRows(
                List.of(buildPatrolledColony()),
                List.of(),
                WEIGHING_RULES,
                SystemColonyReading.NONE));

        var body = CellTooltipBody.openBody(PATROL_DETAILS);

        body.appendSection("Dominated by:", entries);

        return TooltipSection.readRowsInOrder(body.readSections());
    }

    // One colony fielding a single small patrol: enough to reach every depth the box has, and no more,
    // so each row index above names exactly one line.
    private static MarketWeightBreakdown buildPatrolledColony() {
        return new MarketWeightBreakdown(
            "jangala",
            EntityNameplate.createUnmarkedNameplate("Jangala"),
            VISIBLE_COLONY,
            PLANET_COLONY,
            FULL_STABILITY,
            new BaseSizeFactor(4, 4.0, 4.0, 0.0),
            Optional.empty(),
            Optional.of(new PatrolFactor(
                new PatrolTierFactor(1, 0.25, 0.25),
                new PatrolTierFactor(0, 0.5, 0.0),
                new PatrolTierFactor(0, 1.0, 0.0),
                0.0)));
    }

    private static int readSubordinationLevel(List<TooltipRow> rows, int rowIndex) {
        return rows.get(rowIndex).subordinationLevel();
    }
}
