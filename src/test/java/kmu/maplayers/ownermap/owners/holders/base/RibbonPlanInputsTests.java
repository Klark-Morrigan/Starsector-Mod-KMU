package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.RibbonSegmentLengths;
import kmu.maplayers.ownermap.ribbon.UncontestedRibbonRuns;
import kmu.starsector.SectorFactionFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.GROUP_BLOC_ID;
import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.buildGroupOf;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins what one bake's band inputs are read from: the palettes built off the pass's own sector and
 * grouping, the affiliation and rules carried as handed, and the grouping and habitation answered by
 * the pass rather than by a read of their own.
 */
final class RibbonPlanInputsTests {

    private static final String SYSTEM_ID = "corvus";

    private static final Color BRIGHT = new Color(140, 160, 220);
    private static final Color DARK = new Color(40, 60, 120);

    // The size every posed colony carries, which nothing here reads.
    private static final int COLONY_SIZE = 5;

    // Laying rules no default lands on, so carrying them is seen.
    private static final RibbonPlanRules RULES =
        new RibbonPlanRules(new RibbonSegmentLengths(4, 2), new UncontestedRibbonRuns(true));

    // An affiliation the case can tell from NONE by identity alone.
    private static final OwnerAffiliation AFFILIATION = (firstOwnerId, secondOwnerId) -> true;

    @Nested
    class CreateForPass {

        @Test
        void readsABlocsShadesOffThePassesSectorAndGrouping() {
            // A group bloc is shaded by its lead member, which only a palette folding under the
            // pass's grouping and reading the pass's sector can answer.
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction("hegemony", BRIGHT, DARK);
            var pass = HolderPass.createOver(sectorMock, UNDER_THE_FOG, buildGroupOf("hegemony", "persean"));
            var inputs = RibbonPlanInputs.createForPass(pass, AFFILIATION, RULES);

            assertThat(inputs.palettes().readBlocPalette(GROUP_BLOC_ID))
                .isEqualTo(new ColourPair(BRIGHT, DARK));
        }

        @Test
        void carriesTheHandedAffiliationAndRules() {

            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var inputs = RibbonPlanInputs.createForPass(pass, AFFILIATION, RULES);

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
            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, grouping);

            assertThat(RibbonPlanInputs.createForPass(pass, OwnerAffiliation.NONE, RULES).grouping())
                .isSameAs(grouping);
        }
    }

    @Nested
    class ReadHabitationIn {

        @Test
        void answersThePassesOwnRememberedHabitation() {
            // Asserted as identity: the band reads the very value the pass remembered for the
            // system, so it neither filters nor folds the colonies a second time.
            var sector = buildSectorWith(
                SYSTEM_ID,
                buildVisibleMarket(buildFaction("hegemony"), COLONY_SIZE));

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var system = buildOnlySystem(sector);
            var habitation = RibbonPlanInputs
                .createForPass(pass, OwnerAffiliation.NONE, RULES)
                .readHabitationIn(system);

            assertThat(habitation)
                .isSameAs(pass.readHabitationIn(system));
        }

        @Test
        void answersNobodyLivingForNoSystem() {

            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());
            var inputs = RibbonPlanInputs.createForPass(pass, OwnerAffiliation.NONE, RULES);

            assertThat(inputs.readHabitationIn(null).hasInhabitingColony())
                .isFalse();
        }
    }
}
