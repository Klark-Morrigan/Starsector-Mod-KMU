package kmu.maplayers.politicalmap.ribbon;

import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.RibbonSegment;
import kmu.maplayers.base.ribbon.SystemRibbonPlanner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which mechanic speaks for a cell on a view that paints held territory and extends it with
 * claims.
 *
 * <p>One rule, and it has to hold both ways round. A system some bloc holds is counted by the
 * dominance sample even when that sample plans no band - a lone holder's cell draws nothing, and
 * asking the claim contest about it instead would find a rival standing there and paint a band the
 * fill never earned. A system nobody holds is exactly the system a claim painted, so the contest
 * answers for it.
 *
 * <p>Both mechanics are posed as their answers rather than as their reads, which is what the
 * narrow held source buys: the rule under test is "which answer is taken", and the economy walk
 * and the contest walk behind those answers have suites of their own.
 */
final class HeldOrClaimedSystemRibbonPlannerTests {

    private static final Color HELD_COLOUR = new Color(140, 160, 220);
    private static final Color CLAIMED_COLOUR = new Color(200, 180, 120);

    // Two plans no case could confuse, so an assertion reads which mechanic replied off the runs.
    private static final RibbonPlan HELD_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(HELD_COLOUR, 3)));

    private static final RibbonPlan CLAIMED_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(CLAIMED_COLOUR, 3)));

    // The system every case plans.
    private static final String SYSTEM_ID = "corvus";

    @Nested
    class PlanSystemRibbon {

        @Test
        void takesTheHeldPlanForASystemTheDominanceSamplePaints() {

            assertThat(planFrom(Optional.of(HELD_PLAN)).segments())
                .containsExactly(new RibbonSegment(HELD_COLOUR, 3));
        }

        @Test
        void keepsTheHeldSilenceForAHeldSystemThatPlansNoBand() {
            // The case the composition exists for. A lone holder's system plans nothing, and that
            // silence is the dominance mechanic's answer - not an absence for the claim contest to
            // fill in, which would put a band on a cell whose fill says one bloc holds it.
            assertThat(planFrom(Optional.of(RibbonPlan.NONE)))
                .isEqualTo(RibbonPlan.NONE);
        }

        @Test
        void fallsBackToTheClaimContestForASystemNoBlocHolds() {
            // Nothing held here, which on these views is precisely the system a claim painted -
            // and the contest is what knows who is standing in it.
            assertThat(planFrom(Optional.empty()).segments())
                .containsExactly(new RibbonSegment(CLAIMED_COLOUR, 3));
        }

        // The map shows a band, never which mechanic counted it, so the line is what says the
        // band followed the fill.
        @Test
        void writesThatTheHeldMechanicCountedAHeldSystem() {

            // ARRANGE
            var expectedLine = "System band planned; system=corvus mechanic=held";

            // ACT
            var logFake = LogAppenderFake.captureLogOf(
                HeldOrClaimedSystemRibbonPlanner.class,
                () -> planFrom(Optional.of(HELD_PLAN)));

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(expectedLine);
        }

        @Test
        void writesThatTheClaimMechanicCountedASystemNoBlocHolds() {

            // ARRANGE
            var expectedLine = "System band planned; system=corvus mechanic=claimed";

            // ACT
            var logFake = LogAppenderFake.captureLogOf(
                HeldOrClaimedSystemRibbonPlanner.class,
                () -> planFrom(Optional.empty()));

            // ASSERT
            assertThat(logFake.getMessages())
                .containsExactly(expectedLine);
        }
    }

    // The composition over a held source giving the posed answer and a claim planner giving a
    // plan of its own, so which of the two came back is visible in the result.
    private static RibbonPlan planFrom(Optional<RibbonPlan> heldAnswer) {

        HeldSystemRibbonSource heldSourceFake = system -> heldAnswer;
        SystemRibbonPlanner claimedPlannerFake = system -> CLAIMED_PLAN;

        // Nothing but its ID is read off the system - both mechanics are stand-ins here - and the
        // ID only by the debug line naming it.
        var system = StarSystemFixture.buildSystem(SYSTEM_ID);

        return new HeldOrClaimedSystemRibbonPlanner(heldSourceFake, claimedPlannerFake)
            .planSystemRibbon(system);
    }
}
