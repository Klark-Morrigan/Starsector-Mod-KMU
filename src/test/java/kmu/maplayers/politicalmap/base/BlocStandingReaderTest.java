package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;

import kmlib.starsector.relation.PlayerStanding;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the fold from a bloc's members to the one position it holds on the player's scale: which of
 * the three cases a bloc lands in, and - where it is measured - which two members' standings the
 * ends came from.
 *
 * <p>All on hand-built standings, the sector reads being the reader's one seam. The single case
 * over a live sector is there for what only the binding decides: that the factions are looked up in
 * the sector handed in.
 */
final class BlocStandingReaderTest {

    // The shades a relation resolves to, distinct so an end carrying the wrong member's standing
    // shows as a colour rather than only as a number.
    private static final Color GREEN = new Color(60, 180, 60);
    private static final Color RED = new Color(200, 50, 50);

    private static final PlayerStanding FRIENDLY = new PlayerStanding(RepLevel.FRIENDLY, 60, GREEN);
    private static final PlayerStanding HOSTILE = new PlayerStanding(RepLevel.HOSTILE, -40, RED);

    // An alliance of two, which is the smallest grouping in which a bloc's membership is anything
    // other than the bloc's own id.
    private static final HolderGrouping PACT_GROUPING = new HolderGrouping(
        Map.of("hegemony", "pact", "tritachyon", "pact"),
        Map.of("pact", "hegemony"),
        Map.of("pact", "Persean Pact"));

    // The same alliance with the player's own faction inside it, so a bloc the map paints as partly
    // the player's is asked about as one.
    private static final HolderGrouping PLAYER_PACT_GROUPING = new HolderGrouping(
        Map.of("player", "pact", "hegemony", "pact"),
        Map.of("pact", "hegemony"),
        Map.of("pact", "Persean Pact"));

    @Nested
    class ReadBlocStanding {

        @Test
        void readBlocStandingFoldsALoneFactionToARangeWhoseEndsCoincide() {

            var standingSourceFake = new PlayerStandingSourceFake(
                null,
                Map.of("hegemony", HOSTILE));

            var reader = new BlocStandingReader(HolderGrouping.identity(), standingSourceFake);

            // Both ends are the one member's standing, which is what lets a lone faction's value
            // collapse to a single number without a case of its own.
            assertThat(reader.readBlocStanding("hegemony"))
                .isEqualTo(new BlocStanding.Measured(HOSTILE, HOSTILE));
        }

        @Test
        void readBlocStandingFoldsAnAlliancesMembersToTheTwoEndsTheyHold() {

            var standingSourceFake = new PlayerStandingSourceFake(
                null,
                Map.of("hegemony", HOSTILE, "tritachyon", FRIENDLY));

            var reader = new BlocStandingReader(PACT_GROUPING, standingSourceFake);

            assertThat(reader.readBlocStanding("pact"))
                .isEqualTo(new BlocStanding.Measured(HOSTILE, FRIENDLY));
        }

        @Test
        void readBlocStandingMeasuresTheMembersThatAnsweredWhenOneCannotBeLookedUp() {

            // Tritachyon is absent from the sector, so the range is the one member that answered
            // rather than one end dragged to the scale's centre by a faction nothing is known about.
            var standingSourceFake = new PlayerStandingSourceFake(
                null,
                Map.of("hegemony", FRIENDLY));

            var reader = new BlocStandingReader(PACT_GROUPING, standingSourceFake);

            assertThat(reader.readBlocStanding("pact"))
                .isEqualTo(new BlocStanding.Measured(FRIENDLY, FRIENDLY));
        }

        @Test
        void readBlocStandingReportsABlocNoMemberAnsweredForAsUnreadable() {

            var standingSourceFake = new PlayerStandingSourceFake(null, Map.of());
            var reader = new BlocStandingReader(PACT_GROUPING, standingSourceFake);

            assertThat(reader.readBlocStanding("pact"))
                .isEqualTo(BlocStanding.UNREADABLE);
        }

        @Test
        void readBlocStandingReportsABlocWithNoIdAsUnreadable() {

            // The player faction is established here, so the case also pins that a bloc nothing
            // named is not matched against the player's own bloc by two absent ids agreeing.
            var standingSourceFake = new PlayerStandingSourceFake("player", Map.of());
            var reader = new BlocStandingReader(HolderGrouping.identity(), standingSourceFake);

            assertThat(reader.readBlocStanding(null))
                .isEqualTo(BlocStanding.UNREADABLE);
        }

        @Test
        void readBlocStandingRecognisesThePlayersOwnFactionAsTheirBloc() {

            var standingSourceFake = new PlayerStandingSourceFake(
                "player",
                Map.of("player", FRIENDLY));

            var reader = new BlocStandingReader(HolderGrouping.identity(), standingSourceFake);

            // The engine answers a standing for the player with themselves; the bloc the scale is
            // measured from takes its own case rather than that number.
            assertThat(reader.readBlocStanding("player"))
                .isEqualTo(BlocStanding.PLAYERS_OWN);
        }

        @Test
        void readBlocStandingRecognisesTheAllianceThePlayersFactionFoldsInto() {

            var standingSourceFake = new PlayerStandingSourceFake(
                "player",
                Map.of("hegemony", HOSTILE));

            var reader = new BlocStandingReader(PLAYER_PACT_GROUPING, standingSourceFake);

            // The map paints this bloc as the player's, so ranking it by how its other members feel
            // about them would rank a bloc against itself.
            assertThat(reader.readBlocStanding("pact"))
                .isEqualTo(BlocStanding.PLAYERS_OWN);
        }

        @Test
        void readBlocStandingMeasuresThePlayersFactionBeforeAnIdentityIsEstablished() {

            var standingSourceFake = new PlayerStandingSourceFake(
                null,
                Map.of("player", FRIENDLY));

            var reader = new BlocStandingReader(HolderGrouping.identity(), standingSourceFake);

            // With no established faction there is nobody for a bloc to be recognised as, so the
            // bloc is measured like any other.
            assertThat(reader.readBlocStanding("player"))
                .isEqualTo(new BlocStanding.Measured(FRIENDLY, FRIENDLY));
        }
    }

    @Nested
    class CreateForSector {

        @Test
        void createForSectorReadsAMembersStandingFromTheHandedInSector() {

            var relationshipMock = mock(RelationshipAPI.class);

            when(relationshipMock.getLevel())
                .thenReturn(RepLevel.HOSTILE);
            when(relationshipMock.getRepInt())
                .thenReturn(-40);
            when(relationshipMock.getRelColor())
                .thenReturn(RED);

            var factionMock = mock(FactionAPI.class);

            when(factionMock.getRelToPlayer())
                .thenReturn(relationshipMock);

            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction("hegemony"))
                .thenReturn(factionMock);

            var reader = BlocStandingReader.createForSector(sectorMock, HolderGrouping.identity());

            // The standing came off the sector that was handed in, so a reader built over a second
            // sector reports that sector's relations rather than the running game's.
            assertThat(reader.readBlocStanding("hegemony"))
                .isEqualTo(new BlocStanding.Measured(HOSTILE, HOSTILE));
        }
    }

}
