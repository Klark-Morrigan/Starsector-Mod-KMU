package kmu.maplayers.ownermap.ribbon;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.RibbonSegment;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.ALLIED_HEGEMONY_AND_TRITACHYON;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.DIKTAT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.DIKTAT_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.DIKTAT_DARK;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY_DARK;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.NO_PAINTER;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.SHORTENED_UNCONTESTED_RULES;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.STANDARD_RULES;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON_DARK;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the segment rule on hand-built presences: which cells get a band at all, what length its
 * runs are laid at, and what the runs inside one look like.
 *
 * <p>Two things carry the whole design and are stated here on literals. Which reading a cell falls
 * under is checked from both sides: the authored lengths wherever some owner other than the painter
 * holds something, the absent-painter case where the painter holds nothing itself and a single other owner
 * is enough, and a lone owner's cell banding at the shortened runs while a contested one beside it
 * keeps the authored ones under that same shortening. The contest is stated a second time over a
 * cell no fill covers, where there is no painter to be a rival of and a second owner holding
 * something is what makes one; those cases are read off the run lengths, which is the only place
 * the reading a cell took is visible. The partings - a dark one between two stakes of one owner,
 * and one more in the outgoing owner's shade wherever another owner's run follows - are checked on a
 * run long enough to have an inside, across a handover, and at the end of a band, which is the one
 * boundary that stays open.
 *
 * <p>Both readings are then stated a third time over an affiliation, since who counts as a rival
 * is the one thing an affiliation moves: an owner standing with the painter, and a pair standing
 * together on a cell no fill covers, fall to the shortened runs, and an outsider beside either of
 * them puts the cell back on the authored ones. A cell no fill covers is stated once more with an
 * owner listed ahead of the rest holding nothing, since who such a cell is judged against is only
 * visible where standing together changes the answer. Every other case here is posed under
 * {@link OwnerAffiliation#NONE}, which is both a layer where nobody stands together and the
 * statement that nothing about a band changed for it.
 *
 * <p>Every case names its owners in a deliberate order and expects that same order out, since
 * the rule ranks nothing itself and a case that happened to be sorted would hide it.
 */
final class OwnerRibbonRunsTests {

    // The shortening switched off, for the one case laying its own lengths on a contested cell,
    // where the shortening never reaches.
    private static final boolean UNCONTESTED_RUNS_AUTHORED = false;

    @Nested
    class PlanCellRibbon {

        @Test
        void drawsNoRibbonInACellNoOwnerIsPresentIn() {
            // A painted system nobody holds anything in: the mechanic painted the fill regardless,
            // and there is no presence anywhere for a band to report.
            assertThat(OwnerRibbonRuns.planCellRibbon(
                    Optional.of(HEGEMONY),
                    List.of(),
                    OwnerAffiliation.NONE,
                    STANDARD_RULES))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsThePainterAloneWhereItIsTheOnlyOwnerPresent() {
            // The single-owner cell: nobody contests the system, and the band is there to say
            // how much is in it rather than whose it is - which its fill has already said.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(2)),
                OwnerAffiliation.NONE,
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void leavesAnOwnerHoldingNothingOutOfAnOtherwiseSingleOwnerCell() {
            // An owner counted at nothing holds no stake the score was decided on, so it is not
            // presence: it takes no run of its own, and the cell stays the painter's uncontested
            // one rather than becoming a contest.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(2), buildTriTachyonPresence(0)),
                OwnerAffiliation.NONE,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 1),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 1));
        }

        @Test
        void drawsEachStakeAtOneWidthOnAnUncontestedCell() {
            // The shortening, which is what keeps a large lone holding from laying more band than
            // the contested cells the readout exists for. Only the stake runs shorten: the
            // parting keeps its own length, or the ticks and the gaps between them would be
            // indistinguishable.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(3)),
                OwnerAffiliation.NONE,
                SHORTENED_UNCONTESTED_RULES);

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
            // The shortening reaches only the cells nobody contests. A contest is the readout the
            // bands exist for, so no knob under the uncontested reading may change how one is
            // drawn.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(1)),
                OwnerAffiliation.NONE,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void shortensTheRunsWhereTheOnlyOtherOwnerStandsWithThePainter() {
            // The same cell as the contested one above, under an affiliation that stands its two
            // owners together: they hold the system between them rather than fighting over it, so
            // the band is a tally of that joint footprint. Both keep their own run in their own
            // colours - what standing together changes is the length, never the fold.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(1)),
                ALLIED_HEGEMONY_AND_TRITACHYON,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 1),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 1));
        }

        @Test
        void keepsTheAuthoredRunLengthWhereARivalStandsBesideThePainterAndItsAlly() {
            // An ally does not make a cell uncontested - it only stops being a rival itself. The
            // Diktat stands with nobody here, so the cell is the contest it looks like and
            // keeps the authored lengths even under the shortening.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(
                    buildHegemonyPresence(1),
                    buildTriTachyonPresence(1),
                    buildDiktatPresence(1)),
                ALLIED_HEGEMONY_AND_TRITACHYON,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(DIKTAT_BRIGHT, 3));
        }

        @Test
        void drawsNoRibbonInACellPaintedForAnOwnerHoldingNothing() {
            // A system its painting owner holds nothing in: the uncontested reading reports the size
            // of a footprint, and there is none here - so banding the cell would lay a band of no
            // runs on a system holding nothing.
            assertThat(OwnerRibbonRuns.planCellRibbon(
                    Optional.of(HEGEMONY),
                    List.of(buildHegemonyPresence(0)),
                    OwnerAffiliation.NONE,
                    SHORTENED_UNCONTESTED_RULES))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void partsOneOwnersStakesWithInterjectionsInItsDarkShade() {
            // Three stakes of one owner come out as three bright segments with two dark
            // partings strictly between them - the run itself neither opens nor closes on one,
            // and the only other parting here is the divider the owner ahead of it is closed by.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(3)),
                OwnerAffiliation.NONE,
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
        void closesAnOwnersRunInItsOwnDarkShadeWhereAnotherOwnersRunFollows() {
            // Hegemony with three stakes beside Tri-Tachyon with two. The divider between them
            // is Hegemony's dark shade, not Tri-Tachyon's: it closes the run it follows, which
            // is what makes it read as the Hegemony's holdings ending rather than as a gap
            // belonging to nobody.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(3), buildTriTachyonPresence(2)),
                OwnerAffiliation.NONE,
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
        void leavesTheLastOwnersRunOpen() {
            // Three owners of one stake each: a divider at each of the two handovers and none
            // after the last run, since a band ending on a divider would part its final owner
            // from nothing at all.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(
                    buildHegemonyPresence(1),
                    buildTriTachyonPresence(1),
                    buildDiktatPresence(1)),
                OwnerAffiliation.NONE,
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
        void drawsNoPartingInsideAnOwnerHoldingASingleStake() {
            // A one-stake owner has no inside, so nothing parts it from itself - the smallest
            // rival mark the ribbon can make is one bright segment, and the only dark run in
            // this band is the divider between the two owners.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(1)),
                OwnerAffiliation.NONE,
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsTheOnePresentOwnerAloneWhereTheCellIsPaintedForAnAbsentOne() {
            // The absent-painter case the contest rule is phrased for: the cell is painted for the
            // Diktat, which holds nothing in it, so the band is Tri-Tachyon's run by itself.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(DIKTAT),
                List.of(buildTriTachyonPresence(2)),
                OwnerAffiliation.NONE,
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void shortensALoneOwnersRunsOnACellNoFillCovers() {
            // A system settled by one owner that no layer paints for anybody. Nothing here is a
            // rival of anything, so the cell falls to the uncontested reading and draws at its
            // shortened runs. Read off the lengths rather than off the fact of a band, since only
            // they tell the two readings apart.
            var plan = OwnerRibbonRuns.planCellRibbon(
                NO_PAINTER,
                List.of(buildTriTachyonPresence(2)),
                OwnerAffiliation.NONE,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 1),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 1));
        }

        @Test
        void keepsTheAuthoredRunLengthForTwoOwnersOnACellNoFillCovers() {
            // Two owners settled in a system no layer paints: with no fill naming either of them,
            // the second owner is what makes the cell a contest. Posed with the shortening on, so
            // the authored lengths are what say the contested reading took it.
            var plan = OwnerRibbonRuns.planCellRibbon(
                NO_PAINTER,
                List.of(buildTriTachyonPresence(1), buildDiktatPresence(1)),
                OwnerAffiliation.NONE,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(DIKTAT_BRIGHT, 3));
        }

        @Test
        void shortensALoneOwnersRunsBesideAnEmptyOneOnACellNoFillCovers() {
            // The same painterless cell with its second owner counted at nothing. What makes such a
            // cell a contest is a second owner holding something, not a second entry in the list -
            // so this is the lone owner's cell it looks like, and takes the shortened runs.
            var plan = OwnerRibbonRuns.planCellRibbon(
                NO_PAINTER,
                List.of(buildTriTachyonPresence(2), buildDiktatPresence(0)),
                OwnerAffiliation.NONE,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 1),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 1));
        }

        @Test
        void shortensTwoAlliesRunsOnACellNoFillCovers() {
            // An unpainted system settled by two owners standing together. With no fill naming
            // either of them the reading falls to how many sides are in it, and allies are one -
            // so the band tallies their joint footprint rather than announcing a war.
            var plan = OwnerRibbonRuns.planCellRibbon(
                NO_PAINTER,
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(1)),
                ALLIED_HEGEMONY_AND_TRITACHYON,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 1),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 1));
        }

        @Test
        void judgesACellNoFillCoversAgainstAnOwnerActuallyInIt() {
            // The Diktat is ranked first and holds nothing, and the two owners behind it stand
            // together: one side, so the cell takes the shortened runs. Judged against the listed
            // but absent owner instead, both allies would read as rivals of somebody who is not in
            // the system at all and the cell would band as a war.
            var plan = OwnerRibbonRuns.planCellRibbon(
                NO_PAINTER,
                List.of(
                    buildDiktatPresence(0),
                    buildHegemonyPresence(1),
                    buildTriTachyonPresence(1)),
                ALLIED_HEGEMONY_AND_TRITACHYON,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 1),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 1));
        }

        @Test
        void keepsTheAuthoredRunLengthForAnAlliedPairAndARivalOnACellNoFillCovers() {
            // The same unpainted system with an outsider settled in it: two sides are present, so
            // it is a contest and keeps the authored lengths. Read against the case above, this is
            // what says the painterless reading counts sides rather than allowing at most one owner.
            var plan = OwnerRibbonRuns.planCellRibbon(
                NO_PAINTER,
                List.of(
                    buildHegemonyPresence(1),
                    buildTriTachyonPresence(1),
                    buildDiktatPresence(1)),
                ALLIED_HEGEMONY_AND_TRITACHYON,
                SHORTENED_UNCONTESTED_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(DIKTAT_BRIGHT, 3));
        }

        @Test
        void keepsTheOwnerOrderItWasHandedRatherThanRankingThemItself() {
            // The painter is listed second and stays second: the caller ranked these, and a
            // ribbon that re-sorted could disagree with the fill about who leads the system.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildTriTachyonPresence(1), buildHegemonyPresence(1)),
                OwnerAffiliation.NONE,
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void leavesOutAnOwnerHoldingNothingWhileDrawingTheRest() {
            // The Diktat is listed but holds nothing counted, so it takes no run; the band is
            // the painter's two stakes and the one real rival, with no gap where it was. It
            // takes no divider either: the single dark run at the handover is the Hegemony's,
            // closing the run it actually follows.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(
                    buildHegemonyPresence(2),
                    buildDiktatPresence(0),
                    buildTriTachyonPresence(1)),
                OwnerAffiliation.NONE,
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
        void laysNoDividerAheadOfTheFirstOwnerWithSomethingToDraw() {
            // The absent-painter shape: the painting owner is listed and holds nothing, so the first run
            // in the band is the second owner's. A divider laid for the empty owner would open the
            // band on a parting, which parts the first run from nothing at all.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(DIKTAT),
                List.of(
                    buildDiktatPresence(0),
                    buildHegemonyPresence(2),
                    buildTriTachyonPresence(1)),
                OwnerAffiliation.NONE,
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
        void laysNoDividerAfterTheLastOwnerWithSomethingToDraw() {
            // An owner ranked last on a weight it holds no stakes behind still reaches the rule.
            // It draws no run, so the band has to end on Tri-Tachyon's segment: a divider for
            // the empty owner would close the band on a parting belonging to an owner that is not
            // in it.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(
                    buildHegemonyPresence(1),
                    buildTriTachyonPresence(1),
                    buildDiktatPresence(0)),
                OwnerAffiliation.NONE,
                STANDARD_RULES);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void takesBothRunLengthsFromTheOnesItWasGiven() {
            // Neither length is the rule's own: a four-wide stake parted by two reads as the
            // same holdings at the proportions the player set. The divider takes that same
            // parting length, so no knob can make one boundary say more than the other.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(2), buildTriTachyonPresence(1)),
                OwnerAffiliation.NONE,
                RibbonPlanRules.createFromAuthoredLengths(
                    new RibbonSegmentLengths(4, 2),
                    UNCONTESTED_RUNS_AUTHORED));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 4),
                    new RibbonSegment(HEGEMONY_DARK, 2),
                    new RibbonSegment(HEGEMONY_BRIGHT, 4),
                    new RibbonSegment(HEGEMONY_DARK, 2),
                    new RibbonSegment(TRITACHYON_BRIGHT, 4));
        }

        @Test
        void laysEveryStakeAndPartingIntoTheBandsLength() {
            // The two halves over one cell: five stakes, three partings inside the two runs
            // and the divider between them, summed into the budget the drawn width is settled
            // against on a crowded cell.
            var plan = OwnerRibbonRuns.planCellRibbon(
                Optional.of(HEGEMONY),
                List.of(buildHegemonyPresence(3), buildTriTachyonPresence(2)),
                OwnerAffiliation.NONE,
                STANDARD_RULES);

            assertThat(plan.sumLengthUnits())
                .isEqualTo(19);
        }
    }

    // The three owners the cases are stated over, each with its own pair of shades so a run can
    // be told from its neighbour's and a bright segment from the parting inside it. Each names
    // only what makes it that owner, leaving how many stakes it holds the one thing a case says.
    //
    // Both the IDs and the shades are the shared ones, since an owner drawing one colour here and
    // another in the suites that read a band back through a palette would let two of them mean
    // different things by the same owner while all of them passed.
    private static OwnerPresence buildHegemonyPresence(int stakeCount) {
        return buildPresence(HEGEMONY, HEGEMONY_BRIGHT, HEGEMONY_DARK, stakeCount);
    }

    private static OwnerPresence buildTriTachyonPresence(int stakeCount) {
        return buildPresence(TRITACHYON, TRITACHYON_BRIGHT, TRITACHYON_DARK, stakeCount);
    }

    private static OwnerPresence buildDiktatPresence(int stakeCount) {
        return buildPresence(DIKTAT, DIKTAT_BRIGHT, DIKTAT_DARK, stakeCount);
    }

    // The one shape every owner above is built in, so a presence gaining a component is one edit
    // rather than three, and no owner can drift into being stated differently from its neighbours.
    private static OwnerPresence buildPresence(
            String ownerId,
            Color brightShade,
            Color darkShade,
            int stakeCount) {

        return new OwnerPresence(
            ownerId,
            new ColourPair(brightShade, darkShade),
            stakeCount);
    }
}
