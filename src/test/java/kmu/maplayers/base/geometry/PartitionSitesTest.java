package kmu.maplayers.base.geometry;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildStarAnchoredSectorOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what may seed a site: one system per hyperspace point, the point going to the system the map
 * shows in its own right, and a coincidence said out loud once rather than on every collection.
 *
 * <p>Which of two systems a tie-breaker prefers is exercised next door over the tie-breakers
 * themselves. What is here is the collection applying them - that a losing system really is absent
 * from the sites, and that the loss reaches the log.
 */
class PartitionSitesTest {

    // No movers in these cases: the exclusion is exercised through the partition it shapes, and an
    // empty set leaves the point rule as the only thing able to drop a site.
    private static final Set<SystemKey> NO_MOVING_SYSTEMS = Set.of();

    // The override on, which is what lets a system the map would not show reach the collection at
    // all and contest a point.
    private static final MapVisibilityRules SHOWING_HIDDEN_SYSTEMS =
        new MapVisibilityRules(ColonyVisibility.BASE_FOG, true);

    // Where the colliding pair is posed, standing in for RAT's two abyss systems on one point.
    private static final float SHARED_X = -89_840f;
    private static final float SHARED_Y = -59_500f;

    @Nested
    class CollectSitesFrom {

        @Test
        void collectsOneSiteForTwoSystemsOnOnePoint() {
            // Two sites on one coordinate cannot both be cut a cell, so the second seeds nothing -
            // the pair being told apart at all is what the key arms are for.
            var first = new SystemKey("abyss", "", "425b5");

            var sites = new PartitionSites().collectSitesFrom(
                openPassOver(
                    buildAccessibleSystem("abyss", "425b5", SHARED_X, SHARED_Y),
                    buildAccessibleSystem("abyss", "4379d", SHARED_X, SHARED_Y)),
                NO_MOVING_SYSTEMS);

            assertThat(sites)
                .containsOnlyKeys(first);
        }

        @Test
        void givesTheSharedPointToTheSystemTheMapShowsRatherThanToAHiddenOne() {
            // The hidden system is listed first and still loses the point: it leaves the map the
            // moment the setting goes off, and what the system beside it is drawn as must not turn
            // on a toggle about something else.
            var shown = new SystemKey("abyss", "", "425b5");

            var sites = new PartitionSites().collectSitesFrom(
                openPassOver(
                    buildHiddenSystem("abyss", "4379d", SHARED_X, SHARED_Y),
                    buildAccessibleSystem("abyss", "425b5", SHARED_X, SHARED_Y)),
                NO_MOVING_SYSTEMS);

            assertThat(sites)
                .containsOnlyKeys(shown);
        }

        @Test
        void collectsTheDroppedSystemAgainOnceThePointItStoodOnIsFree() {
            // The drop is a reading of the current sites rather than a verdict remembered about a
            // system, so the one that lost the point takes it as soon as the holder is off the map.
            var second = new SystemKey("abyss", "", "4379d");
            var partitionSites = new PartitionSites();

            partitionSites.collectSitesFrom(
                openPassOver(
                    buildAccessibleSystem("abyss", "425b5", SHARED_X, SHARED_Y),
                    buildAccessibleSystem("abyss", "4379d", SHARED_X, SHARED_Y)),
                NO_MOVING_SYSTEMS);

            var sites = partitionSites.collectSitesFrom(
                openPassOver(buildAccessibleSystem("abyss", "4379d", SHARED_X, SHARED_Y)),
                NO_MOVING_SYSTEMS);

            assertThat(sites)
                .containsOnlyKeys(second);
        }

        @Test
        void statesThePointAndBothSystemsOfACoincidence() {
            // The line that turns "a system has no cell" into a one-line diagnosis. A dropped site
            // is invisible on the map by construction - there is nothing drawn to notice the
            // absence of - so the saying of it is pinned rather than left to the reader of the code.
            var pass = openPassOver(
                buildNamedSystem(
                    buildAccessibleSystem("abyss", "425b5", SHARED_X, SHARED_Y), "The Abyss"),
                buildNamedSystem(
                    buildAccessibleSystem("abyss", "4379d", SHARED_X, SHARED_Y), "Abyss Icon"));

            var log = LogAppenderFake.captureLogOf(
                PartitionSites.class,
                () -> new PartitionSites().collectSitesFrom(pass, NO_MOVING_SYSTEMS));

            assertThat(log.getMessages())
                .hasSize(1);
            assertThat(log.getMessages().get(0))
                .contains("abyss")
                .contains(String.valueOf((double) SHARED_X))
                .contains(String.valueOf((double) SHARED_Y));
        }

        @Test
        void saysNothingFurtherWhileTheSameCoincidenceStands() {
            // The sites are re-collected on every update, and a pair that has not changed is not
            // news twice. Said again only when the dropped set moves, which is what keeps this from
            // being a warn-once mute over a set that can genuinely change.
            var partitionSites = new PartitionSites();

            partitionSites.collectSitesFrom(
                openPassOver(
                    buildAccessibleSystem("abyss", "425b5", SHARED_X, SHARED_Y),
                    buildAccessibleSystem("abyss", "4379d", SHARED_X, SHARED_Y)),
                NO_MOVING_SYSTEMS);

            var log = LogAppenderFake.captureLogOf(
                PartitionSites.class,
                () -> partitionSites.collectSitesFrom(
                    openPassOver(
                        buildAccessibleSystem("abyss", "425b5", SHARED_X, SHARED_Y),
                        buildAccessibleSystem("abyss", "4379d", SHARED_X, SHARED_Y)),
                    NO_MOVING_SYSTEMS));

            assertThat(log.getMessages())
                .isEmpty();
        }
    }

    // A pass under the override, so a hidden system reaches the collection rather than being
    // declined before it can contest anything.
    private static MapVisibilityPass openPassOver(StarSystemAPI... systems) {
        return MapVisibilityPass.over(buildStarAnchoredSectorOf(systems), SHOWING_HIDDEN_SYSTEMS);
    }

    // An admitted system carrying a hyperspace anchor of its own, so a case can pose two systems
    // the sector states one ID about and still tell them apart.
    private static StarSystemAPI buildAccessibleSystem(
            String id,
            String anchorEntityId,
            float x,
            float y) {

        // Wired into hyperspace by a jump point, so the access rule admits it.
        var systemMock = StarSystemFixture.buildSystemAt(id, x, y);

        when(systemMock.getJumpPoints())
            .thenReturn(List.of(mock(SectorEntityToken.class)));

        return StarSystemFixture.anchorSystemTo(systemMock, anchorEntityId);
    }

    // A system the map would not show: cut off from hyperspace, no gate, nobody living there. On
    // the map only while hidden systems are being shown.
    private static StarSystemAPI buildHiddenSystem(
            String id,
            String anchorEntityId,
            float x,
            float y) {

        var systemMock = buildAccessibleSystem(id, anchorEntityId, x, y);

        when(systemMock.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER))
            .thenReturn(true);
        when(systemMock.getEntitiesWithTag(Tags.GATE))
            .thenReturn(List.of());

        return systemMock;
    }

    // A system the log can name as a person would, the two of a colliding pair sharing an ID but
    // not their names.
    private static StarSystemAPI buildNamedSystem(StarSystemAPI systemMock, String name) {
        return StarSystemFixture.nameSystem(systemMock, name);
    }
}
