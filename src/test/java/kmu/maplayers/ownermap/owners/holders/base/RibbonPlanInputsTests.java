package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.buildGroupOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins that one bake's band inputs answer their grouping and habitation off the pass rather than
 * off a read of their own.
 *
 * <p>The pass is stood in for, since what it answers is pinned in its own suite; what belongs here
 * is that the inputs ask it rather than anything beside it.
 */
final class RibbonPlanInputsTests {

    private static final boolean UNCONTESTED_RUNS_SHORTENED = true;

    // Laying rules no default lands on, so carrying them is seen.
    private static final RibbonPlanRules RULES = RibbonPlanRules.createFromAuthoredLengths(
        new RibbonSegmentLengths(4, 2),
        UNCONTESTED_RUNS_SHORTENED);

    // A palette read none of these cases colours through: what they pin is the pass's answers.
    private static final RibbonPaletteReader UNREAD_PALETTES = (blocId, ownerFactionId) -> null;

    @Nested
    class Grouping {

        // The counts fold under the grouping the fills were painted with, which is the pass's.
        @Test
        void answersThePassesGrouping() {

            // ARRANGE
            var grouping = buildGroupOf("hegemony", "persean");
            var passMock = mock(HolderPass.class);
            var inputs = new RibbonPlanInputs(passMock, UNREAD_PALETTES, OwnerAffiliation.NONE, RULES);

            when(passMock.grouping())
                .thenReturn(grouping);

            // ACT
            var actualGrouping = inputs.grouping();

            // ASSERT
            assertThat(actualGrouping)
                .isSameAs(grouping);
        }
    }

    @Nested
    class ReadHabitationIn {

        // Asserted as identity: the band reads the very value the pass remembered for the
        // system, so it neither filters nor folds the colonies a second time.
        @Test
        void answersThePassesOwnRememberedHabitation() {

            // ARRANGE
            var systemMock = mock(StarSystemAPI.class);
            var habitation = new SystemHabitation(
                List.of(),
                Map.of("hegemony", new BlocHabitation(List.of("hegemony"), 5)));

            var passMock = mock(HolderPass.class);
            var inputs = new RibbonPlanInputs(passMock, UNREAD_PALETTES, OwnerAffiliation.NONE, RULES);

            when(passMock.readHabitationIn(systemMock))
                .thenReturn(habitation);

            // ACT
            var actualHabitation = inputs.readHabitationIn(systemMock);

            // ASSERT
            assertThat(actualHabitation)
                .isSameAs(habitation);
        }
    }
}
