package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.DecivilisedPlanetFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what a pass adds over the rule it answers: the parts it must be handed, and that a system
 * asked about twice is read once.
 *
 * <p>What the rule itself admits is exercised next door, over this same pass. Only the memo is
 * here, because it is the one thing no case about the rule can show - a pass that forgot between
 * asks would go on giving every one of those cases the right answer, and would quietly walk every
 * planet in the sector again on each of the reads a poll makes.
 *
 * <p>The ruin read is what the count is taken off. The colony half is memoised in the index
 * beneath, so it was never what repeated; the ruin walk had no memo of its own until the pass gave
 * it one.
 */
class MapVisibilityPassTest {

    // What one poll's reads of a system amount to once the memo holds: the fingerprint scan asks
    // for the salt and again through membership, and the motion walk asks a third time.
    private static final int ONE_READ = 1;

    @Nested
    class Constructor {

        @Test
        void rejectsNullColonies() {
            // A pass with no walk behind it would fault on the first system it read rather than
            // here, and a pass over a sector that cannot be reached is a different thing entirely -
            // an index over a null sector, which answers an empty set and is perfectly legal.
            assertThatThrownBy(() ->
                    new MapVisibilityPass(null, VisibleStars.scan(null), MapVisibilityRules.BASE))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullVisibleStars() {
            // Standing an empty scan in would draw no system on the access path, so a sector's
            // whole core would vanish from the map with nothing on screen saying why.
            assertThatThrownBy(() ->
                    new MapVisibilityPass(
                        new SystemColoniesIndex(null),
                        null,
                        MapVisibilityRules.BASE))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullRules() {
            // Required on the same terms as the other two: the rules are sampled once where the
            // tick begins, so a null here is that one read having gone wrong.
            assertThatThrownBy(() ->
                    new MapVisibilityPass(
                        new SystemColoniesIndex(null),
                        VisibleStars.scan(null),
                        null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class IsRevealedDecivilised {

        @Test
        void readsOneSystemsColoniesOnceHoweverOftenTheRuinIsAsked() {
            // Every public read reaches the pass's one walk of the system, so the count covers a
            // poll's whole use of it: the salt asks the ruin outright, and membership asks it
            // through inhabitation.
            var system = buildSystem("a");
            var pass = MapVisibilityPass.over(buildSectorHolding(system), MapVisibilityRules.BASE);

            pass.isRevealedDecivilised(system);
            pass.isSystemInhabited(system);
            pass.isDrawn(system);

            verify(system, times(ONE_READ)).getAllEntities();
        }

        @Test
        void answersOffTheColonySetRatherThanOffTheSystemsPlanets() {
            // A ruin is a kind of colony, so the pass reads it where it reads everything else -
            // which is what keeps the salt, the cell and the box over it from ever parting on
            // whether a system is drawn as ruins.
            var ruinedSystem = buildSystem("ruined");
            var emptySystem = buildSystem("empty");

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(ruinedSystem);

            var pass = MapVisibilityPass.over(
                buildSectorHolding(ruinedSystem, emptySystem),
                MapVisibilityRules.BASE);

            assertThat(pass.isRevealedDecivilised(ruinedSystem))
                .isTrue();
            assertThat(pass.isRevealedDecivilised(emptySystem))
                .isFalse();
        }
    }

    // A sector whose hyperspace carries no star anchor and whose economy lists nothing, so
    // inhabitation rests on what a case hangs on a system rather than on any route the fixture
    // opens.
    private static SectorAPI buildSectorHolding(StarSystemAPI... systems) {

        var hyperspaceMock = mock(LocationAPI.class);

        when(hyperspaceMock.getEntities(JumpPointAPI.class))
            .thenReturn(List.of());

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systems));
        when(sectorMock.getEconomy())
            .thenReturn(mock(EconomyAPI.class));
        when(sectorMock.getHyperspace())
            .thenReturn(hyperspaceMock);

        return sectorMock;
    }

    // A system holding nothing at all, which each case then furnishes. Its planets are stubbed
    // empty rather than left to the mock default, so the ruin walk really runs and the count
    // above is of a read that happened.
    private static StarSystemAPI buildSystem(String id) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(id);
        when(systemMock.getPlanets())
            .thenReturn(List.of());

        return systemMock;
    }
}
