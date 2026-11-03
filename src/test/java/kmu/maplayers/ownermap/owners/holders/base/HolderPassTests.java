package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.DecivilisedPlanetFixtures;
import kmu.starsector.SectorFactionFixtures;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_DEV_REVEAL;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.DecivilisedColonyHabitation.COUNTS_AS_POPULATED;
import static kmu.maplayers.ownermap.owners.holders.base.DecivilisedColonyHabitation.COUNTS_AS_UNPOPULATED;
import static kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture.GROUP_BLOC_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link HolderPass}: the construction guard, the entry opening under the live
 * settings, the two projections it offers over one walk, the habitation value the cell and the
 * spotlight both answer off, the walk and economy guards every resolve shares, that the pass
 * names the sector its own walk was opened over rather than one carried beside it, and the one
 * palette read it colours blocs through.
 *
 * <p>What each projection admits is settled in KMLib, where the rule lives. What is read here is
 * that the pass reaches the right one of them and holds both to the rule it was opened with - a
 * habitation read wired to the known listing would answer identically for every system holding no
 * derelict, which is nearly all of them.
 *
 * <p>{@link DecivilisedColonyHabitation} is read here rather than downstream for the same reason:
 * this is the one read it lands on, and the surfaces that move with it - the cell, the picker, the
 * band, the stats - are all folds of what these cases assert.
 *
 * <p>The naming matters because every resolver behind the holder seam takes its sector from here.
 * A pass that could report one sector while answering colonies out of another would let a resolver
 * walk the systems of one sector and price them against a second - which with one sector in play
 * would show as nothing at all.
 *
 * <p>What the per-system colony read answers is covered where it is consumed, through the resolves
 * in the {@code ownermap.owners} integration suites.
 */
final class HolderPassTests {

    private static final String SYSTEM_ID = "corvus";

    // The ID a live sector really lists several systems under, for the case posing such a pair.
    private static final String SHARED_SYSTEM_ID = "deep space";

    // The one owner the projection cases read a colony back for.
    private static final FactionAPI HEGEMONY_FACTION = SectorOwnershipFixtures
        .buildFaction("hegemony");

    // The size every posed colony carries where a case is not about size. The projections read
    // ownership and discovery, so only the habitation fold's own sums vary it.
    private static final int COLONY_SIZE = 5;

    // The two positions the habitation rule takes, under the one fog, for the cases that are about
    // the rule itself. Written out here rather than taken from the shared fixture, whose readings
    // are the ones a case poses when it is about something else.
    private static final ColonyReadRules DECIVILISED_POPULATED =
        new ColonyReadRules(BASE_FOG, COUNTS_AS_POPULATED);

    private static final ColonyReadRules DECIVILISED_UNPOPULATED =
        new ColonyReadRules(BASE_FOG, COUNTS_AS_UNPOPULATED);

    // The two posed owners folded into one bloc, for the cases reading presence per bloc rather than
    // per faction.
    private static final HolderGrouping ALLIED_HEGEMONY_AND_TRITACHYON =
        HolderGroupingFixture.buildGroupOf("hegemony", "tritachyon");

    // The two shades the palette cases' faction is authored in, distinct so a swapped slot shows.
    private static final Color PALETTE_BRIGHT = new Color(140, 160, 220);
    private static final Color PALETTE_DARK = new Color(40, 60, 120);

    private final StaticSeams seams = new StaticSeams();

    @AfterEach
    void closeSeams() {
        seams.closeEverySeam();
    }

    @Nested
    class Constructor {

        @Test
        void rejectsNullGrouping() {

            // ARRANGE
            var sectorIndex = new SectorPassIndex(null);
            var expectedMessage = "grouping";

            // ACT
            ThrowingCallable actualCall = () -> new HolderPass(sectorIndex, UNDER_THE_FOG, null);

            // ASSERT
            assertThatThrownBy(actualCall)
                .isInstanceOf(NullPointerException.class)
                .hasMessage(expectedMessage);
        }

        // Required on the same terms as the other two, and for the same reason: the rules are
        // resolved once where the rebuild begins, so a null here is that one resolve having
        // gone wrong. Standing the fog in would answer it with a map that draws less than it
        // should and says nothing about why.
        @Test
        void rejectsNullColonyReadRules() {

            // ARRANGE
            var sectorIndex = new SectorPassIndex(null);
            var expectedMessage = "colonyReadRules";

            // ACT
            ThrowingCallable actualCall = () -> new HolderPass(sectorIndex, null, HolderGrouping.identity());

            // ASSERT
            assertThatThrownBy(actualCall)
                .isInstanceOf(NullPointerException.class)
                .hasMessage(expectedMessage);
        }

        // A pass with no walk behind it would fault on the first system it read rather than
        // here, and a pass over a sector that cannot be reached is a different thing entirely -
        // an index over a null sector, which answers an empty set and is perfectly legal.
        @Test
        void rejectsNullSectorIndex() {

            // ARRANGE
            var expectedMessage = "sectorIndex";

            // ACT
            ThrowingCallable actualCall = () -> new HolderPass(null, UNDER_THE_FOG, HolderGrouping.identity());

            // ASSERT
            assertThatThrownBy(actualCall)
                .isInstanceOf(NullPointerException.class)
                .hasMessage(expectedMessage);
        }
    }

    @Nested
    class ReadFromLunaSettings {

        // The one entry with no rules on the call, so the settings read is all the pass has to
        // reach. Both halves are posed away from the shipped reading, so a pass taking either
        // from anywhere else reads this sector differently: the reveal shows on the pass's
        // knowledge, and the unpopulated position empties the system of its decivilised world.
        @Test
        void opensUnderTheLiveColonyRulesAndTheHandedGrouping() {

            // ARRANGE
            seams
                .openSeam(ColonyReadRules.class)
                .when(ColonyReadRules::readFromLunaSettings)
                .thenReturn(new ColonyReadRules(UNDER_THE_REVEAL, COUNTS_AS_UNPOPULATED));

            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var expectedRule = UNDER_THE_REVEAL;

            // ACT
            var actualPass = HolderPass.readFromLunaSettings(sector, ALLIED_HEGEMONY_AND_TRITACHYON);
            var actualInhabitingColonies = actualPass.readInhabitingColoniesIn(system);

            // ASSERT
            assertThat(actualPass.colonyKnowledge().rule())
                .isEqualTo(expectedRule);
            assertThat(actualInhabitingColonies)
                .isEmpty();
            assertThat(actualPass.grouping())
                .isSameAs(ALLIED_HEGEMONY_AND_TRITACHYON);
            assertThat(actualPass.sector())
                .isSameAs(sector);
        }
    }

    @Nested
    class ReadKnownColoniesIn {

        // The projection every display reader below takes, stated where it is named: a pass with
        // the fog in force reports the colonies the player may be shown and no others, so a
        // band, a fill and a box over one cell cannot each withhold a different set.
        @Test
        void withholdsAColonyThePlayerHasNotFound() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedFactionIds = List.of("hegemony");

            // ACT
            var actualKnownColonies = pass.readKnownColoniesIn(system);

            // ASSERT
            assertThat(actualKnownColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyElementsOf(expectedFactionIds);
        }

        // The same pass with the dev reveal on, which is the one knob the projection reads.
        @Test
        void reportsAnUndiscoveredColonyWhereTheRevealLiftsTheFog() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_DEV_REVEAL, HolderGrouping.identity());
            var expectedColonyCount = 1;

            // ACT
            var actualKnownColonies = pass.readKnownColoniesIn(system);

            // ASSERT
            assertThat(actualKnownColonies)
                .hasSize(expectedColonyCount);
        }

        // The null-system answer every read on the pass holds to, so a reader handed a system
        // the sector no longer lists is not obliged to guard before asking.
        @Test
        void reportsNoColoniesForASystemThatIsNotThere() {

            // ARRANGE
            var pass = HolderPass.createOver(
                mock(SectorAPI.class),
                UNDER_THE_FOG,
                HolderGrouping.identity());

            // ACT
            var actualKnownColonies = pass.readKnownColoniesIn(null);

            // ASSERT
            assertThat(actualKnownColonies)
                .isEmpty();
        }
    }

    @Nested
    class ReadInhabitingColoniesIn {

        // The one case that can tell the two reads apart. Both run over the same walk under
        // the same rule, so a habitation read that had been wired to the known projection -
        // a copy-paste away - would pass every other case in this class.
        @Test
        void leavesOutADerelictTheKnownListingNames() {

            // ARRANGE
            var colony = SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE);
            var derelict = SectorOwnershipFixtures.buildAbandonedStationMarket(COLONY_SIZE);
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID, colony);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            // The derelict reaches the walk through the entity side, as a vanilla one does: the
            // economy never lists one, so posing it in the economy would pose a market the sector
            // does not hold.
            SectorOwnershipFixtures.placeMarketsOnSystemEntities(system, colony, derelict);

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedKnownFactionIds = List.of("hegemony", "neutral");
            var expectedInhabitingFactionIds = List.of("hegemony");

            // ACT
            var actualKnownColonies = pass.readKnownColoniesIn(system);
            var actualInhabitingColonies = pass.readInhabitingColoniesIn(system);

            // ASSERT
            assertThat(actualKnownColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyInAnyOrderElementsOf(expectedKnownFactionIds);

            assertThat(actualInhabitingColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyElementsOf(expectedInhabitingFactionIds);
        }

        // The second rule the pass carries, and this read is the only place it lands. A world
        // people are still on is an owned cell under one position and empty space under the other,
        // which is the whole of the choice - so both are read back here.
        @Test
        void leavesOutADecivilisedWorldWhereSuchAWorldCountsAsUnpopulated() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var populatedPass = HolderPass.createOver(sector, DECIVILISED_POPULATED, HolderGrouping.identity());
            var unpopulatedPass = HolderPass.createOver(sector, DECIVILISED_UNPOPULATED, HolderGrouping.identity());
            var expectedPopulatedFactionIds = List.of("neutral");

            // ACT
            var actualPopulatedColonies = populatedPass.readInhabitingColoniesIn(system);
            var actualUnpopulatedColonies = unpopulatedPass.readInhabitingColoniesIn(system);

            // ASSERT
            assertThat(actualPopulatedColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyElementsOf(expectedPopulatedFactionIds);

            assertThat(actualUnpopulatedColonies)
                .isEmpty();
        }

        // The rule names one kind and reaches nothing else in the system, so a faction living
        // beside such a world goes on inhabiting its system whichever position the pass takes.
        // A rule written as "what this system amounts to" rather than over the one kind would
        // take the colony with it.
        @Test
        void keepsALiveColonyStandingBesideADecivilisedWorldUnderEitherRule() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var populatedPass = HolderPass.createOver(sector, DECIVILISED_POPULATED, HolderGrouping.identity());
            var unpopulatedPass = HolderPass.createOver(sector, DECIVILISED_UNPOPULATED, HolderGrouping.identity());
            var expectedPopulatedFactionIds = List.of("hegemony", "neutral");
            var expectedUnpopulatedFactionIds = List.of("hegemony");

            // ACT
            var actualPopulatedColonies = populatedPass.readInhabitingColoniesIn(system);
            var actualUnpopulatedColonies = unpopulatedPass.readInhabitingColoniesIn(system);

            // ASSERT
            assertThat(actualPopulatedColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyInAnyOrderElementsOf(expectedPopulatedFactionIds);

            assertThat(actualUnpopulatedColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyElementsOf(expectedUnpopulatedFactionIds);
        }

        // Habitation is the known projection minus the derelicts, so it is held to the pass's
        // rule exactly as the listing is. A read that reached past the fog would settle a cell
        // on a colony the box beside it may not name.
        @Test
        void withholdsAColonyThePlayerHasNotFound() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedFactionIds = List.of("hegemony");

            // ACT
            var actualInhabitingColonies = pass.readInhabitingColoniesIn(system);

            // ASSERT
            assertThat(actualInhabitingColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyElementsOf(expectedFactionIds);
        }

        // The same knob the listing reads, so a map showing every faction cannot draw a cell
        // as settled on one read and empty on the other.
        @Test
        void reportsAnUndiscoveredColonyWhereTheRevealLiftsTheFog() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_DEV_REVEAL, HolderGrouping.identity());
            var expectedColonyCount = 1;

            // ACT
            var actualInhabitingColonies = pass.readInhabitingColoniesIn(system);

            // ASSERT
            assertThat(actualInhabitingColonies)
                .hasSize(expectedColonyCount);
        }

        // The null-system answer every read on the pass holds to, stated here too so the newer
        // read cannot be the one that faults where its siblings answer.
        @Test
        void reportsNoColoniesForASystemThatIsNotThere() {

            // ARRANGE
            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());

            // ACT
            var actualInhabitingColonies = pass.readInhabitingColoniesIn(null);

            // ASSERT
            assertThat(actualInhabitingColonies)
                .isEmpty();
        }
    }

    @Nested
    class ReadKnownColonyFactionIds {

        // Who is in the system, whatever the economy makes of them: the registered colony's
        // owner and the owner of a station the listing never held both count, which is what
        // lets a listing built on this name every faction a band counting the same projection
        // draws a run for.
        @Test
        void namesEveryOwnerTheProjectionHolds() {

            // ARRANGE
            var registeredColony = SectorOwnershipFixtures.buildVisibleMarket(
                HEGEMONY_FACTION,
                COLONY_SIZE);

            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID, registeredColony);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            SectorOwnershipFixtures.placeMarketsOnSystemEntities(
                system,
                registeredColony,
                SectorOwnershipFixtures.buildVisibleMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedFactionIds = List.of("hegemony", "tritachyon");

            // ACT
            var actualFactionIds = pass.readKnownColonyFactionIds(system);

            // ASSERT
            assertThat(actualFactionIds)
                .containsExactlyInAnyOrderElementsOf(expectedFactionIds);
        }

        // A listing names a faction once, so the owners come back as a set: a faction with two
        // colonies here is one faction present, not two.
        @Test
        void namesAnOwnerOnceHoweverManyColoniesItHolds() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedFactionIds = List.of("hegemony");

            // ACT
            var actualFactionIds = pass.readKnownColonyFactionIds(system);

            // ASSERT
            assertThat(actualFactionIds)
                .containsExactlyElementsOf(expectedFactionIds);
        }

        // The fog reaches presence as it reaches the colonies themselves: naming a faction over
        // a base the player has not found is the one thing the projection exists to prevent,
        // and the dev reveal states it like anything else.
        @Test
        void withholdsAnOwnerHoldingOnlyUndiscoveredColonies() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var foggedPass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var revealedPass = HolderPass.createOver(sector, UNDER_THE_DEV_REVEAL, HolderGrouping.identity());
            var expectedRevealedFactionIds = List.of("hegemony");

            // ACT
            var actualFoggedFactionIds = foggedPass.readKnownColonyFactionIds(system);
            var actualRevealedFactionIds = revealedPass.readKnownColonyFactionIds(system);

            // ASSERT
            assertThat(actualFoggedFactionIds)
                .isEmpty();
            assertThat(actualRevealedFactionIds)
                .containsExactlyElementsOf(expectedRevealedFactionIds);
        }

        @Test
        void namesNobodyForASystemThatIsNotThere() {

            // ARRANGE
            var pass = HolderPass.createOver(
                mock(SectorAPI.class),
                UNDER_THE_FOG,
                HolderGrouping.identity());

            // ACT
            var actualFactionIds = pass.readKnownColonyFactionIds(null);

            // ASSERT
            assertThat(actualFactionIds)
                .isEmpty();
        }
    }

    @Nested
    class ReadHabitationIn {

        // Presence is asked by surfaces that decide per bloc - the spotlight's fill, the band's
        // runs - so two allies in one system are one bloc living there, not two.
        @Test
        void foldsAlliedOwnersIntoTheOneBlocTheyPaintAs() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildVisibleMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, ALLIED_HEGEMONY_AND_TRITACHYON);
            var expectedBlocIds = List.of(GROUP_BLOC_ID);

            // ACT
            var actualHabitation = pass.readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation.blocIds())
                .containsExactlyElementsOf(expectedBlocIds);
        }

        // The picker's size and the band's colonies come off this fold as well, so a bloc's
        // presence and what it lives on are one set of colonies rather than two selections that
        // could differ by one. Allied members sum into the bloc they paint as, as their weights
        // do, each colony still held by the member owning it.
        @Test
        void sumsWhatEachBlocLivesOnThere() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, 5),
                SectorOwnershipFixtures.buildVisibleMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    3));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, ALLIED_HEGEMONY_AND_TRITACHYON);
            var expectedHabitation = entry(
                GROUP_BLOC_ID,
                new BlocHabitation(List.of("hegemony", "tritachyon"), 8));

            // ACT
            var actualHabitation = pass.readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation.habitationByBlocId())
                .containsExactly(expectedHabitation);
        }

        // The fold beneath the grouping. An owner's colonies are summed before any bloc is
        // named, so a faction living on two places in one system reads as the whole of what it
        // lives on rather than as whichever of them the walk reached last.
        @Test
        void sumsSeveralColoniesOfOneOwnerThere() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, 5),
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, 3));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedHabitation = entry(
                "hegemony",
                new BlocHabitation(List.of("hegemony", "hegemony"), 8));

            // ACT
            var actualHabitation = pass.readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation.habitationByBlocId())
                .containsExactly(expectedHabitation);
        }

        // A market that keeps its faction object but stores no faction ID. The fold reads the
        // ID, as every other owner read on the map does, so the colony lands under no bloc -
        // and it still counts as somebody living there.
        @Test
        void countsAColonyWhoseMarketNamesNoFactionIdAsNobodys() {

            // ARRANGE
            var ownerless = SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE);

            when(ownerless.getFactionId())
                .thenReturn(null);

            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                ownerless);

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedHabitation = entry(
                "hegemony",
                new BlocHabitation(List.of("hegemony"), COLONY_SIZE));

            var expectedColonyCount = 2;

            // ACT
            var actualHabitation = pass.readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation.habitationByBlocId())
                .containsExactly(expectedHabitation);
            assertThat(actualHabitation.colonies())
                .hasSize(expectedColonyCount);
        }

        // Two such markets in one system. The fold holds each colony by its owner's ID, and two
        // missing IDs cannot be ordered against each other, so both are left out before the fold
        // rather than faulting the system's whole habitation.
        @Test
        void foldsSeveralColoniesWhoseMarketsNameNoFactionIdAsNobodys() {

            // ARRANGE
            var firstOwnerless = SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE);
            var secondOwnerless = SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE);

            when(firstOwnerless.getFactionId())
                .thenReturn(null);
            when(secondOwnerless.getFactionId())
                .thenReturn(null);

            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID, firstOwnerless, secondOwnerless);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedColonyCount = 2;

            // ACT
            var actualHabitation = pass.readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation.habitationByBlocId())
                .isEmpty();
            assertThat(actualHabitation.colonies())
                .hasSize(expectedColonyCount);
        }

        // A colony a mod hung on a faction with no ID. The same rule every per-bloc fold on the
        // map applies: a nameless key would travel on as a bloc, and a surface asked to paint
        // or grey one has nothing to name it by.
        @Test
        void leavesOutAnOwnerNoBlocCanBeNamedFor() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildVisibleMarket(
                    SectorOwnershipFixtures.buildFaction(null),
                    COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedBlocIds = List.of("hegemony");

            // ACT
            var actualHabitation = pass.readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation.blocIds())
                .containsExactlyElementsOf(expectedBlocIds);
        }

        // The case the whole value exists for. The listing names the derelict's owner, and nobody
        // lives on it - so the blocs come back empty beside a habitation that has nothing in
        // it, which is what keeps the spotlight from sparing a cell the map draws as empty
        // space.
        @Test
        void namesNoBlocForASystemHoldingOnlyADerelict() {

            // ARRANGE
            var derelict = SectorOwnershipFixtures.buildAbandonedStationMarket(COLONY_SIZE);
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            // Through the entity side, as a vanilla derelict arrives: the economy never lists one.
            SectorOwnershipFixtures.placeMarketsOnSystemEntities(system, derelict);

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedFactionIds = List.of("neutral");

            // ACT
            var actualHabitation = pass.readHabitationIn(system);
            var actualFactionIds = pass.readKnownColonyFactionIds(system);

            // ASSERT
            assertThat(actualFactionIds)
                .containsExactlyElementsOf(expectedFactionIds);
            assertThat(actualHabitation.blocIds())
                .isEmpty();
            assertThat(actualHabitation.hasInhabitingColony())
                .isFalse();
        }

        // The value the cell and the spotlight answer off, which is where the rule has to
        // land for either of them to move. The world goes on being listed and named in a box;
        // what changes is that no bloc lives there, so the cell is empty space and a
        // spotlight over it has no cell to light.
        @Test
        void namesNoBlocForASystemHoldingOnlyADecivilisedWorldCountedAsUnpopulated() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var pass = HolderPass.createOver(sector, DECIVILISED_UNPOPULATED, HolderGrouping.identity());
            var expectedFactionIds = List.of("neutral");

            // ACT
            var actualHabitation = pass.readHabitationIn(system);
            var actualFactionIds = pass.readKnownColonyFactionIds(system);

            // ASSERT
            assertThat(actualFactionIds)
                .containsExactlyElementsOf(expectedFactionIds);
            assertThat(actualHabitation.blocIds())
                .isEmpty();
            assertThat(actualHabitation.hasInhabitingColony())
                .isFalse();
        }

        // The other position, read off the same value: the fold is taken from the projection
        // above, so the cell the rule leaves empty in the case before this one is an owned cell
        // here without either surface being told which rule was in force.
        @Test
        void namesTheWorldsOwnerForThatSystemWhereSuchAWorldCountsAsPopulated() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var pass = HolderPass.createOver(sector, DECIVILISED_POPULATED, HolderGrouping.identity());
            var expectedBlocIds = List.of("neutral");

            // ACT
            var actualHabitation = pass.readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation.blocIds())
                .containsExactlyElementsOf(expectedBlocIds);
        }

        // Two readers ask this per system and they run in separate walks of the sector - the
        // filter's holder resolve for the blocs, the inhabitation scan for the emptiness - so
        // without the memo every filtered rebuild projects and folds the whole sector twice.
        // Asserted as identity, since an equal value worked out again is exactly the repeat
        // this exists to stop.
        @Test
        void worksOutOneSystemsHabitationOnceForTheWholePass() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE));

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);
            var firstHabitation = pass.readHabitationIn(system);

            // ACT
            var actualHabitation = pass.readHabitationIn(system);

            // ASSERT
            assertThat(actualHabitation)
                .isSameAs(firstHabitation);
        }

        // The defect a memo keyed on the ID carries, on the one layer whose whole output is
        // who lives where: a sector holds two systems under one ID, so the pair is a single
        // entry and the second system is handed the first's blocs. The key separates them, an
        // anchor ID being minted per system - and it is the address the colony memo beneath
        // already reads, so the two cannot disagree about what one system is.
        @Test
        void answersEachOfTwoSystemsSharingAnIdItsOwnInhabitants() {

            // ARRANGE
            var first = StarSystemFixture.buildKeyedSystem(SHARED_SYSTEM_ID, null, "8b3");
            var second = StarSystemFixture.buildKeyedSystem(SHARED_SYSTEM_ID, null, "38d53");

            var sector = SectorOwnershipFixtures.buildSectorHoldingSystems(
                List.of(),
                SectorOwnershipFixtures.listMarketsIn(
                    first,
                    SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE)),
                SectorOwnershipFixtures.listMarketsIn(
                    second,
                    SectorOwnershipFixtures.buildVisibleMarket(
                        SectorOwnershipFixtures.buildFaction("tritachyon"),
                        COLONY_SIZE)));

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedFirstBlocIds = List.of("hegemony");
            var expectedSecondBlocIds = List.of("tritachyon");

            // ACT
            var actualFirstHabitation = pass.readHabitationIn(first);
            var actualSecondHabitation = pass.readHabitationIn(second);

            // ASSERT
            assertThat(actualFirstHabitation.blocIds())
                .containsExactlyElementsOf(expectedFirstBlocIds);
            assertThat(actualSecondHabitation.blocIds())
                .containsExactlyElementsOf(expectedSecondBlocIds);
        }

        // A system the sector names with nothing has no key to remember it by. It is resolved
        // afresh rather than pooled, which costs a fold a later ask would have saved - the
        // honest price, since a shared key would hand one system's blocs to another.
        @Test
        void keepsTwoUnkeyableSystemsApart() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWithSystems(
                List.of(),
                SectorOwnershipFixtures.listSystemMarkets(
                    null,
                    SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE)),
                SectorOwnershipFixtures.listSystemMarkets(null));

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var systems = sector.getStarSystems();
            var expectedFirstBlocIds = List.of("hegemony");

            // ACT
            var actualFirstHabitation = pass.readHabitationIn(systems.get(0));
            var actualSecondHabitation = pass.readHabitationIn(systems.get(1));

            // ASSERT
            assertThat(actualFirstHabitation.blocIds())
                .containsExactlyElementsOf(expectedFirstBlocIds);
            assertThat(actualSecondHabitation.blocIds())
                .isEmpty();
        }

        // The null-system answer every read on the pass holds to, stated for the value too so a
        // reader handed a system the sector no longer lists is not obliged to guard first.
        @Test
        void reportsNobodyLivingInASystemThatIsNotThere() {

            // ARRANGE
            var pass = HolderPass.createOver(
                mock(SectorAPI.class),
                UNDER_THE_FOG,
                HolderGrouping.identity());

            // ACT
            var actualHabitation = pass.readHabitationIn(null);

            // ASSERT
            assertThat(actualHabitation.hasInhabitingColony())
                .isFalse();
            assertThat(actualHabitation.blocIds())
                .isEmpty();
        }
    }

    @Nested
    class ReadSystems {

        // Stated so a resolve can walk without guarding the unreachable sector first.
        @Test
        void walksNothingForAPassOverNoSector() {

            // ARRANGE
            var pass = HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity());

            // ACT
            var actualSystems = pass.readSystems();

            // ASSERT
            assertThat(actualSystems)
                .isEmpty();
        }

        @Test
        void walksTheSectorsSystemsInItsOwnOrder() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWithSystems(
                List.of(),
                SectorOwnershipFixtures.listSystemMarkets("corvus"),
                SectorOwnershipFixtures.listSystemMarkets("askonia"));

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var expectedSystemIds = List.of("corvus", "askonia");

            // ACT
            var actualSystems = pass.readSystems();

            // ASSERT
            assertThat(actualSystems)
                .extracting(StarSystemAPI::getId)
                .containsExactlyElementsOf(expectedSystemIds);
        }
    }

    @Nested
    class CanReadEconomy {

        @Test
        void isFalseForAPassOverNoSector() {

            // ARRANGE
            var pass = HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity());

            // ACT
            var actualCanRead = pass.canReadEconomy();

            // ASSERT
            assertThat(actualCanRead)
                .isFalse();
        }

        // The mid-load sector: it has a system to walk, so only the economy check stops a
        // weighing resolve reporting an empty sector there.
        @Test
        void isFalseForASectorWhoseEconomyIsNotUpYet() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildEconomylessSectorWithSystem(SYSTEM_ID);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());

            // ACT
            var actualCanRead = pass.canReadEconomy();

            // ASSERT
            assertThat(actualCanRead)
                .isFalse();
        }

        @Test
        void isTrueForASectorWithItsEconomyUp() {

            // ARRANGE
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());

            // ACT
            var actualCanRead = pass.canReadEconomy();

            // ASSERT
            assertThat(actualCanRead)
                .isTrue();
        }
    }

    @Nested
    class Sector {

        @Test
        void namesTheSectorTheWalkWasOpenedOver() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var pass = HolderPass.createOver(sectorMock, UNDER_THE_FOG, HolderGrouping.identity());

            // ACT
            var actualSector = pass.sector();

            // ASSERT
            assertThat(actualSector)
                .isSameAs(sectorMock);
        }

        // The unreachable-sector case every resolve already guards on, reported rather than
        // stood in for.
        @Test
        void namesNoSectorForAPassOverNone() {

            // ARRANGE
            var pass = HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity());

            // ACT
            var actualSector = pass.sector();

            // ASSERT
            assertThat(actualSector)
                .isNull();
        }
    }

    @Nested
    class Palettes {

        // Every resolve and band over one rebuild reads a bloc's shades through the same object,
        // which is what lets each bloc be looked up once for all of them.
        @Test
        void answersOnePaletteReadForThePass() {

            // ARRANGE
            var pass = HolderPass.createOver(
                mock(SectorAPI.class),
                UNDER_THE_FOG,
                HolderGrouping.identity());

            var firstPalettes = pass.palettes();

            // ACT
            var actualPalettes = pass.palettes();

            // ASSERT
            assertThat(actualPalettes)
                .isSameAs(firstPalettes);
        }

        // A group bloc is shaded by its lead member, which only a read folding under the pass's
        // grouping and asking the pass's sector can answer.
        @Test
        void coloursBlocsUnderThePassesSectorAndGrouping() {

            // ARRANGE
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction(
                "hegemony",
                PALETTE_BRIGHT,
                PALETTE_DARK);

            var pass = HolderPass.createOver(
                sectorMock,
                UNDER_THE_FOG,
                HolderGroupingFixture.buildGroupOf("hegemony"));

            var expectedPalette = new ColourPair(PALETTE_BRIGHT, PALETTE_DARK);

            // ACT
            var actualPalette = pass.palettes().readBlocPalette(GROUP_BLOC_ID);

            // ASSERT
            assertThat(actualPalette)
                .isEqualTo(expectedPalette);
        }
    }

    // A colony's owner, which is what every projection case here asserts on. Named once rather
    // than spelled at each assertion so a case reads as the set of owners it expects rather than
    // as the walk down to an ID.
    private static String readColonyFactionId(Colony colony) {
        return colony.market().getFaction().getId();
    }
}
