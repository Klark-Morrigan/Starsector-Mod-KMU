package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;
import kmu.maplayers.ownermap.ribbon.UncontestedRibbonRuns;
import kmu.starsector.SectorFactionFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.GROUP_BLOC_ID;
import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.buildGroupOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what one bake's band inputs are read from: the pass's own palettes, the pass, affiliation
 * and rules carried as handed, and the grouping and habitation
 * answered by the pass rather than by a read of their own.
 *
 * <p>The pass is stood in for, since what it answers is pinned in its own suite; what belongs here
 * is that the inputs ask it rather than anything beside it.
 */
final class RibbonPlanInputsTests {

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    // Laying rules no default lands on, so carrying them is seen.
    private static final RibbonPlanRules RULES =
        new RibbonPlanRules(new RibbonSegmentLengths(4, 2), new UncontestedRibbonRuns(true));

    // An affiliation the case can tell from NONE by identity alone.
    private static final OwnerAffiliation AFFILIATION = (firstOwnerId, secondOwnerId) -> true;

    @Nested
    class CreateForPass {

        @Test
        void readsABlocsShadesThroughThePassesOwnPalettes() {
            // The pass's own palette read rather than one built beside it, so a band colours a bloc
            // through the very object the fills of the same rebuild were coloured through. A group
            // bloc is shaded by its lead member, which only a palette folding under the pass's
            // grouping and reading the pass's sector can answer.
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction("hegemony", BRIGHT, DARK);
            var grouping = buildGroupOf("hegemony", "persean");
            var passMock = stubPassOver(sectorMock, grouping);
            var palettes = new SectorBlocPalettes(sectorMock, grouping);

            when(passMock.palettes())
                .thenReturn(palettes);

            var inputs = RibbonPlanInputs.createForPass(passMock, AFFILIATION, RULES);

            assertThat(inputs.palettes())
                .isSameAs(palettes);
            assertThat(inputs.palettes().readBlocPalette(GROUP_BLOC_ID))
                .isEqualTo(new ColourPair(BRIGHT, DARK));
        }

        @Test
        void carriesTheHandedPassAffiliationAndRules() {
            // The pass is carried rather than reopened, so every planner of one bake counts off the
            // one walk the bake began with.
            var passMock = stubPassOver(mock(SectorAPI.class), HolderGrouping.identity());
            var inputs = RibbonPlanInputs.createForPass(passMock, AFFILIATION, RULES);

            assertThat(inputs.pass())
                .isSameAs(passMock);
            assertThat(inputs.affiliation())
                .isSameAs(AFFILIATION);
            assertThat(inputs.rules())
                .isSameAs(RULES);
        }
    }

    @Nested
    class Grouping {

        @Test
        void answersThePassesGrouping() {
            // The counts fold under the grouping the fills were painted with, which is the pass's.
            var grouping = buildGroupOf("hegemony", "persean");
            var passMock = stubPassOver(mock(SectorAPI.class), grouping);

            assertThat(RibbonPlanInputs.createForPass(passMock, OwnerAffiliation.NONE, RULES).grouping())
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
            var passMock = stubPassOver(mock(SectorAPI.class), HolderGrouping.identity());

            when(passMock.readHabitationIn(systemMock))
                .thenReturn(habitation);

            assertThat(RibbonPlanInputs
                    .createForPass(passMock, OwnerAffiliation.NONE, RULES)
                    .readHabitationIn(systemMock))
                .isSameAs(habitation);
        }
    }

    // A pass naming the stated sector and grouping, which is all the palettes are built from.
    private static HolderPass stubPassOver(SectorAPI sector, HolderGrouping grouping) {

        var passMock = mock(HolderPass.class);

        when(passMock.sector())
            .thenReturn(sector);
        when(passMock.grouping())
            .thenReturn(grouping);

        return passMock;
    }
}
