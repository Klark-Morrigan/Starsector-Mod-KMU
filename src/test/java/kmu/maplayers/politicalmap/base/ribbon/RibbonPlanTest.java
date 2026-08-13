package kmu.maplayers.politicalmap.base.ribbon;

import kmlib.starsector.factions.FactionPalette;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the segment rule on hand-built presences: which cells get a band at all, and what the
 * runs inside one look like.
 *
 * <p>Two things carry the whole design and are stated here on literals. The presence gate -
 * a band exactly where some bloc other than the painter holds something - is checked from
 * both sides, including the decree case where the painter holds nothing itself and a single
 * other bloc is enough. The interjection boundary - a dark parting between two markets of one
 * bloc, and nothing at all where two blocs meet - is checked on a run long enough to have an
 * inside, and across a handover.
 *
 * <p>Every case names its blocs in a deliberate order and expects that same order out, since
 * the rule ranks nothing itself and a case that happened to be sorted would hide it.
 */
final class RibbonPlanTest {

    private static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    private static final Color HEGEMONY_DARK = new Color(40, 60, 120);
    private static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);
    private static final Color TRITACHYON_DARK = new Color(20, 90, 80);
    private static final Color DIKTAT_BRIGHT = new Color(230, 180, 90);
    private static final Color DIKTAT_DARK = new Color(110, 70, 20);

    // The design's own proportions: a market three widths long, parted by one width.
    private static final RibbonSegmentLengths STANDARD_LENGTHS = new RibbonSegmentLengths(3, 1);

    @Nested
    class PlanCellRibbon {

        @Test
        void drawsNoRibbonInACellNoBlocIsPresentIn() {
            // A decreed system nobody holds anything in: the decree painted the fill, and
            // there is no presence anywhere for a band to report.
            assertThat(RibbonPlan.planCellRibbon("hegemony", List.of(), STANDARD_LENGTHS))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsNoRibbonWhereThePainterIsTheOnlyBlocPresent() {
            // The single-holder case the gate exists for: however many markets the painter
            // holds, a band would only repeat what its own fill already says.
            assertThat(RibbonPlan.planCellRibbon(
                    "hegemony",
                    List.of(buildHegemonyPresence(4)),
                    STANDARD_LENGTHS))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void drawsNoRibbonWhereTheOnlyOtherBlocHoldsNothing() {
            // A bloc counted at nothing holds no market the score was decided on, so it is not
            // presence and cannot open a band on a cell that is otherwise single-holder.
            assertThat(RibbonPlan.planCellRibbon(
                    "hegemony",
                    List.of(buildHegemonyPresence(2), buildTriTachyonPresence(0)),
                    STANDARD_LENGTHS))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void partsOneBlocsMarketsWithInterjectionsInItsDarkShade() {
            // Three markets of one bloc come out as three bright segments with two dark
            // partings strictly between them - the run neither opens nor closes on one.
            var plan = RibbonPlan.planCellRibbon(
                "hegemony",
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(3)),
                STANDARD_LENGTHS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void buttsTwoBlocsRunsAgainstEachOtherWithNoInterjection() {
            // Hegemony with three markets beside Tri-Tachyon with two: eight runs, and the
            // only boundary between the two blocs is the colour change itself.
            var plan = RibbonPlan.planCellRibbon(
                "hegemony",
                List.of(buildHegemonyPresence(3), buildTriTachyonPresence(2)),
                STANDARD_LENGTHS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsOneBareSegmentForABlocHoldingASingleMarket() {
            // A one-market bloc has no inside, so it takes no parting at all - the smallest
            // rival mark the ribbon can make.
            var plan = RibbonPlan.planCellRibbon(
                "hegemony",
                List.of(buildHegemonyPresence(1), buildTriTachyonPresence(1)),
                STANDARD_LENGTHS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsTheOnePresentBlocAloneWhereTheCellIsPaintedForAnAbsentOne() {
            // The decree case the gate is phrased for: the Diktat holds the system by decree
            // and nothing in it, so the band is Tri-Tachyon's run by itself.
            var plan = RibbonPlan.planCellRibbon(
                "sindria",
                List.of(buildTriTachyonPresence(2)),
                STANDARD_LENGTHS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void keepsTheBlocOrderItWasHandedRatherThanRankingThemItself() {
            // The painter is listed second and stays second: the caller ranked these, and a
            // ribbon that re-sorted could disagree with the fill about who leads the system.
            var plan = RibbonPlan.planCellRibbon(
                "hegemony",
                List.of(buildTriTachyonPresence(1), buildHegemonyPresence(1)),
                STANDARD_LENGTHS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3));
        }

        @Test
        void leavesOutABlocHoldingNothingWhileDrawingTheRest() {
            // The Diktat is listed but holds nothing counted, so it takes no run; the band is
            // the painter's two markets and the one real rival, with no gap where it was.
            var plan = RibbonPlan.planCellRibbon(
                "hegemony",
                List.of(
                    buildHegemonyPresence(2),
                    buildDiktatPresence(0),
                    buildTriTachyonPresence(1)),
                STANDARD_LENGTHS);

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void takesBothRunLengthsFromTheOnesItWasGiven() {
            // Neither length is the rule's own: a four-wide market parted by two reads as the
            // same holdings at the proportions the player set.
            var plan = RibbonPlan.planCellRibbon(
                "hegemony",
                List.of(buildHegemonyPresence(2), buildTriTachyonPresence(1)),
                new RibbonSegmentLengths(4, 2));

            assertThat(plan.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 4),
                    new RibbonSegment(HEGEMONY_DARK, 2),
                    new RibbonSegment(HEGEMONY_BRIGHT, 4),
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
            // The two halves over one cell: five markets and three partings planned, summed
            // into the budget the drawn width is settled against on a crowded cell.
            var plan = RibbonPlan.planCellRibbon(
                "hegemony",
                List.of(buildHegemonyPresence(3), buildTriTachyonPresence(2)),
                STANDARD_LENGTHS);

            assertThat(plan.sumLengthUnits())
                .isEqualTo(18);
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
    private static BlocPresence buildHegemonyPresence(int marketCount) {
        return buildPresence("hegemony", HEGEMONY_BRIGHT, HEGEMONY_DARK, marketCount);
    }

    private static BlocPresence buildTriTachyonPresence(int marketCount) {
        return buildPresence("tritachyon", TRITACHYON_BRIGHT, TRITACHYON_DARK, marketCount);
    }

    private static BlocPresence buildDiktatPresence(int marketCount) {
        return buildPresence("sindria", DIKTAT_BRIGHT, DIKTAT_DARK, marketCount);
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
