package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the spotlight as the render side reads it: where the spotlit bloc lives among the systems a
 * holder map left out, and the one synthetic key its footprint carries. The presence read answers
 * only over the candidates it is handed and only where the bloc lives, since the cells it spares are
 * the ones the map would otherwise draw as empty backdrop. The key is pinned as a literal and
 * recognised only by this class, so a resolve stamping it and the style side matching it cannot
 * disagree.
 */
final class SpotlitBlocsTest {

    private static final String SPOTLIT_BLOC_ID = "hegemony";

    // The size every posed colony carries; the read asks who lives where, never how many.
    private static final int COLONY_SIZE = 4;

    private static final FactionAPI HEGEMONY_FACTION = SectorOwnershipFixtures.buildFaction("hegemony");
    private static final FactionAPI TRITACHYON_FACTION = SectorOwnershipFixtures.buildFaction("tritachyon");

    @Nested
    class FindPresentSystemKeys {

        @Test
        void findPresentSystemKeysNamesTheCandidatesTheSpotlitBlocLivesIn() {

            var sector = buildTwoSystemSector();

            var corvusKey = readSystemKey(sector, "corvus");
            var askoniaKey = readSystemKey(sector, "askonia");

            assertThat(SpotlitBlocs.findPresentSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of(corvusKey, askoniaKey)))
                .containsExactly(corvusKey);
        }

        @Test
        void findPresentSystemKeysLeavesOutASystemThatIsNoCandidate() {
            // The holder map already drew every system it resolved, so a system outside the
            // candidates is never answered for, wherever the bloc lives.
            var sector = buildTwoSystemSector();

            assertThat(SpotlitBlocs.findPresentSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of(readSystemKey(sector, "askonia"))))
                .isEmpty();
        }

        @Test
        void findPresentSystemKeysAnswersNothingWithNoBlocSpotlit() {

            var sector = buildTwoSystemSector();

            assertThat(SpotlitBlocs.findPresentSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    null,
                    Set.of(readSystemKey(sector, "corvus"))))
                .isEmpty();
        }

        @Test
        void findPresentSystemKeysAnswersNothingWithNoCandidates() {

            var sector = buildTwoSystemSector();

            assertThat(SpotlitBlocs.findPresentSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of()))
                .isEmpty();
        }

        @Test
        void findPresentSystemKeysAnswersNothingBeforeTheEconomyStandsUp() {
            // Mid-load the systems are walkable while the economy is not, so no colony can be read
            // and nothing is reported present rather than every system reported empty.
            var sector = SectorOwnershipFixtures.buildEconomylessSectorWithSystem("corvus");

            assertThat(SpotlitBlocs.findPresentSystemKeys(
                    SectorOwnershipFixtures.buildHolderPassOver(sector),
                    SPOTLIT_BLOC_ID,
                    Set.of(readSystemKey(sector, "corvus"))))
                .isEmpty();
        }
    }

    @Nested
    class IsSpotlitBloc {

        @Test
        void isSpotlitBlocRecognisesTheSpotlitKey() {

            assertThat(SpotlitBlocs.isSpotlitBloc(SpotlitBlocs.readSpotlitBlocKey()))
                .isTrue();
        }

        @Test
        void isSpotlitBlocRejectsARealBlocId() {

            assertThat(SpotlitBlocs.isSpotlitBloc(SPOTLIT_BLOC_ID))
                .isFalse();
        }

        @Test
        void isSpotlitBlocRejectsNoKey() {

            assertThat(SpotlitBlocs.isSpotlitBloc(null))
                .isFalse();
        }
    }

    @Nested
    class ReadSpotlitBlocKey {

        @Test
        void readSpotlitBlocKeyAnswersTheSentinelPrefixedKey() {
            // The "$" prefix is what keeps the key from colliding with any real faction or group ID.
            assertThat(SpotlitBlocs.readSpotlitBlocKey())
                .isEqualTo("$kmu_filter_spotlit");
        }
    }

    // Corvus holds a colony of the spotlit bloc; Askonia holds only a rival's.
    private static SectorAPI buildTwoSystemSector() {

        return SectorOwnershipFixtures.buildSectorWithSystems(
            List.of(),
            SectorOwnershipFixtures.listSystemMarkets(
                "corvus",
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE)),
            SectorOwnershipFixtures.listSystemMarkets(
                "askonia",
                SectorOwnershipFixtures.buildVisibleMarket(TRITACHYON_FACTION, COLONY_SIZE)));
    }

    // The key the pass reads a staged system under, so a case names its candidates the same way.
    private static SystemKey readSystemKey(SectorAPI sector, String systemId) {
        return SystemKey.readKeyOf(SectorOwnershipFixtures.findSystemIn(sector, systemId));
    }
}
