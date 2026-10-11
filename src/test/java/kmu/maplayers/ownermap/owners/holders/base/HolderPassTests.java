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

            assertThatThrownBy(() ->
                    new HolderPass(new SectorPassIndex(null), UNDER_THE_FOG, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("grouping");
        }

        @Test
        void rejectsNullColonyReadRules() {
            // Required on the same terms as the other two, and for the same reason: the rules are
            // resolved once where the rebuild begins, so a null here is that one resolve having
            // gone wrong. Standing the fog in would answer it with a map that draws less than it
            // should and says nothing about why.
            assertThatThrownBy(() ->
                    new HolderPass(new SectorPassIndex(null), null, HolderGrouping.identity()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("colonyReadRules");
        }

        @Test
        void rejectsNullSectorIndex() {
            // A pass with no walk behind it would fault on the first system it read rather than
            // here, and a pass over a sector that cannot be reached is a different thing entirely -
            // an index over a null sector, which answers an empty set and is perfectly legal.
            assertThatThrownBy(() ->
                    new HolderPass(null, UNDER_THE_FOG, HolderGrouping.identity()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sectorIndex");
        }
    }

    @Nested
    class ReadFromLunaSettings {

        @Test
        void opensUnderTheLiveColonyRulesAndTheHandedGrouping() {
            // The one entry with no rules on the call, so the settings read is all the pass has to
            // reach. Both halves are posed away from the shipped reading, so a pass taking either
            // from anywhere else reads this sector differently: the reveal shows on the pass's
            // knowledge, and the unpopulated position empties the system of its decivilised world.
            seams.openSeam(ColonyReadRules.class)
                .when(ColonyReadRules::readFromLunaSettings)
                .thenReturn(new ColonyReadRules(UNDER_THE_REVEAL, COUNTS_AS_UNPOPULATED));

            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var pass = HolderPass.readFromLunaSettings(sector, ALLIED_HEGEMONY_AND_TRITACHYON);

            assertThat(pass.colonyKnowledge().rule())
                .isEqualTo(UNDER_THE_REVEAL);
            assertThat(pass.readInhabitingColoniesIn(system))
                .isEmpty();
            assertThat(pass.grouping())
                .isSameAs(ALLIED_HEGEMONY_AND_TRITACHYON);
            assertThat(pass.sector())
                .isSameAs(sector);
        }
    }

    @Nested
    class ReadKnownColoniesIn {

        @Test
        void withholdsAColonyThePlayerHasNotFound() {
            // The projection every display reader below takes, stated where it is named: a pass with
            // the fog in force reports the colonies the player may be shown and no others, so a
            // band, a fill and a box over one cell cannot each withhold a different set.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var knownColonies = HolderPass
                .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity())
                .readKnownColoniesIn(SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(knownColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactly("hegemony");
        }

        @Test
        void reportsAnUndiscoveredColonyWhereTheRevealLiftsTheFog() {
            // The same pass with the dev reveal on, which is the one knob the projection reads.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var knownColonies = HolderPass
                .createOver(sector, UNDER_THE_DEV_REVEAL, HolderGrouping.identity())
                .readKnownColoniesIn(SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(knownColonies)
                .hasSize(1);
        }

        @Test
        void reportsNoColoniesForASystemThatIsNotThere() {
            // The null-system answer every read on the pass holds to, so a reader handed a system
            // the sector no longer lists is not obliged to guard before asking.
            assertThat(HolderPass
                    .createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity())
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
            var colony = SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE);
            var derelict = SectorOwnershipFixtures.buildAbandonedStationMarket(COLONY_SIZE);

            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID, colony);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            // The derelict reaches the walk through the entity side, as a vanilla one does: the
            // economy never lists one, so posing it in the economy would pose a market the sector
            // does not hold.
            SectorOwnershipFixtures.placeMarketsOnSystemEntities(system, colony, derelict);

            var pass = HolderPass
                .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());

            assertThat(pass.readKnownColoniesIn(system))
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyInAnyOrder("hegemony", "neutral");
            assertThat(pass.readInhabitingColoniesIn(system))
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactly("hegemony");
        }

        @Test
        void leavesOutADecivilisedWorldWhereSuchAWorldCountsAsUnpopulated() {
            // The second rule the pass carries, and this read is the only place it lands. A world
            // people are still on is an owned cell under one position and empty space under the other,
            // which is the whole of the choice - so both are read back here.
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            assertThat(HolderPass
                    .createOver(sector, DECIVILISED_POPULATED, HolderGrouping.identity())
                    .readInhabitingColoniesIn(system))
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactly("neutral");
            assertThat(HolderPass
                    .createOver(sector, DECIVILISED_UNPOPULATED, HolderGrouping.identity())
                    .readInhabitingColoniesIn(system))
                .isEmpty();
        }

        @Test
        void keepsALiveColonyStandingBesideADecivilisedWorldUnderEitherRule() {
            // The rule names one kind and reaches nothing else in the system, so a faction living
            // beside such a world goes on inhabiting its system whichever position the pass takes.
            // A rule written as "what this system amounts to" rather than over the one kind would
            // take the colony with it.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            assertThat(HolderPass
                    .createOver(sector, DECIVILISED_POPULATED, HolderGrouping.identity())
                    .readInhabitingColoniesIn(system))
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactlyInAnyOrder("hegemony", "neutral");
            assertThat(HolderPass
                    .createOver(sector, DECIVILISED_UNPOPULATED, HolderGrouping.identity())
                    .readInhabitingColoniesIn(system))
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactly("hegemony");
        }

        @Test
        void withholdsAColonyThePlayerHasNotFound() {
            // Habitation is the known projection minus the derelicts, so it is held to the pass's
            // rule exactly as the listing is. A read that reached past the fog would settle a cell
            // on a colony the box beside it may not name.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            var inhabitingColonies = HolderPass
                .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity())
                .readInhabitingColoniesIn(SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(inhabitingColonies)
                .extracting(HolderPassTests::readColonyFactionId)
                .containsExactly("hegemony");
        }

        @Test
        void reportsAnUndiscoveredColonyWhereTheRevealLiftsTheFog() {
            // The same knob the listing reads, so a map showing every faction cannot draw a cell
            // as settled on one read and empty on the other.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var inhabitingColonies = HolderPass
                .createOver(sector, UNDER_THE_DEV_REVEAL, HolderGrouping.identity())
                .readInhabitingColoniesIn(SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(inhabitingColonies)
                .hasSize(1);
        }

        @Test
        void reportsNoColoniesForASystemThatIsNotThere() {
            // The null-system answer every read on the pass holds to, stated here too so the newer
            // read cannot be the one that faults where its siblings answer.
            assertThat(HolderPass
                    .createOver(
                        mock(SectorAPI.class),
                        UNDER_THE_FOG,
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
            var registeredColony = SectorOwnershipFixtures.buildVisibleMarket(
                HEGEMONY_FACTION,
                COLONY_SIZE);
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID, registeredColony);

            SectorOwnershipFixtures.placeMarketsOnSystemEntities(
                SectorOwnershipFixtures.buildOnlySystem(sector),
                registeredColony,
                SectorOwnershipFixtures.buildVisibleMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            assertThat(HolderPass
                    .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity())
                    .readKnownColonyFactionIds(SectorOwnershipFixtures.buildOnlySystem(sector)))
                .containsExactlyInAnyOrder("hegemony", "tritachyon");
        }

        @Test
        void namesAnOwnerOnceHoweverManyColoniesItHolds() {
            // A listing names a faction once, so the owners come back as a set: a faction with two
            // colonies here is one faction present, not two.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE));

            assertThat(HolderPass
                    .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity())
                    .readKnownColonyFactionIds(SectorOwnershipFixtures.buildOnlySystem(sector)))
                .containsExactly("hegemony");
        }

        @Test
        void withholdsAnOwnerHoldingOnlyUndiscoveredColonies() {
            // The fog reaches presence as it reaches the colonies themselves: naming a faction over
            // a base the player has not found is the one thing the projection exists to prevent,
            // and the dev reveal states it like anything else.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildUndiscoveredHiddenMarket(
                    HEGEMONY_FACTION,
                    COLONY_SIZE));

            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            assertThat(HolderPass
                    .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity())
                    .readKnownColonyFactionIds(system))
                .isEmpty();
            assertThat(HolderPass
                    .createOver(sector, UNDER_THE_DEV_REVEAL, HolderGrouping.identity())
                    .readKnownColonyFactionIds(system))
                .containsExactly("hegemony");
        }

        @Test
        void namesNobodyForASystemThatIsNotThere() {

            assertThat(HolderPass
                    .createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity())
                    .readKnownColonyFactionIds(null))
                .isEmpty();
        }
    }

    @Nested
    class ReadHabitationIn {

        @Test
        void foldsAlliedOwnersIntoTheOneBlocTheyPaintAs() {
            // Presence is asked by surfaces that decide per bloc - the spotlight's fill, the band's
            // runs - so two allies in one system are one bloc living there, not two.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildVisibleMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    COLONY_SIZE));

            assertThat(HolderPass
                    .createOver(sector, UNDER_THE_FOG, ALLIED_HEGEMONY_AND_TRITACHYON)
                    .readHabitationIn(SectorOwnershipFixtures.buildOnlySystem(sector))
                    .blocIds())
                .containsExactly(GROUP_BLOC_ID);
        }

        @Test
        void sumsWhatEachBlocLivesOnThere() {
            // The picker's size and the band's count come off this fold as well, so a bloc's
            // presence and what it lives on are one set of colonies rather than two selections that
            // could differ by one. Allied members sum into the bloc they paint as, as their weights
            // do.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, 5),
                SectorOwnershipFixtures.buildVisibleMarket(
                    SectorOwnershipFixtures.buildFaction("tritachyon"),
                    3));

            assertThat(HolderPass
                    .createOver(sector, UNDER_THE_FOG, ALLIED_HEGEMONY_AND_TRITACHYON)
                    .readHabitationIn(SectorOwnershipFixtures.buildOnlySystem(sector))
                    .habitationByBlocId())
                .containsExactly(entry(GROUP_BLOC_ID, new BlocHabitation(2, 8)));
        }

        @Test
        void sumsSeveralColoniesOfOneOwnerThere() {
            // The fold beneath the grouping. An owner's colonies are summed before any bloc is
            // named, so a faction living on two places in one system reads as the whole of what it
            // lives on rather than as whichever of them the walk reached last.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, 5),
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, 3));

            assertThat(HolderPass
                    .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity())
                    .readHabitationIn(SectorOwnershipFixtures.buildOnlySystem(sector))
                    .habitationByBlocId())
                .containsExactly(entry("hegemony", new BlocHabitation(2, 8)));
        }

        @Test
        void countsAColonyWhoseMarketNamesNoFactionIdAsNobodys() {
            // A market that keeps its faction object but stores no faction ID. The fold reads the
            // ID, as every other owner read on the map does, so the colony lands under no bloc -
            // and it still counts as somebody living there.
            var ownerless = SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE);

            when(ownerless.getFactionId())
                .thenReturn(null);

            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                ownerless);

            var habitation = HolderPass
                .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity())
                .readHabitationIn(SectorOwnershipFixtures.buildOnlySystem(sector));

            assertThat(habitation.habitationByBlocId())
                .containsExactly(entry("hegemony", new BlocHabitation(1, COLONY_SIZE)));
            assertThat(habitation.colonies())
                .hasSize(2);
        }

        @Test
        void leavesOutAnOwnerNoBlocCanBeNamedFor() {
            // A colony a mod hung on a faction with no id. The same rule every per-bloc fold on the
            // map applies: a nameless key would travel on as a bloc, and a surface asked to paint
            // or grey one has nothing to name it by.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE),
                SectorOwnershipFixtures.buildVisibleMarket(
                    SectorOwnershipFixtures.buildFaction(null),
                    COLONY_SIZE));

            assertThat(HolderPass
                    .createOver(sector, UNDER_THE_FOG, HolderGrouping.identity())
                    .readHabitationIn(SectorOwnershipFixtures.buildOnlySystem(sector))
                    .blocIds())
                .containsExactly("hegemony");
        }

        @Test
        void namesNoBlocForASystemHoldingOnlyADerelict() {
            // The case the whole value exists for. The listing names the derelict's owner, and nobody
            // lives on it - so the blocs come back empty beside a habitation that has nothing in
            // it, which is what keeps the spotlight from sparing a cell the map draws as empty
            // space.
            var derelict = SectorOwnershipFixtures.buildAbandonedStationMarket(COLONY_SIZE);
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            // Through the entity side, as a vanilla derelict arrives: the economy never lists one.
            SectorOwnershipFixtures.placeMarketsOnSystemEntities(system, derelict);

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var habitation = pass.readHabitationIn(system);

            assertThat(pass.readKnownColonyFactionIds(system))
                .containsExactly("neutral");
            assertThat(habitation.blocIds())
                .isEmpty();
            assertThat(habitation.hasInhabitingColony())
                .isFalse();
        }

        @Test
        void namesNoBlocForASystemHoldingOnlyADecivilisedWorldCountedAsUnpopulated() {
            // The value the cell and the spotlight answer off, which is where the rule has to
            // land for either of them to move. The world goes on being listed and named in a box;
            // what changes is that no bloc lives there, so the cell is empty space and a
            // spotlight over it has no cell to light.
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            var pass = HolderPass.createOver(sector, DECIVILISED_UNPOPULATED, HolderGrouping.identity());
            var habitation = pass.readHabitationIn(system);

            assertThat(pass.readKnownColonyFactionIds(system))
                .containsExactly("neutral");
            assertThat(habitation.blocIds())
                .isEmpty();
            assertThat(habitation.hasInhabitingColony())
                .isFalse();
        }

        @Test
        void namesTheWorldsOwnerForThatSystemWhereSuchAWorldCountsAsPopulated() {
            // The other position, read off the same value: the fold is taken from the projection
            // above, so the cell the rule leaves empty in the case before this one is an owned cell
            // here without either surface being told which rule was in force.
            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(system);

            assertThat(HolderPass
                    .createOver(sector, DECIVILISED_POPULATED, HolderGrouping.identity())
                    .readHabitationIn(system)
                    .blocIds())
                .containsExactly("neutral");
        }

        @Test
        void worksOutOneSystemsHabitationOnceForTheWholePass() {
            // Two readers ask this per system and they run in separate walks of the sector - the
            // filter's holder resolve for the blocs, the inhabitation scan for the emptiness - so
            // without the memo every filtered rebuild projects and folds the whole sector twice.
            // Asserted as identity, since an equal value worked out again is exactly the repeat
            // this exists to stop.
            var sector = SectorOwnershipFixtures.buildSectorWith(
                SYSTEM_ID,
                SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE));

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var system = SectorOwnershipFixtures.buildOnlySystem(sector);

            assertThat(pass.readHabitationIn(system))
                .isSameAs(pass.readHabitationIn(system));
        }

        @Test
        void answersEachOfTwoSystemsSharingAnIdItsOwnInhabitants() {
            // The defect a memo keyed on the ID carries, on the one layer whose whole output is
            // who lives where: a sector holds two systems under one ID, so the pair is a single
            // entry and the second system is handed the first's blocs. The key separates them, an
            // anchor ID being minted per system - and it is the address the colony memo beneath
            // already reads, so the two cannot disagree about what one system is.
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

            assertThat(pass.readHabitationIn(first).blocIds())
                .containsExactly("hegemony");
            assertThat(pass.readHabitationIn(second).blocIds())
                .containsExactly("tritachyon");
        }

        @Test
        void keepsTwoUnkeyableSystemsApart() {
            // A system the sector names with nothing has no key to remember it by. It is resolved
            // afresh rather than pooled, which costs a fold a later ask would have saved - the
            // honest price, since a shared key would hand one system's blocs to another.
            var sector = SectorOwnershipFixtures.buildSectorWithSystems(
                List.of(),
                SectorOwnershipFixtures.listSystemMarkets(
                    null,
                    SectorOwnershipFixtures.buildVisibleMarket(HEGEMONY_FACTION, COLONY_SIZE)),
                SectorOwnershipFixtures.listSystemMarkets(null));

            var pass = HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity());
            var systems = sector.getStarSystems();

            assertThat(pass.readHabitationIn(systems.get(0)).blocIds())
                .containsExactly("hegemony");
            assertThat(pass.readHabitationIn(systems.get(1)).blocIds())
                .isEmpty();
        }

        @Test
        void reportsNobodyLivingInASystemThatIsNotThere() {
            // The null-system answer every read on the pass holds to, stated for the value too so a
            // reader handed a system the sector no longer lists is not obliged to guard first.
            var habitation = HolderPass
                .createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity())
                .readHabitationIn(null);

            assertThat(habitation.hasInhabitingColony())
                .isFalse();
            assertThat(habitation.blocIds())
                .isEmpty();
        }
    }

    @Nested
    class ReadSystems {

        @Test
        void walksNothingForAPassOverNoSector() {
            // Stated so a resolve can walk without guarding the unreachable sector first.
            assertThat(HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity()).readSystems())
                .isEmpty();
        }

        @Test
        void walksTheSectorsSystemsInItsOwnOrder() {

            var sector = SectorOwnershipFixtures.buildSectorWithSystems(
                List.of(),
                SectorOwnershipFixtures.listSystemMarkets("corvus"),
                SectorOwnershipFixtures.listSystemMarkets("askonia"));

            assertThat(HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity()).readSystems())
                .extracting(StarSystemAPI::getId)
                .containsExactly("corvus", "askonia");
        }
    }

    @Nested
    class CanReadEconomy {

        @Test
        void isFalseForAPassOverNoSector() {

            assertThat(HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity()).canReadEconomy())
                .isFalse();
        }

        @Test
        void isFalseForASectorWhoseEconomyIsNotUpYet() {
            // The mid-load sector: it has a system to walk, so only the economy check stops a
            // weighing resolve reporting an empty sector there.
            var sector = SectorOwnershipFixtures.buildEconomylessSectorWithSystem(SYSTEM_ID);

            assertThat(HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity()).canReadEconomy())
                .isFalse();
        }

        @Test
        void isTrueForASectorWithItsEconomyUp() {

            var sector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);

            assertThat(HolderPass.createOver(sector, UNDER_THE_FOG, HolderGrouping.identity()).canReadEconomy())
                .isTrue();
        }
    }

    @Nested
    class Sector {

        @Test
        void namesTheSectorTheWalkWasOpenedOver() {

            var sectorMock = mock(SectorAPI.class);

            assertThat(HolderPass.createOver(sectorMock, UNDER_THE_FOG, HolderGrouping.identity()).sector())
                .isSameAs(sectorMock);
        }

        @Test
        void namesNoSectorForAPassOverNone() {
            // The unreachable-sector case every resolve already guards on, reported rather than
            // stood in for.
            assertThat(HolderPass.createOver(null, UNDER_THE_FOG, HolderGrouping.identity()).sector())
                .isNull();
        }
    }

    @Nested
    class Palettes {

        @Test
        void answersOnePaletteReadForThePass() {
            // Every resolve and band over one rebuild reads a bloc's shades through the same object,
            // which is what lets each bloc be looked up once for all of them.
            var pass = HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());

            assertThat(pass.palettes())
                .isSameAs(pass.palettes());
        }

        @Test
        void coloursBlocsUnderThePassesSectorAndGrouping() {
            // A group bloc is shaded by its lead member, which only a read folding under the pass's
            // grouping and asking the pass's sector can answer.
            var sectorMock = SectorFactionFixtures.buildSectorShadingFaction(
                "hegemony",
                PALETTE_BRIGHT,
                PALETTE_DARK);
            var pass = HolderPass.createOver(
                sectorMock,
                UNDER_THE_FOG,
                HolderGroupingFixture.buildGroupOf("hegemony"));

            assertThat(pass.palettes().readBlocPalette(GROUP_BLOC_ID))
                .isEqualTo(new ColourPair(PALETTE_BRIGHT, PALETTE_DARK));
        }
    }

    // A colony's owner, which is what every projection case here asserts on. Named once rather
    // than spelled at each assertion so a case reads as the set of owners it expects rather than
    // as the walk down to an id.
    private static String readColonyFactionId(Colony colony) {
        return colony.market().getFaction().getId();
    }
}
