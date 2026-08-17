package kmu.maplayers.politicalmap.base.ribbon;

import kmlib.starsector.factions.FactionPalette;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.DIKTAT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.DIKTAT_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.DIKTAT_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.NO_PAINTER;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_DARK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the segment rule on hand-built presences: which cells get a band at all, and what the
 * runs inside one look like.
 *
 * <p>Two things carry the whole design and are stated here on literals. The presence gate is
 * checked from both sides on each of its arms: a band wherever some bloc other than the painter
 * holds something, the decree case where the painter holds nothing itself and a single other bloc
 * is enough, and a lone holder's cell banding only where the uncontested cells are admitted - at
 * the shortened runs that admission carries, and never taking those runs into a contested cell.
 * The contest is stated a second time over a cell no fill covers, where there is no painter to be
 * a rival of and a second bloc holding something is what makes one; those cases are read off the
 * run lengths, which is the only place the arm a cell took is visible. The partings - a dark one
 * between two markets of one bloc, and one more in the outgoing bloc's shade wherever another
 * bloc's run follows - are checked on a run long enough to have an inside, across a handover, and
 * at the end of a band, which is the one boundary that stays open.
 *
 * <p>Every case names its blocs in a deliberate order and expects that same order out, since
 * the rule ranks nothing itself and a case that happened to be sorted would hide it.
 */
final class RibbonPlanTest {

    // The design's own proportions: a market three widths long, parted by one width. Stated here
    // rather than taken from the fixtures, which withhold their widths so that an edit to the
    // proportions fails every suite reading a band back instead of moving with them.
    private static final RibbonSegmentLengths STANDARD_LENGTHS = new RibbonSegmentLengths(3, 1);

    // The uncontested cells left bare, which is what every case about the first arm of the gate
    // is posed under: with them admitted, a single-holder cell bands and the case would be
    // stating the second arm's answer instead.
    private static final UncontestedCellBands NO_UNCONTESTED_BANDS =
        new UncontestedCellBands(false, false);

    // The rules most cases are posed under: the standard proportions, uncontested cells bare.
    private static final RibbonPlanRules STANDARD_RULES =
        new RibbonPlanRules(STANDARD_LENGTHS, NO_UNCONTESTED_BANDS);

    // The uncontested cells admitted, at the two run lengths that tell the second knob's two
    // answers apart: the authored run, and the tally of single widths.
    private static final RibbonPlanRules RULES_ADMITTING_UNCONTESTED_AT_FULL_RUNS =
        new RibbonPlanRules(STANDARD_LENGTHS, new UncontestedCellBands(true, false));
    private static final RibbonPlanRules RULES_ADMITTING_UNCONTESTED_AT_SHORT_RUNS =
        new RibbonPlanRules(STANDARD_LENGTHS, new UncontestedCellBands(true, true));

    @Nested
    class PlanCellRibbon {

        @Test
        void drawsNoRibbonInACellNoBlocIsPresentIn() {
            // A decreed system nobody holds anything in: the decree painted the fill, and
            // there is no presence anywhere for a band to report.
            assertThat(RibbonPlan.planCellRibbon(
                    Optional.of(HEGEMONY),
                    List.of(),
                    STANDARD_RULES))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsNoRibbonWhereThePainterIsTheOnlyBlocPresent() {
            // The single-holder case the gate exists for: however many markets the painter
            // holds, a band would only repeat what its own fill already says.
            assertThat(RibbonPlan.planCellRibbon(
                    Optional.of(HEGEMONY),
                    List.of(buildHegemonyPresence(4)),
                    STANDARD_RULES))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsNoRibbonWhereTheOnlyOtherBlocHoldsNothing() {
            // A bloc counted at nothing holds no market the score was decided on, so it is not
            // presence and cannot open a band on a cell that is otherwise single-holder.
            assertThat(RibbonPlan.planCellRibbon(
                    Optional.of(HEGEMONY),
                    List.of(buildHegemonyPresence(2), buildTriTachyonPresence(0)),
                    STANDARD_RULES))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsThePainterAloneWhereTheUncontestedCellsAreAdmitted() {
            // The second arm of the gate: nobody contests the system, and the band is there to
            // say how much is in it rather than whose it is - which its fill has already said.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(2)),
                RULES_ADMITTING_UNCONTESTED_AT_FULL_RUNS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void drawsEachMarketAtOneWidthOnAnAdmittedUncontestedCell() {
            // The shortening, which is what keeps a large lone holding from laying more band than
            // the contested cells the readout exists for. Only the market runs shorten: the
            // parting keeps its own length, or the ticks and the gaps between them would be
            // indistinguishable.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(3)),
                RULES_ADMITTING_UNCONTESTED_AT_SHORT_RUNS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 1),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 1),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 1));
        }

        @Test
        void keepsTheAuthoredRunLengthOnAContestedCellWhileTheShorteningIsOn() {
            // The shortening reaches only the cells the second arm admitted. A contested cell is
            // the first arm's, and the two arms answer different questions - so no knob under the
            // second one may change how a contest is drawn.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(1)),
                RULES_ADMITTING_UNCONTESTED_AT_SHORT_RUNS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsNoRibbonInAnEmptyCellEvenWhereTheUncontestedCellsAreAdmitted() {
            // A system held by decree alone: the second arm asks for the footprints of a lone
            // holder, and there is no holder here - so admitting the cell would lay a band of no
            // runs on a system holding nothing.
            assertThat(RibbonPlan.planCellRibbon(
                    Optional.of(HEGEMONY),
                    List.of(buildHegemonyPresence(0)),
                    RULES_ADMITTING_UNCONTESTED_AT_SHORT_RUNS))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void partsOneBlocsMarketsWithInterjectionsInItsDarkShade() {
            // Three markets of one bloc come out as three bright segments with two dark
            // partings strictly between them - the run itself neither opens nor closes on one,
            // and the only other parting here is the divider the bloc ahead of it is closed by.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(3)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void closesABlocsRunInItsOwnDarkShadeWhereAnotherBlocsRunFollows() {
            // Hegemony with three markets beside Tri-Tachyon with two. The divider between them
            // is Hegemony's dark shade, not Tri-Tachyon's: it closes the run it follows, which
            // is what makes it read as the Hegemony's holdings ending rather than as a gap
            // belonging to nobody.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(3), buildTriTachyonPresence(2)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void leavesTheLastBlocsRunOpen() {
            // Three blocs of one market each: a divider at each of the two handovers and none
            // after the last run, since a band ending on a divider would part its final bloc
            // from nothing at all.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(
                    buildHegemonyPresence(1),
                    buildTriTachyonPresence(1),
                    buildDiktatPresence(1)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(DIKTAT_BRIGHT, 3));
        }

        @Test
        void drawsNoPartingInsideABlocHoldingASingleMarket() {
            // A one-market bloc has no inside, so nothing parts it from itself - the smallest
            // rival mark the ribbon can make is one bright segment, and the only dark run in
            // this band is the divider between the two blocs.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(1)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsTheOnePresentBlocAloneWhereTheCellIsPaintedForAnAbsentOne() {
            // The decree case the gate is phrased for: the Diktat holds the system by decree
            // and nothing in it, so the band is Tri-Tachyon's run by itself.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(DIKTAT),
                List.of(buildTriTachyonPresence(2)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsNoRibbonForALoneBlocOnACellNoFillCovers() {
            // A system settled by one bloc that no layer paints for anybody. Nothing here is a
            // rival of anything, so the cell falls to the second arm - and with the uncontested
            // cells bare, that leaves it bare too.
            assertThat(RibbonPlan.planCellRibbon(
                    NO_PAINTER,
                    List.of(buildTriTachyonPresence(2)),
                    STANDARD_RULES))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void shortensALoneBlocsRunsOnACellNoFillCovers() {
            // The same cell with the uncontested cells admitted: the band is the second arm's, so
            // it draws at that arm's shortened runs. Read off the lengths rather than off the fact
            // of a band, since only they tell the two arms apart.
            var plan = RibbonPlan.planCellRibbon(
                NO_PAINTER,
                List.of(buildTriTachyonPresence(2)),
                RULES_ADMITTING_UNCONTESTED_AT_SHORT_RUNS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 1),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 1));
        }

        @Test
        void keepsTheAuthoredRunLengthForTwoBlocsOnACellNoFillCovers() {
            // Two blocs settled in a system no layer paints: with no fill naming either of them,
            // the second bloc is what makes the cell a contest. Posed with the shortening on, so
            // the authored lengths are what say the contested arm took it.
            var plan = RibbonPlan.planCellRibbon(
                NO_PAINTER,
                List.of(buildTriTachyonPresence(1), buildDiktatPresence(1)),
                RULES_ADMITTING_UNCONTESTED_AT_SHORT_RUNS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(DIKTAT_BRIGHT, 3));
        }

        @Test
        void drawsNoRibbonForALoneBlocBesideAnEmptyOneOnACellNoFillCovers() {
            // The same painterless cell with its second bloc counted at nothing. What makes such a
            // cell a contest is a second bloc holding something, not a second entry in the list -
            // so this is the lone holder's cell it looks like, and bare with the uncontested cells
            // left out.
            assertThat(RibbonPlan.planCellRibbon(
                    NO_PAINTER,
                    List.of(buildTriTachyonPresence(2), buildDiktatPresence(0)),
                    STANDARD_RULES))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void keepsTheBlocOrderItWasHandedRatherThanRankingThemItself() {
            // The painter is listed second and stays second: the caller ranked these, and a
            // ribbon that re-sorted could disagree with the fill about who leads the system.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildTriTachyonPresence(1), buildHegemonyPresence(1)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void leavesOutABlocHoldingNothingWhileDrawingTheRest() {
            // The Diktat is listed but holds nothing counted, so it takes no run; the band is
            // the painter's two markets and the one real rival, with no gap where it was. It
            // takes no divider either: the single dark run at the handover is the Hegemony's,
            // closing the run it actually follows.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(
                    buildHegemonyPresence(2),
                    buildDiktatPresence(0),
                    buildTriTachyonPresence(1)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void laysNoDividerAheadOfTheFirstBlocWithSomethingToDraw() {
            // The decree shape: the painting bloc is listed and holds nothing, so the first run
            // in the band is the second bloc's. A divider laid for the empty bloc would open the
            // band on a parting, which parts the first run from nothing at all.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(DIKTAT),
                List.of(
                    buildDiktatPresence(0),
                    buildHegemonyPresence(2),
                    buildTriTachyonPresence(1)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void laysNoDividerAfterTheLastBlocWithSomethingToDraw() {
            // A bloc ranked last on a weight it holds no markets behind still reaches the rule.
            // It draws no run, so the band has to end on Tri-Tachyon's segment: a divider for
            // the empty bloc would close the band on a parting belonging to a bloc that is not
            // in it.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(
                    buildHegemonyPresence(1),
                    buildTriTachyonPresence(1),
                    buildDiktatPresence(0)),
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void takesBothRunLengthsFromTheOnesItWasGiven() {
            // Neither length is the rule's own: a four-wide market parted by two reads as the
            // same holdings at the proportions the player set. The divider takes that same
            // parting length, so no knob can make one boundary say more than the other.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(2), buildTriTachyonPresence(1)),
                new RibbonPlanRules(
                    new RibbonSegmentLengths(4, 2),
                    NO_UNCONTESTED_BANDS));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 4),
                    new RibbonSegment(HEGEMONY_DARK, 2),
                    new RibbonSegment(HEGEMONY_BRIGHT, 4),
                    new RibbonSegment(HEGEMONY_DARK, 2),
                    new RibbonSegment(TRITACHYON_BRIGHT, 4));
        }
    }

    @Nested
    class SumLengthUnits {

        @Test
        void addsUpEveryRunItWasBuiltFrom() {

            var plan = new RibbonPlan(List.of(
                new RibbonSegment(HEGEMONY_BRIGHT, 3),
                new RibbonSegment(HEGEMONY_DARK, 1),
                new RibbonSegment(TRITACHYON_BRIGHT, 3)));

            assertThat(plan.sumLengthUnits())
                .isEqualTo(7);
        }

        @Test
        void addsUpEveryRunOfAPlannedRibbon() {
            // The two halves over one cell: five markets, three partings inside the two runs
            // and the divider between them, summed into the budget the drawn width is settled
            // against on a crowded cell.
            var plan = RibbonPlan.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(3), buildTriTachyonPresence(2)),
                STANDARD_RULES);

            assertThat(plan.sumLengthUnits())
                .isEqualTo(19);
        }

        @Test
        void addsUpToNothingForACellWithNoRibbon() {
            assertThat(RibbonPlan.NONE.sumLengthUnits())
                .isEqualTo(0);
        }
    }

    @Nested
    class Segments {

        @Test
        void staysAsPlannedWhenTheListItWasBuiltFromIsAddedToAfterwards() {
            // A plan is cached and read back over many frames, so it has to be a value rather
            // than a window onto whatever list built it - a caller reusing its builder must not
            // be able to lengthen a ribbon already planned.
            var source = new ArrayList<RibbonSegment>();

            source.add(new RibbonSegment(HEGEMONY_BRIGHT, 3));

            var plan = new RibbonPlan(source);
            
            source.add(new RibbonSegment(TRITACHYON_BRIGHT, 3));

            assertThat(plan.segments())
                .containsExactly(new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }
    }

    // The three blocs the cases are stated over, each with its own pair of shades so a run can
    // be told from its neighbour's and a bright segment from the parting inside it. Each names
    // only what makes it that bloc, leaving how many markets it holds the one thing a case says.
    //
    // Both the ids and the shades are the shared ones, since a bloc drawing one colour here and
    // another in the suites that read a band back through a palette would let two of them mean
    // different things by the same faction while all of them passed.
    private static BlocPresence buildHegemonyPresence(int marketCount) {
        return buildPresence(HEGEMONY, HEGEMONY_BRIGHT, HEGEMONY_DARK, marketCount);
    }

    private static BlocPresence buildTriTachyonPresence(int marketCount) {
        return buildPresence(TRITACHYON, TRITACHYON_BRIGHT, TRITACHYON_DARK, marketCount);
    }

    private static BlocPresence buildDiktatPresence(int marketCount) {
        return buildPresence(DIKTAT, DIKTAT_BRIGHT, DIKTAT_DARK, marketCount);
    }

    // The one shape every bloc above is built in, so a presence gaining a component is one edit
    // rather than three, and no bloc can drift into being stated differently from its neighbours.
    private static BlocPresence buildPresence(
            String blocId,
            Color brightShade,
            Color darkShade,
            int marketCount) {

        return new BlocPresence(
            blocId,
            new FactionPalette(brightShade, darkShade),
            marketCount);
    }
}
