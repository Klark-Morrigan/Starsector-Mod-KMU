package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.colonies.Colony;
import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit coverage for {@link HolderPass}: the construction guard, the two projections it offers over
 * one walk, and that the pass names the sector its own walk was opened over rather than one carried
 * beside it.
 *
 * <p>What each projection admits is settled in KMLib, where the rule lives. What is read here is
 * that the pass reaches the right one of them and holds both to the rule it was opened with - a
 * habitation read wired to the known listing would answer identically for every system holding no
 * derelict, which is nearly all of them.
 *
 * <p>The naming matters because every resolver behind the holder seam takes its sector from here.
 * A pass that could report one sector while answering colonies out of another would let a resolver
 * walk the systems of one sector and price them against a second - which with one sector in play
 * would show as nothing at all.
 *
 * <p>What the per-system colony read answers is covered where it is consumed, through the resolves
 * in the {@code base.politics} integration suites.
 */
final class HolderPassTest {

    // The "show all factions" reveal on: the fog lifted outright, and no gate held, which
    // is the widest rule any surface reads under.
    private static final ColonyVisibility UNDER_THE_REVEAL =
        new ColonyVisibility(true, Set.of());

    private static final String SYSTEM_ID = "corvus";

    // The one owner the projection cases read a colony back for.
    private static final FactionAPI HEGEMONY_FACTION = SectorPoliticsFixtures
        .buildFaction("hegemony");

    // The size every posed colony carries. The projection reads ownership and discovery, so a case
    // varying size would vary nothing it can see.
    private static final int COLONY_SIZE = 5;

    // The bloc the two posed owners fold into where a case reads presence per bloc rather than per
    // faction.
    private static final String ALLIANCE_ID = "alliance-1";

    private static final HolderGrouping ALLIED_HEGEMONY_AND_TRITACHYON = new HolderGrouping(
        Map.of("hegemony", ALLIANCE_ID, "tritachyon", ALLIANCE_ID),
        Map.of(ALLIANCE_ID, "hegemony"),
        Map.of(ALLIANCE_ID, "Allied Powers"));

    @Nested
    class Constructor {

        @Test
        void rejectsNullGrouping() {

            assertThatThrownBy(() ->
                    new HolderPass(null, ColonyVisibility.BASE_FOG, new SystemColoniesIndex(null)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullColonyVisibility() {
            // Required on the same terms as the other two, and for the same reason: the rule is
            // resolved once where the rebuild begins, so a null here is that one resolve having
            // gone wrong. Standing the fog in would answer it with a map that draws less than it
            // should and says nothing about why.
            assertThatThrownBy(() ->
                    new HolderPass(HolderGrouping.identity(), null, new SystemColoniesIndex(null)))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsNullColonies() {
            // A pass with no walk behind it would fault on the first system it read rather than
            // here, and a pass over a sector that cannot be reached is a different thing entirely -
            // an index over a null sector, which answers an empty set and is perfectly legal.
            assertThatThrownBy(() ->
                    new HolderPass(HolderGrouping.identity(), ColonyVisibility.BASE_FOG, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ReadKnownColoniesIn {

        @Test
        void withholdsAColonyThePlayerHasNotFound() {
            // The projection every display reader below takes, stated where it is named: a pass with
            // the fog in force reports the colonies the player may be shown and no others, so a
            // band, a fill and a box over one cell cannot each withhold a different set.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorPoliticsFixtures.buildUndiscoveredHiddenMarket(
                    SectorPoliticsFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var knownColonies = HolderPass
                .over(sector, ColonyVisibility.BASE_FOG, HolderGrouping.identity())
                .readKnownColoniesIn(SectorPoliticsFixtures.buildOnlySystem(sector));

            assertThat(knownColonies)
                .extracting(HolderPassTest::readColonyFactionId)
                .containsExactly("hegemony");
        }

        @Test
        void reportsAnUnfoundColonyWhereTheRevealLiftsTheFog() {
            // The same pass with the dev reveal on, which is the one knob the projection reads.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var knownColonies = HolderPass
                .over(sector, UNDER_THE_REVEAL, HolderGrouping.identity())
                .readKnownColoniesIn(SectorPoliticsFixtures.buildOnlySystem(sector));

            assertThat(knownColonies)
                .hasSize(1);
        }

        @Test
        void reportsNoColoniesForASystemThatIsNotThere() {
            // The null-system answer every read on the pass holds to, so a reader handed a system
            // the sector no longer lists is not obliged to guard before asking.
            assertThat(HolderPass
                    .over(mock(SectorAPI.class), ColonyVisibility.BASE_FOG, HolderGrouping.identity())
                    .readKnownColoniesIn(null))
                .isEmpty();
        }
    }

    @Nested
    class ReadInhabitingColoniesIn {

        @Test
        void leavesOutADerelictTheKnownListingNames() {
            // The one case that can tell the two reads apart. Both run over the same walk under
            // the same rule, so a habitation read that had been wired to the known projection -
            // a copy-paste away - would pass every other case in this class.
            var colony = SectorPoliticsFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE);
            var derelict = SectorPoliticsFixtures.buildAbandonedStationMarket(
                SectorPoliticsFixtures.buildFaction("neutral"),
                COLONY_SIZE);

            var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID, colony);
            var system = SectorPoliticsFixtures.buildOnlySystem(sector);

            // The derelict reaches the walk through the entity side, as a vanilla hulk does: the
            // economy never lists one, so posing it in the economy would pose a market the sector
            // does not hold.
            SectorPoliticsFixtures.placeMarketsOnSystemEntities(system, colony, derelict);

            var pass = HolderPass
                .over(sector, ColonyVisibility.BASE_FOG, HolderGrouping.identity());

            assertThat(pass.readKnownColoniesIn(system))
                .extracting(HolderPassTest::readColonyFactionId)
                .containsExactlyInAnyOrder("hegemony", "neutral");
            assertThat(pass.readInhabitingColoniesIn(system))
                .extracting(HolderPassTest::readColonyFactionId)
                .containsExactly("hegemony");
        }

        @Test
        void withholdsAColonyThePlayerHasNotFound() {
            // Habitation is the known projection minus the derelicts, so it is held to the pass's
            // rule exactly as the listing is. A read that reached past the fog would settle a cell
            // on a colony the box beside it may not name.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorPoliticsFixtures.buildUndiscoveredHiddenMarket(
                    SectorPoliticsFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var inhabitingColonies = HolderPass
                .over(sector, ColonyVisibility.BASE_FOG, HolderGrouping.identity())
                .readInhabitingColoniesIn(SectorPoliticsFixtures.buildOnlySystem(sector));

            assertThat(inhabitingColonies)
                .extracting(HolderPassTest::readColonyFactionId)
                .containsExactly("hegemony");
        }

        @Test
        void reportsAnUnfoundColonyWhereTheRevealLiftsTheFog() {
            // The same knob the listing reads, so a map showing every faction cannot draw a cell
            // as settled on one read and empty on the other.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var inhabitingColonies = HolderPass
                .over(sector, UNDER_THE_REVEAL, HolderGrouping.identity())
                .readInhabitingColoniesIn(SectorPoliticsFixtures.buildOnlySystem(sector));

            assertThat(inhabitingColonies)
                .hasSize(1);
        }

        @Test
        void reportsNoColoniesForASystemThatIsNotThere() {
            // The null-system answer every read on the pass holds to, stated here too so the newer
            // read cannot be the one that faults where its siblings answer.
            assertThat(HolderPass
                    .over(
                        mock(SectorAPI.class),
                        ColonyVisibility.BASE_FOG,
                        HolderGrouping.identity())
                    .readInhabitingColoniesIn(null))
                .isEmpty();
        }
    }

    @Nested
    class ReadKnownColonyFactionIds {

        @Test
        void namesEveryOwnerTheProjectionHolds() {
            // Who is in the system, whatever the economy makes of them: the registered colony's
            // owner and the owner of a station the listing never held both count, which is what
            // lets a listing built on this name every faction a band counting the same projection
            // draws a run for.
            var registeredColony = SectorPoliticsFixtures.buildVisibleMarket(
                HEGEMONY_FACTION,
                COLONY_SIZE);
            var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID, registeredColony);

            SectorPoliticsFixtures.placeMarketsOnSystemEntities(
                SectorPoliticsFixtures.buildOnlySystem(sector),
                registeredColony,
                SectorPoliticsFixtures.buildVisibleMarket(
                    SectorPoliticsFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            assertThat(HolderPass
                    .over(sector, ColonyVisibility.BASE_FOG, HolderGrouping.identity())
                    .readKnownColonyFactionIds(SectorPoliticsFixtures.buildOnlySystem(sector)))
                .containsExactlyInAnyOrder("hegemony", "tritachyon");
        }

        @Test
        void namesAnOwnerOnceHoweverManyColoniesItHolds() {
            // A listing names a faction once, so the owners come back as a set: a faction with two
            // colonies here is one faction present, not two.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorPoliticsFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE));

            assertThat(HolderPass
                    .over(sector, ColonyVisibility.BASE_FOG, HolderGrouping.identity())
                    .readKnownColonyFactionIds(SectorPoliticsFixtures.buildOnlySystem(sector)))
                .containsExactly("hegemony");
        }

        @Test
        void withholdsAnOwnerThePlayerHasOnlyUnfoundColoniesOf() {
            // The fog reaches presence as it reaches the colonies themselves: naming a faction over
            // a base the player has not found is the one thing the projection exists to prevent,
            // and the dev reveal states it like anything else.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var system = SectorPoliticsFixtures.buildOnlySystem(sector);

            assertThat(HolderPass
                    .over(sector, ColonyVisibility.BASE_FOG, HolderGrouping.identity())
                    .readKnownColonyFactionIds(system))
                .isEmpty();
            assertThat(HolderPass
                    .over(sector, UNDER_THE_REVEAL, HolderGrouping.identity())
                    .readKnownColonyFactionIds(system))
                .containsExactly("hegemony");
        }

        @Test
        void namesNobodyForASystemThatIsNotThere() {

            assertThat(HolderPass
                    .over(mock(SectorAPI.class), ColonyVisibility.BASE_FOG, HolderGrouping.identity())
                    .readKnownColonyFactionIds(null))
                .isEmpty();
        }
    }

    @Nested
    class ReadKnownColonyBlocIds {

        @Test
        void foldsAlliedOwnersIntoTheOneBlocTheyPaintAs() {
            // Presence is asked by surfaces that decide per bloc - the spotlight's fill, the band's
            // runs - so two allies in one system are one bloc present there, not two.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorPoliticsFixtures.buildVisibleMarket(
                    SectorPoliticsFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            assertThat(HolderPass
                    .over(sector, ColonyVisibility.BASE_FOG, ALLIED_HEGEMONY_AND_TRITACHYON)
                    .readKnownColonyBlocIds(SectorPoliticsFixtures.buildOnlySystem(sector)))
                .containsExactly(ALLIANCE_ID);
        }

        @Test
        void leavesOutAnOwnerNoBlocCanBeNamedFor() {
            // A colony a mod hung on a faction with no id. The same rule every per-bloc fold on the
            // map applies: a nameless key would travel on as a bloc, and a surface asked to paint
            // or grey one has nothing to name it by.
            var sector = SectorPoliticsFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorPoliticsFixtures.buildVisibleMarket(
                    SectorPoliticsFixtures.buildFaction(null),
                    COLONY_SIZE));

            assertThat(HolderPass
                    .over(sector, ColonyVisibility.BASE_FOG, HolderGrouping.identity())
                    .readKnownColonyBlocIds(SectorPoliticsFixtures.buildOnlySystem(sector)))
                .containsExactly("hegemony");
        }
    }

    @Nested
    class Sector {

        @Test
        void namesTheSectorTheWalkWasOpenedOver() {

            var sectorMock = mock(SectorAPI.class);

            assertThat(HolderPass.over(sectorMock, ColonyVisibility.BASE_FOG, HolderGrouping.identity()).sector())
                .isSameAs(sectorMock);
        }

        @Test
        void namesNoSectorForAPassOverNone() {
            // The unreachable-sector case every resolve already guards on, reported rather than
            // stood in for.
            assertThat(HolderPass.over(null, ColonyVisibility.BASE_FOG, HolderGrouping.identity()).sector())
                .isNull();
        }
    }

    // A colony's owner, which is what every projection case here asserts on. Named once rather
    // than spelled at each assertion so a case reads as the set of owners it expects rather than
    // as the walk down to an id.
    private static String readColonyFactionId(Colony colony) {
        return colony.market().getFaction().getId();
    }
}
