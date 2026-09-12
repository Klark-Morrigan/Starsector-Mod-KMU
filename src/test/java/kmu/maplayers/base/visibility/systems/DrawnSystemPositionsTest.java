package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.profiling.ProfileSection;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.starsector.SectorWalkCounters;
import kmlib.starsector.systems.SystemKey;
import kmlib.testfixtures.profiling.ProfileCounts;
import kmlib.testfixtures.profiling.RecordedCapture;
import kmlib.testfixtures.starsector.markets.colonies.ColonyMarketFixture;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildUnroutedSectorOf;
import static kmu.maplayers.base.visibility.systems.MapSectorFixture.listMarketsIn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the drawn-set walk: which systems earn a Voronoi site and where each one sits.
 *
 * <p>The membership rule itself is exercised next door; what only this can show is the walk
 * around it. It is driven through a real pass over a real sector, so a case admitting a system
 * on its colony read alone is what says that reading really happened - a walk over a hollow
 * pass would leave such a system off the map while every other case here went on passing.
 *
 * <p>The other half is the skip: a system with no hyperspace position has no site to place a
 * cell at, and is left out whatever the rule says about it.
 *
 * <p>Both addresses get a case posing two systems that share an id, since a live modded sector
 * lists several such pairs: the keyed read holds a site for each, the id read holds the first of
 * them - which is what a structure keyed by id gives up by being keyed that way.
 */
class DrawnSystemPositionsTest {

    // The force override on, so membership is settled without staging an economy that owns
    // the system - which leaves a case free to be about the position rather than the rule.
    private static final MapVisibilityRules FORCED_ONTO_MAP =
        new MapVisibilityRules(BASE_FOG, true);

    private static final String OWNING_FACTION = "hegemony";

    // The keys of the two systems the sector lists under one id. Written out rather than read off
    // the systems they belong to, an expectation taken from the code under test being no
    // expectation at all.
    private static final SystemKey THE_FIRST_DEEP_SPACE = new SystemKey("deep space", "", "8b3");
    private static final SystemKey THE_SECOND_DEEP_SPACE = new SystemKey("deep space", "", "38d53");

    // Nothing about a traversal count is a duration, so one reading answers every clock read.
    private static final long FIXED_CLOCK_NANOS = 0L;

    // The one the pass itself makes, which is what everything reading through it shares.
    private static final long THE_PASSES_OWN_TRAVERSAL = 1L;

    @Nested
    class CollectLivePositions {

        @Test
        void collectsNoPositionsWithoutASectorToRead() {

            assertThat(collectPositionsUnder(null, FORCED_ONTO_MAP))
                .isEmpty();
        }

        @Test
        void keysEachDrawnSystemsLivePositionByItsKey() {

            var sector = buildUnroutedSectorOf(
                buildSystemAt("a", 3f, 4f),
                buildSystemAt("b", -5f, 6f));

            var positions = collectPositionsUnder(sector, FORCED_ONTO_MAP);

            assertThat(positions)
                .containsOnlyKeys(keyOf("a"), keyOf("b"));
            assertThat(positions.get(keyOf("a")))
                .containsExactly(3.0, 4.0);
            assertThat(positions.get(keyOf("b")))
                .containsExactly(-5.0, 6.0);
        }

        @Test
        void seedsASiteForEachOfTwoSystemsSharingAnId() {
            // What the key address is for. A live modded sector lists several systems under one id
            // - vanilla's own deep space among them - and a point cloud gathered under ids is short
            // a site for each, which draws as a system with no cell on a map that cells every
            // neighbour it has.
            var positions = collectPositionsUnder(
                buildUnroutedSectorOfTwoSystemsSharingAnId(),
                FORCED_ONTO_MAP);

            assertThat(positions)
                .containsOnlyKeys(THE_FIRST_DEEP_SPACE, THE_SECOND_DEEP_SPACE);
            assertThat(positions.get(THE_SECOND_DEEP_SPACE))
                .containsExactly(-5.0, 6.0);
        }

        @Test
        void leavesOutASystemWithNoPositionToSeedACellAt() {
            // A site is a position, so a system without one cannot enter the partition however
            // the rule judges it - which is why the force override is on here.
            var positions = collectPositionsUnder(
                buildUnroutedSectorOf(buildSystemWithoutAPosition("a")),
                FORCED_ONTO_MAP);

            assertThat(positions)
                .isEmpty();
        }

        @Test
        void leavesOutASystemTheRuleDoesNotDraw() {
            // Unreachable, no visible star anchor and nobody living there, so nothing admits
            // it once the force override is off.
            var positions = collectPositionsUnder(
                buildUnroutedSectorOf(buildSystemAt("a", 3f, 4f)),
                MapVisibilityRules.BASE);

            assertThat(positions)
                .isEmpty();
        }

        @Test
        void admitsASystemOnItsOwnColonyReadAlone() {
            // The pass's own reading of the sector, and the only case that can show it was
            // made. This system has no other route onto the map, so it is drawn if and only if
            // the colony index behind the pass answered for it.
            var system = buildSystemAt("a", 3f, 4f);
            var sector = buildUnroutedSectorOf(system);

            listMarketsIn(sector, system, ColonyMarketFixture.buildVisibleColony(OWNING_FACTION));

            assertThat(collectPositionsUnder(sector, MapVisibilityRules.BASE))
                .containsOnlyKeys(keyOf("a"));
        }

        @Test
        void opensNoTraversalOfTheSystemListItsPassHasNotAlreadyMade() {
            // What holds a rebuild inside the one traversal the frame allows it. The band bake
            // resolves its systems off the same pass by id while this addresses them by key, so a
            // traversal opened here would be the second in a rebuild whichever of the two ran
            // first - and the bound is per call, so it would break on every full rebuild rather
            // than on an unlucky one.
            var pass = MapVisibilityPass.over(
                buildUnroutedSectorOf(buildSystemAt("a", 3f, 4f)),
                FORCED_ONTO_MAP);

            var counts = RecordedCapture
                .recordWhile(new RecordingProfiler(() -> FIXED_CLOCK_NANOS), () -> {
                    pass.sectorIndex().readSystemsById();
                    DrawnSystemPositions.collectLivePositions(pass);
                })
                .findNode(ProfileSection.UNSCOPED_COUNTS.getName());

            assertThat(ProfileCounts.readTotalOf(counts, SectorWalkCounters.SECTOR_WALKS))
                .isEqualTo(THE_PASSES_OWN_TRAVERSAL);
        }
    }

    @Nested
    class CollectLivePositionsById {

        @Test
        void keysEachDrawnSystemsLivePositionById() {

            var positions = collectPositionsByIdUnder(buildUnroutedSectorOf(
                buildSystemAt("a", 3f, 4f),
                buildSystemAt("b", -5f, 6f)));

            assertThat(positions)
                .containsOnlyKeys("a", "b");
            assertThat(positions.get("a"))
                .containsExactly(3.0, 4.0);
        }

        @Test
        void holdsTheFirstPositionOfTwoSystemsSharingAnId() {
            // What an id address costs, stated where a structure keyed that way takes it: the two
            // systems are one entry, holding the one every other id-keyed read of the sector
            // answers with.
            var positions = collectPositionsByIdUnder(buildUnroutedSectorOfTwoSystemsSharingAnId());

            assertThat(positions)
                .containsOnlyKeys("deep space");
            assertThat(positions.get("deep space"))
                .containsExactly(3.0, 4.0);
        }
    }

    // One walk over a pass opened the way a rebuild opens one: built per call and discarded
    // with it, which is what the production caller does - a kept pass would answer a second
    // walk off the sector the first one saw.
    private static Map<SystemKey, double[]> collectPositionsUnder(
            SectorAPI sector,
            MapVisibilityRules visibilityRules) {

        return DrawnSystemPositions.collectLivePositions(
            MapVisibilityPass.over(sector, visibilityRules));
    }

    // The same walk taken at the id address. Every case of that read is about the address rather
    // than the rule, so the force override is fixed here and none of them states it.
    private static Map<String, double[]> collectPositionsByIdUnder(SectorAPI sector) {
        return DrawnSystemPositions.collectLivePositionsById(
            MapVisibilityPass.over(sector, FORCED_ONTO_MAP));
    }

    // Two systems the sector lists under one id, apart in hyperspace and told apart by their
    // anchors alone - vanilla's own deep space pair, and the world both addresses answer
    // differently over.
    private static SectorAPI buildUnroutedSectorOfTwoSystemsSharingAnId() {
        return buildUnroutedSectorOf(
            buildKeyedSystemAt("deep space", "8b3", 3f, 4f),
            buildKeyedSystemAt("deep space", "38d53", -5f, 6f));
    }

    // The key a system posed with an id alone carries: a sector states no centre and no anchor for
    // one, and an arm it does not state is absent rather than missing.
    private static SystemKey keyOf(String systemId) {
        return new SystemKey(systemId, "", "");
    }

    // An unreachable system standing at a hyperspace position: no jump points, so only the
    // force override or somebody living there can put it on the map.
    private static StarSystemAPI buildSystemAt(String id, float x, float y) {
        return StarSystemFixture.placeSystemAt(buildSystemWithoutAPosition(id), x, y);
    }

    private static StarSystemAPI buildSystemWithoutAPosition(String id) {
        return closeTheJumpRoutesOf(StarSystemFixture.buildSystem(id));
    }

    // A system standing where another of the same id stands too, told apart by its anchor alone -
    // the shape a live modded sector lists and the one nothing but a key separates.
    private static StarSystemAPI buildKeyedSystemAt(
            String id,
            String anchorEntityId,
            float x,
            float y) {

        return StarSystemFixture.placeSystemAt(
            closeTheJumpRoutesOf(StarSystemFixture.buildKeyedSystem(id, null, anchorEntityId)),
            x,
            y);
    }

    // Leaves a posed system unreachable, so only the force override or somebody living there can
    // put it on the map - which is what lets a case decide admission by what it stages.
    private static StarSystemAPI closeTheJumpRoutesOf(StarSystemAPI system) {

        when(system.getJumpPoints())
            .thenReturn(List.of());

        return system;
    }

}
