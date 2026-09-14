package kmu.maplayers.base.geometry;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.DecivilisedPlanetFixtures;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildUnroutedSectorOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the fold rather than any one tie-breaker: a preference stated about only one of the two
 * systems settles the point, one stated about both or neither settles nothing, and a pair nothing
 * separates leaves the point where it already is.
 */
class SiteTieBreakerTest {

    // The override on, which is the only arrangement in which a hidden system reaches the partition
    // to contest a point at all. The tie-breakers do not read it - that is the point of asking what
    // the map would show rather than what this pass is showing - so it is the same for every case.
    private static final MapVisibilityRules SHOWING_HIDDEN_SYSTEMS =
        new MapVisibilityRules(ColonyVisibility.BASE_FOG, true);

    @Nested
    class ShouldTakePoint {

        @Test
        void takesThePointForAShownContenderFromAHiddenHolder() {
            // The one thing the current list says, and the reason it exists: the settled system's
            // cell must not depend on a setting about systems the player has not been shown.
            var hiddenHolder = buildSystem("hidden");
            var shownContender = buildInhabitedSystem("settled");
            var pass = openPassOver(hiddenHolder, shownContender);

            assertThat(SiteTieBreaker.shouldTakePoint(pass, hiddenHolder, shownContender))
                .isTrue();
        }

        @Test
        void leavesThePointWithAShownHolderAgainstAHiddenContender() {
            // The same tie-breaker read the other way round. Without this a fold that answered the
            // contender whenever any tie-breaker spoke would pass the case above.
            var shownHolder = buildInhabitedSystem("settled");
            var hiddenContender = buildSystem("hidden");
            var pass = openPassOver(shownHolder, hiddenContender);

            assertThat(SiteTieBreaker.shouldTakePoint(pass, shownHolder, hiddenContender))
                .isFalse();
        }

        @Test
        void leavesThePointWhereItIsWhenBothSystemsAreShown() {
            // A tie-breaker both systems satisfy has said nothing about either, so the holder keeps
            // what it has - the sector's own listing order, which is what every ID-keyed read of a
            // colliding pair answers with too.
            var holder = buildInhabitedSystem("first");
            var contender = buildInhabitedSystem("second");
            var pass = openPassOver(holder, contender);

            assertThat(SiteTieBreaker.shouldTakePoint(pass, holder, contender))
                .isFalse();
        }

        @Test
        void leavesThePointWhereItIsWhenNeitherSystemIsShown() {
            // The other half of the same rule: two systems on the map by the override alone are
            // equally hidden, and nothing below that separates them either.
            var holder = buildSystem("first");
            var contender = buildSystem("second");
            var pass = openPassOver(holder, contender);

            assertThat(SiteTieBreaker.shouldTakePoint(pass, holder, contender))
                .isFalse();
        }
    }

    // A pass over a sector with no route onto the map, so a system is hidden unless the case gives
    // it somebody living there.
    private static MapVisibilityPass openPassOver(StarSystemAPI... systems) {
        return MapVisibilityPass.over(buildUnroutedSectorOf(systems), SHOWING_HIDDEN_SYSTEMS);
    }

    // A system nothing reaches and nobody lives in: on the map by the override alone.
    private static StarSystemAPI buildSystem(String id) {

        var systemMock = StarSystemFixture.buildSystem(id);

        when(systemMock.getPlanets())
            .thenReturn(List.of());

        return systemMock;
    }

    // A system somebody lived in, which is a place on the map the override has nothing to do with.
    private static StarSystemAPI buildInhabitedSystem(String id) {

        var systemMock = buildSystem(id);

        DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(systemMock);

        return systemMock;
    }
}
