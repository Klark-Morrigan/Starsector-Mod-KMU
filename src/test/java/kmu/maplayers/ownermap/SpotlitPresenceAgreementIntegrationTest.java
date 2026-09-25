package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.SectorScenarioFixtures;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.holding.OwnerMapInhabitation;
import kmu.maplayers.ownermap.owners.SectorOwnershipFixtures;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.holding.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one guarantee neither suite either side of it can make: that a cell classified as empty
 * backdrop is never a cell the filter's spotlight has kept a bloc's fill over.
 *
 * <p>The classification reads {@link OwnerMapInhabitation}, the sparing reads
 * {@link SpotlitBlocs#findPresentSystemKeys}, and each has its own suite pinning its own answer.
 * Nothing in that pair stops the two drifting onto different colony projections - and the pair that
 * drifted draws a bloc's colours across a system the same pass has just called empty space, which
 * neither suite alone can see.
 *
 * <p>Integration rather than unit, because the agreement is a property of the habitation value
 * underneath both. Stub either read and the wiring this exists to catch is what gets asserted; the
 * sector, the entity walk and the colony rule are all read for real.
 *
 * <p>The derelict is what the pair is read against. One passes the fog, so a presence read wired
 * to the listing would spare its cell while the classification called the system empty - and would
 * answer identically on every case staging no derelict, which is nearly every system in a sector.
 */
final class SpotlitPresenceAgreementIntegrationTest {

    private static final String SYSTEM_ID = "haven";

    // The size every staged market carries. Neither read weighs a colony, so a case varying this
    // would vary nothing either answer can see.
    private static final int MARKET_SIZE = 4;

    @Nested
    class ClassifyAndSpareOneSystem {

        @Test
        void aBlocLivingInASystemIsSparedAndTheSystemCountsAsInhabited() {
            // The ordinary pairing, stated so the withholding cases below read as a narrowing of a
            // set that does hold something rather than as two empty answers agreeing by accident.
            var pirates = SectorOwnershipFixtures.buildFaction("pirates");
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(pirates, MARKET_SIZE));

            assertThat(isInhabited(sector))
                .isTrue();
            assertThat(findPresentSystemKeys(sector, "pirates"))
                .containsExactly(buildCellKey(SYSTEM_ID));
        }

        @Test
        void aDerelictSparesNobodyAndLeavesTheSystemUninhabited() {
            // Presence is a partition of the very set the classification asks the emptiness of, so
            // the derelict's owner is absent exactly where the system is empty space. A listing-fed
            // presence read would spare this cell and the map would draw a bloc over a system it
            // had just classified as backdrop.
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);

            SectorScenarioFixtures.placeDerelictIn(SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(isInhabited(sector))
                .isFalse();
            assertThat(findPresentSystemKeys(sector, Factions.NEUTRAL))
                .isEmpty();
        }

        @Test
        void anUndiscoveredColonySparesNobodyAndLeavesTheSystemUninhabited() {
            // The fog reaches both reads through the one rule the pass was opened with, so a colony
            // the player has not found neither settles its cell nor spares it.
            var pirates = SectorOwnershipFixtures.buildFaction("pirates");
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(pirates, MARKET_SIZE));

            assertThat(isInhabited(sector))
                .isFalse();
            assertThat(findPresentSystemKeys(sector, "pirates"))
                .isEmpty();
        }
    }

    // Whether the map counts the sector's one system as settled, off a pass opened as a rebuild
    // opens one.
    private static boolean isInhabited(SectorAPI sector) {

        return OwnerMapInhabitation.isSystemInhabited(
            buildPassOver(sector),
            SectorOwnershipFixtures.buildOnlySystem(sector));
    }

    // Where the spotlit bloc is spared the recede, asked of the same system under the same rule -
    // the pairing being the whole point, a case reading the two through different passes would
    // prove nothing.
    private static Set<SystemKey> findPresentSystemKeys(SectorAPI sector, String selectedBlocId) {

        return SpotlitBlocs.findPresentSystemKeys(
            buildPassOver(sector),
            selectedBlocId,
            Set.of(buildCellKey(SYSTEM_ID)));
    }

    private static HolderPass buildPassOver(SectorAPI sector) {
        return HolderPass.over(sector, UNDER_THE_FOG, HolderGrouping.identity());
    }
}
