package kmu.maplayers.politicalmap.tooltip;

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
import static kmu.maplayers.politicalmap.tooltip.ColonyObservationFixture.OBSERVED_AT;
import static kmu.maplayers.politicalmap.tooltip.ColonyObservationFixture.OBSERVED_DATE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins when a colony's line says how old the box's news of it is, and what it says.
 *
 * <p>The remark's whole point is that it appears only where nobody is looking, so both live routes
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
final class ColonyObservationNotesTests {

    private static final String DERELICT_ID = "sentinel_gantries";
    private static final String NEIGHBOUR_ID = "jangala";
    private static final String SYSTEM_ID = "kumari_kandam";

    // The age every dated case is posed at, and the sentence the remark composes for it.
    private static final float DAYS_SINCE_OBSERVED = 34.0f;
    private static final String LAST_SEEN_34_DAYS_AGO = "last seen 34 days ago (c206.05.12)";

    // Whether the economy lists a colony, which the derelict never is and its neighbour is.
    private static final boolean IS_LISTED_BY_ECONOMY = true;
    private static final boolean IS_OFF_ECONOMY = false;

    // What a box hands over where it read no register at all.
    private static final ColonySightings NO_SIGHTINGS_READ = null;

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
    class ReadNotesFor {

        @Test
        void answersNoneWhereThereIsNoSystemToReadAtAll() {
            // A box built before the sector stands up. Nothing can be dated, and nothing faults.
            assertThat(ColonyObservationNotes.readNotesFor(null, null, null, ColonySightings.NONE))
                .isSameAs(ColonyObservationNotes.NONE);
        }

        @Test
        void readsAnUnreadRegisterAsNothingObserved() {
            // A box that read no register states no date, rather than faulting on the first line
            // that asks for one.
            var notes = ColonyObservationNotes.readNotesFor(
                sectorMock,
                systemMock,
                buildDerelictSet(),
                NO_SIGHTINGS_READ);

            var line = buildColonyLine();

            assertThat(notes.remarkOnColony(line, DERELICT_ID))
                .isSameAs(line);
        }
    }

    @Nested
    class RemarkOnColony {

        @Test
        void laysHowLongAgoAndOnWhatDateAColonyNobodyIsLookingAtWasSeen() {
            // The whole of what the time decides. Both halves are stated: the span is what a
            // reader judges the news by, and the date is what they hold it against.
            //
            // The opening words are the colony's own last-seen key, resolved through the settings
            // mirror of it - so an axis handed over under a lead-in minted beside the shared rule
            // fails here rather than in game.
            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(DAYS_SINCE_OBSERVED));

            assertThat(notes.remarkOnColony(buildColonyLine(), DERELICT_ID).noteText())
                .isEqualTo(LAST_SEEN_34_DAYS_AGO);
        }

        @Test
        void handsBackTheSameLineForAColonyThePlayerIsStandingOver() {
            // In sight, the name stands alone. The register does hold a dated observation - the
            // player's arrival wrote one - so a rule reading the stamp alone would date a colony
            // the player is looking straight at.
            //
            // The line must come back as its account built it: a remark that is not due must leave
            // the line alone rather than lay an empty one on it, which would part the name from
            // what qualifies it over a note with nothing in it.
            when(sectorMock.getCurrentLocation())
                .thenReturn(systemMock);

            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(DAYS_SINCE_OBSERVED));
            var line = buildColonyLine();

            assertThat(notes.remarkOnColony(line, DERELICT_ID))
                .isSameAs(line);
        }

        @Test
        void handsBackTheSameLineForAColonyItsNeighboursCanSee() {
            // The other live route. A derelict in orbit over an inhabited world is common knowledge
            // there, so the box's news of it is as current as the system it stands in.
            var notes = readNotesOver(buildSettledDerelictSet(), observedDaysAgo(DAYS_SINCE_OBSERVED));
            var line = buildColonyLine();

            assertThat(notes.remarkOnColony(line, DERELICT_ID))
                .isSameAs(line);
        }

        @Test
        void handsBackTheSameLineForAColonyWhoseObservationCarriesNoTime() {
            // An observation stored without a moment, as the register's undated shape holds it. The
            // colony is still shown; only the remark is withheld, there being no date to state.
            var notes = readNotesOver(
                buildDerelictSet(),
                colonyId -> ColonyObservation.createUndatedObservation(SYSTEM_ID));

            var line = buildColonyLine();

            assertThat(notes.remarkOnColony(line, DERELICT_ID))
                .isSameAs(line);
        }

        @Test
        void handsBackTheSameLineWhereThereIsNoClockToDateTheObservationBy() {
            // A box drawn before the campaign clock is up. The observation stands and the colony is
            // still shown; only the remark is withheld, there being nothing to work the span or the
            // date out of.
            when(sectorMock.getClock())
                .thenReturn(null);

            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(DAYS_SINCE_OBSERVED));
            var line = buildColonyLine();

            assertThat(notes.remarkOnColony(line, DERELICT_ID))
                .isSameAs(line);
        }

        @Test
        void handsBackTheSameLineForAColonyNobodyHasEverObserved() {

            var notes = readNotesOver(buildDerelictSet(), ColonySightings.NONE);
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
        return new Colonies(List.of(new Colony(buildDerelictMarket(DERELICT_ID), IS_OFF_ECONOMY)));
    }

    // The same derelict standing beside another faction's open colony, whose people can see it.
    private static Colonies buildSettledDerelictSet() {

        return new Colonies(List.of(
            new Colony(buildDerelictMarket(DERELICT_ID), IS_OFF_ECONOMY),
            new Colony(buildMarket(NEIGHBOUR_ID, "hegemony"), IS_LISTED_BY_ECONOMY)));
    }

    // A wreck nobody was ever aboard: neutral's, and carrying vanilla's abandoned-station condition,
    // which is what the kind read parts a derelict from a station somebody keeps on. Posed as the
    // real shape rather than declared, the kind being resolved off the market.
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
