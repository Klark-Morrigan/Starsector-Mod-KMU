package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;
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
 * Pins that one bake's band inputs answer their palettes, grouping and habitation off the pass
 * rather than off a read of their own.
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

    @Nested
    class Palettes {

        @Test
        void answersThePassesOwnPaletteRead() {
            // The pass's own rather than one built beside it, so a band colours a bloc through the
            // very object the fills of the same rebuild were coloured through.
            var passMock = mock(HolderPass.class);
            var palettes = new SectorBlocPalettes(mock(SectorAPI.class), HolderGrouping.identity());

            when(passMock.palettes())
                .thenReturn(palettes);

            assertThat(new RibbonPlanInputs(passMock, OwnerAffiliation.NONE, RULES).palettes())
                .isSameAs(palettes);
        }
    }

    @Nested
    class Grouping {

        @Test
        void answersThePassesGrouping() {
            // The counts fold under the grouping the fills were painted with, which is the pass's.
            var grouping = buildGroupOf("hegemony", "persean");
            var passMock = mock(HolderPass.class);

            when(passMock.grouping())
                .thenReturn(grouping);

            assertThat(new RibbonPlanInputs(passMock, OwnerAffiliation.NONE, RULES).grouping())
                .isSameAs(grouping);
        }
    }

    @Nested
    class ReadHabitationIn {

        @Test
        void answersThePassesOwnRememberedHabitation() {
            // Asserted as identity: the band reads the very value the pass remembered for the
            // system, so it neither filters nor folds the colonies a second time.
            var systemMock = mock(StarSystemAPI.class);
            var habitation = new SystemHabitation(List.of(), Map.of("hegemony", new BlocHabitation(1, 5)));
            var passMock = mock(HolderPass.class);

            when(passMock.readHabitationIn(systemMock))
                .thenReturn(habitation);

            assertThat(new RibbonPlanInputs(passMock, OwnerAffiliation.NONE, RULES)
                    .readHabitationIn(systemMock))
                .isSameAs(habitation);
        }
    }
}
