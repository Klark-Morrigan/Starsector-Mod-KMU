package kmu.maplayers.politicalmap.dominance.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
final class HeldOrClaimedSystemRibbonPlannerTest {

    private static final Color HELD_COLOUR = new Color(140, 160, 220);
    private static final Color CLAIMED_COLOUR = new Color(200, 180, 120);

    // Two plans no case could confuse, so an assertion reads which mechanic replied off the runs.
    private static final RibbonPlan HELD_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(HELD_COLOUR, 3)));

    private static final RibbonPlan CLAIMED_PLAN =
        new RibbonPlan(List.of(new RibbonSegment(CLAIMED_COLOUR, 3)));

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
    }

    // The composition over a held source giving the posed answer and a claim planner giving a
    // plan of its own, so which of the two came back is visible in the result.
    private static RibbonPlan planFrom(Optional<RibbonPlan> heldAnswer) {

        HeldSystemRibbonSource heldSourceFake = system -> heldAnswer;
        SystemRibbonPlanner claimedPlannerFake = system -> CLAIMED_PLAN;

        // Nothing is read off the system - both mechanics are stand-ins here - so it only has to
        // be the one handed through.
        var systemMock = mock(StarSystemAPI.class);

        return new HeldOrClaimedSystemRibbonPlanner(heldSourceFake, claimedPlannerFake)
            .planSystemRibbon(systemMock);
    }
}
