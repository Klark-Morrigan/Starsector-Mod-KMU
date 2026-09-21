package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;

import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.base.visibility.colonies.ColonyObservation;
import kmu.maplayers.base.visibility.colonies.ColonySightings;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.visibility.observations.ObservationClockFixture.stubMomentOnClock;
import static kmu.maplayers.politicalmap.base.tooltip.ColonyObservationFixture.OBSERVED_AT;
import static kmu.maplayers.politicalmap.base.tooltip.ColonyObservationFixture.OBSERVED_DATE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins when a colony's line says how old the box's news of it is, and what it says.
 *
 * <p>The remark's whole claim is that it appears only where nobody is looking, so both live routes
 * are posed as cases of their own: the player standing in the system, and the system's own
 * inhabitants seeing the colony. A date beside a thing in plain view is stale by construction, and
 * is the one reading the remark must never produce.
 *
 * <p>The colonies are posed as a {@link Colonies} set rather than through a walk of a stubbed
 * sector, because what the notes read off the set - which gated colonies its inhabitants can see -
 * is the set's own answer, and a case that built one by walking would be pinning the walk.
 *
 * <p>How an age is worded - the span words, their thresholds, the truncation - is pinned once,
 * beside the code that composes it. What is pinned here is the colony's side: when a remark is due
 * at all, one composed sentence proving the route from register to remark, and that the remark
 * reaches the colony's own line.
 */
final class ColonyObservationNotesTest {

    private static final String DERELICT_ID = "sentinel_gantries";
    private static final String NEIGHBOUR_ID = "jangala";
    private static final String SYSTEM_ID = "kumari_kandam";

    private CampaignClockAPI clockMock;
    private SectorAPI sectorMock;
    private StarSystemAPI systemMock;

    @BeforeEach
    void openSector() {

        sectorMock = mock(SectorAPI.class);
        systemMock = mock(StarSystemAPI.class);

        clockMock = ColonyObservationFixture.installObservedClock(sectorMock);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveLastSeenNote {

        @Test
        void remarksNothingOnAColonyThePlayerIsStandingOver() {
            // In sight, the name stands alone. The register does hold a dated observation - the
            // player's arrival wrote one - so a rule reading the stamp alone would date a colony
            // the player is looking straight at.
            when(sectorMock.getCurrentLocation())
                .thenReturn(systemMock);

            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(34.0f));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .isEmpty();
        }

        @Test
        void remarksNothingOnAColonyItsNeighboursCanSee() {
            // The other live route. A derelict in orbit over an inhabited world is common knowledge
            // there, so the box's news of it is as current as the system it stands in.
            var notes = readNotesOver(buildSettledDerelictSet(), observedDaysAgo(34.0f));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .isEmpty();
        }

        @Test
        void remarksHowLongAgoAndOnWhatDateAColonyNobodyIsLookingAtWasSeen() {
            // The whole of what the time decides. Both halves are stated: the span is what a
            // reader judges the news by, and the date is what they hold it against.
            //
            // The opening words are the colony's own last-seen key, resolved through the settings
            // mirror of it - so an axis handed over under a lead-in minted beside the shared rule
            // fails here rather than in game.
            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(34.0f));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .contains("last seen 34 days ago (c206.05.12)");
        }

        @Test
        void remarksNothingOnAColonyWhoseObservationCarriesNoTime() {
            // Every value recorded before observations were timed. The colony goes on being shown
            // exactly as it was - only the remark is withheld, there being no date to state.
            var notes = readNotesOver(
                buildDerelictSet(),
                colonyId -> ColonyObservation.createUndatedObservation(SYSTEM_ID));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .isEmpty();
        }

        @Test
        void remarksNothingWhereThereIsNoClockToDateTheObservationBy() {
            // A box drawn before the campaign clock is up. The observation stands and the colony
            // is listed as it always was; only the remark is withheld, there being nothing to
            // work the span or the date out of.
            when(sectorMock.getClock())
                .thenReturn(null);

            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(34.0f));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .isEmpty();
        }

        @Test
        void remarksNothingOnAColonyNobodyHasEverObserved() {

            var notes = readNotesOver(buildDerelictSet(), ColonySightings.NONE);

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .isEmpty();
        }

        @Test
        void remarksNothingWhereThereIsNoSystemToReadAtAll() {
            // A box built before the sector stands up. Nothing can be dated, and nothing faults.
            assertThat(ColonyObservationNotes
                    .readNotesFor(null, null, null, ColonySightings.NONE)
                    .resolveLastSeenNote(DERELICT_ID))
                .isEmpty();
        }
    }

    @Nested
    class RemarkOnColony {

        @Test
        void laysTheRemarkOnTheColonysOwnLine() {

            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(34.0f));

            assertThat(notes.remarkOnColony(buildColonyLine(), DERELICT_ID).noteText())
                .isEqualTo("last seen 34 days ago (c206.05.12)");
        }

        @Test
        void handsBackTheSameLineWhereNoRemarkIsDue() {
            // A colony in plain sight. The line must come back as its account built it: a remark
            // that is not due must leave the line alone rather than lay an empty one on it, which
            // would part the name from what qualifies it over a note with nothing in it.
            when(sectorMock.getCurrentLocation())
                .thenReturn(systemMock);

            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(34.0f));
            var line = buildColonyLine();

            assertThat(notes.remarkOnColony(line, DERELICT_ID))
                .isSameAs(line);
        }
    }

    // One colony's line as its account hands it over, carrying no remark of its own.
    private static CellTooltipEntryLine buildColonyLine() {
        return CellTooltipEntryLine.createLine(CellTooltipMark.NO_MARK, "Sentinel Gantries", "0");
    }

    // The notes a box over the one system reads, the player's fleet being elsewhere unless a case
    // has said otherwise. The register travels beside the set, as the box's own pass hands the
    // pair over.
    private ColonyObservationNotes readNotesOver(
            Colonies colonies,
            ColonySightings sightings) {

        return ColonyObservationNotes.readNotesFor(sectorMock, systemMock, colonies, sightings);
    }

    // A system holding one derelict and nobody else: nothing living stands here to see it, so the
    // remark turns on the register alone.
    private static Colonies buildDerelictSet() {
        return new Colonies(List.of(new Colony(buildDerelictMarket(DERELICT_ID), false)));
    }

    // The same derelict standing beside another faction's open colony, whose people can see it.
    private static Colonies buildSettledDerelictSet() {

        return new Colonies(List.of(
            new Colony(buildDerelictMarket(DERELICT_ID), false),
            new Colony(buildMarket(NEIGHBOUR_ID, "hegemony"), true)));
    }

    // A wreck nobody was ever aboard: neutral's, and carrying vanilla's abandoned-station condition,
    // which is what the kind read parts a derelict from a station somebody keeps on. Posed as the
    // real shape rather than declared, the kind being resolved off the market now.
    private static MarketAPI buildDerelictMarket(String marketId) {

        var marketMock = buildMarket(marketId, Factions.NEUTRAL);

        when(marketMock.getFaction().isNeutralFaction())
            .thenReturn(true);
        when(marketMock.hasCondition(Conditions.ABANDONED_STATION))
            .thenReturn(true);

        return marketMock;
    }

    // A register holding one dated observation of the derelict where it stands, the clock
    // reporting the given span since. What the clock says of the moment is stubbed in the shared
    // fixture's terms, so a case states the age it is about rather than arithmetic over stamps.
    private ColonySightings observedDaysAgo(float elapsedDays) {

        stubMomentOnClock(clockMock, OBSERVED_AT, elapsedDays, OBSERVED_DATE);

        return colonyId -> DERELICT_ID.equals(colonyId)
            ? ColonyObservation.createObservationAt(SYSTEM_ID, OBSERVED_AT)
            : null;
    }

    // A market under an owner, which is what the settling read compares and the fog admits on.
    private static MarketAPI buildMarket(String marketId, String factionId) {

        var factionMock = mock(FactionAPI.class);
        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(factionMock);
        when(marketMock.getFactionId())
            .thenReturn(factionId);
        when(marketMock.getId())
            .thenReturn(marketId);

        return marketMock;
    }
}
