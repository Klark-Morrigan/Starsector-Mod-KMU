package kmu.maplayers.ownermap.owners.holders.base.picker;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;

import kmlib.starsector.factions.StarsectorPlayerFactionResolver;
import kmlib.starsector.factions.relation.FactionRelation;
import kmlib.testfixtures.statics.StaticSeams;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static kmu.maplayers.ownermap.owners.holders.base.picker.BlocStandingFixtures.FRIENDLY;
import static kmu.maplayers.ownermap.owners.holders.base.picker.BlocStandingFixtures.HOSTILE;
import static kmu.maplayers.ownermap.owners.holders.base.picker.BlocStandingFixtures.PACT_GROUPING;
import static kmu.starsector.SectorFactionFixtures.buildEmptySector;
import static kmu.starsector.SectorFactionFixtures.stubKnownFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the fold from a bloc's members to the one position it holds on the player's scale: which of
 * the three cases a bloc lands in, and - where it is measured - which two members' standings the
 * ends came from.
 *
 * <p>All on hand-built standings, the sector reads being the reader's one seam. The cases over a live
 * sector are there for what only the binding decides: that the factions are looked up in the sector
 * handed in, and that the player is recognised by the ID the library reads off that sector.
 */
final class BlocStandingReaderTests {

    // The same group as the shared pact with the player's own faction inside it, so a bloc the map
    // paints as partly the player's is asked about as one.
    private static final HolderGrouping PLAYER_PACT_GROUPING = new HolderGrouping(
        Map.of("player", "pact", "hegemony", "pact"),
        Map.of("pact", "hegemony"),
        Map.of("pact", "Persean Pact"));

    private final StaticSeams seams = new StaticSeams();

    @AfterEach
    void closeSeams() {
        seams.closeEverySeam();
    }

    @Nested
    class Constructor {

        @Test
        void refusesAMissingGrouping() {
            // The grouping is what names a bloc's members, so a reader with none would fault on the
            // first bloc it was asked about rather than where the picker failed to hand one over.
            assertThatThrownBy(() -> new BlocStandingReader(null, new PlayerRelationSourceFake(null, Map.of())))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("grouping");
        }

        @Test
        void refusesAMissingRelationSource() {

            assertThatThrownBy(() -> new BlocStandingReader(HolderGrouping.identity(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("relationSource");
        }
    }

    @Nested
    class ReadBlocStanding {

        @Test
        void foldsALoneFactionToARangeWhoseEndsCoincide() {

            var relationSourceFake = new PlayerRelationSourceFake(
                null,
                Map.of("hegemony", HOSTILE));

            var reader = new BlocStandingReader(HolderGrouping.identity(), relationSourceFake);

            // Both ends are the one member's standing, which is what lets a lone faction's value
            // collapse to a single number without a case of its own.
            assertThat(reader.readBlocStanding("hegemony"))
                .isEqualTo(new BlocStanding.Measured(HOSTILE, HOSTILE));
        }

        @Test
        void foldsAGroupsMembersToTheTwoEndsTheyHold() {

            var relationSourceFake = new PlayerRelationSourceFake(
                null,
                Map.of("hegemony", HOSTILE, "tritachyon", FRIENDLY));

            var reader = new BlocStandingReader(PACT_GROUPING, relationSourceFake);

            assertThat(reader.readBlocStanding("pact"))
                .isEqualTo(new BlocStanding.Measured(HOSTILE, FRIENDLY));
        }

        @Test
        void measuresTheMembersThatAnsweredWhenOneCannotBeLookedUp() {

            // Tritachyon is absent from the sector, so the range is the one member that answered
            // rather than one end dragged to the scale's centre by a faction nothing is known about.
            var relationSourceFake = new PlayerRelationSourceFake(
                null,
                Map.of("hegemony", FRIENDLY));

            var reader = new BlocStandingReader(PACT_GROUPING, relationSourceFake);

            assertThat(reader.readBlocStanding("pact"))
                .isEqualTo(new BlocStanding.Measured(FRIENDLY, FRIENDLY));
        }

        @Test
        void reportsABlocNoMemberAnsweredForAsUnreadable() {

            var relationSourceFake = new PlayerRelationSourceFake(null, Map.of());
            var reader = new BlocStandingReader(PACT_GROUPING, relationSourceFake);

            assertThat(reader.readBlocStanding("pact"))
                .isEqualTo(BlocStanding.UNREADABLE);
        }

        @Test
        void reportsABlocWithNoIdAsUnreadable() {

            // The player faction is established here, so the case also pins that a bloc nothing
            // named is not matched against the player's own bloc by two absent IDs agreeing.
            var relationSourceFake = new PlayerRelationSourceFake("player", Map.of());
            var reader = new BlocStandingReader(HolderGrouping.identity(), relationSourceFake);

            assertThat(reader.readBlocStanding(null))
                .isEqualTo(BlocStanding.UNREADABLE);
        }

        @Test
        void reportsABlocWithABlankIdAsUnreadable() {

            var relationSourceFake = new PlayerRelationSourceFake("player", Map.of("  ", FRIENDLY));
            var reader = new BlocStandingReader(HolderGrouping.identity(), relationSourceFake);

            assertThat(reader.readBlocStanding("  "))
                .isEqualTo(BlocStanding.UNREADABLE);
        }

        @Test
        void recognisesThePlayersOwnFactionAsTheirBloc() {

            var relationSourceFake = new PlayerRelationSourceFake(
                "player",
                Map.of("player", FRIENDLY));

            var reader = new BlocStandingReader(HolderGrouping.identity(), relationSourceFake);

            // The engine answers a standing for the player with themselves; the bloc the scale is
            // measured from takes its own case rather than that number.
            assertThat(reader.readBlocStanding("player"))
                .isEqualTo(BlocStanding.PLAYERS_OWN);
        }

        @Test
        void recognisesTheGroupThePlayersFactionFoldsInto() {

            var relationSourceFake = new PlayerRelationSourceFake(
                "player",
                Map.of("hegemony", HOSTILE));

            var reader = new BlocStandingReader(PLAYER_PACT_GROUPING, relationSourceFake);

            // The map paints this bloc as the player's, so ranking it by how its other members feel
            // about them would rank a bloc against itself.
            assertThat(reader.readBlocStanding("pact"))
                .isEqualTo(BlocStanding.PLAYERS_OWN);
        }

        @Test
        void measuresThePlayersFactionBeforeAnIdentityIsEstablished() {

            var relationSourceFake = new PlayerRelationSourceFake(
                null,
                Map.of("player", FRIENDLY));

            var reader = new BlocStandingReader(HolderGrouping.identity(), relationSourceFake);

            // With no established faction there is nobody for a bloc to be recognised as, so the
            // bloc is measured like any other.
            assertThat(reader.readBlocStanding("player"))
                .isEqualTo(new BlocStanding.Measured(FRIENDLY, FRIENDLY));
        }
    }

    @Nested
    class CreateForSector {

        @Test
        void refusesAMissingSector() {
            // The reads behind the reader are deferred to each call, so without this guard a missing
            // sector would surface only when the picker first ranked by standing.
            assertThatThrownBy(() -> BlocStandingReader.createForSector(null, HolderGrouping.identity()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sector");
        }

        @Test
        void refusesAMissingGrouping() {

            assertThatThrownBy(() -> BlocStandingReader.createForSector(buildEmptySector(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("grouping");
        }

        @Test
        void readsAMembersStandingFromTheHandedInSector() {

            var sectorMock = buildEmptySector();

            stubRelationWithPlayer(sectorMock, "hegemony", HOSTILE);

            var reader = BlocStandingReader.createForSector(sectorMock, HolderGrouping.identity());

            // The standing came off the sector that was handed in, so a reader built over a second
            // sector reports that sector's relations rather than the running game's.
            assertThat(reader.readBlocStanding("hegemony"))
                .isEqualTo(new BlocStanding.Measured(HOSTILE, HOSTILE));
        }

        @Test
        void recognisesTheSectorsEstablishedPlayerFactionAsTheirBloc() {

            var sectorMock = buildEmptySector();

            stubRelationWithPlayer(sectorMock, "player", FRIENDLY);

            seams.openSeam(StarsectorPlayerFactionResolver.class)
                .when(() -> StarsectorPlayerFactionResolver.findEstablishedPlayerFactionId(sectorMock))
                .thenReturn("player");

            var reader = BlocStandingReader.createForSector(sectorMock, HolderGrouping.identity());

            assertThat(reader.readBlocStanding("player"))
                .isEqualTo(BlocStanding.PLAYERS_OWN);
        }

        @Test
        void measuresTheSectorsPlayerFactionBeforeAnIdentityIsEstablished() {

            var sectorMock = buildEmptySector();

            stubRelationWithPlayer(sectorMock, "player", FRIENDLY);

            seams.openSeam(StarsectorPlayerFactionResolver.class)
                .when(() -> StarsectorPlayerFactionResolver.findEstablishedPlayerFactionId(sectorMock))
                .thenReturn(null);

            var reader = BlocStandingReader.createForSector(sectorMock, HolderGrouping.identity());

            assertThat(reader.readBlocStanding("player"))
                .isEqualTo(new BlocStanding.Measured(FRIENDLY, FRIENDLY));
        }
    }

    // Stubs a faction onto the sector standing toward the player as the engine reports it.
    private static void stubRelationWithPlayer(
            SectorAPI sectorMock,
            String factionId,
            FactionRelation relation) {

        var relationshipMock = mock(RelationshipAPI.class);

        when(relationshipMock.getLevel())
            .thenReturn(relation.level());
        when(relationshipMock.getRepInt())
            .thenReturn(relation.reputation());
        when(relationshipMock.getRelColor())
            .thenReturn(relation.colour());

        var factionMock = stubKnownFaction(sectorMock, factionId);

        when(factionMock.getRelToPlayer())
            .thenReturn(relationshipMock);
    }
}
