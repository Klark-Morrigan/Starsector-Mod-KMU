package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.Colony;

import kmu.maplayers.base.visibility.colonies.ColonyObservation;
import kmu.maplayers.base.visibility.colonies.ColonySightings;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

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
 */
final class ColonyObservationNotesTest {

    private static final String DERELICT_ID = "sentinel_gantries";
    private static final String NEIGHBOUR_ID = "jangala";
    private static final String SYSTEM_ID = "kumari_kandam";

    // When the observation the cases read was made, and the date the clock reports for it. The
    // elapsed span is stubbed apart from the stamp, that being what the game's own clock does with
    // it - so a case states the span it is about rather than arithmetic over two timestamps.
    private static final long OBSERVED_AT = 4_200L;
    private static final String OBSERVED_DATE = "c206.05.12";

    private CampaignClockAPI clockMock;
    private SectorAPI sectorMock;
    private StarSystemAPI systemMock;

    @BeforeEach
    void openSector() {

        clockMock = mock(CampaignClockAPI.class);
        sectorMock = mock(SectorAPI.class);
        systemMock = mock(StarSystemAPI.class);

        var observedClockMock = mock(CampaignClockAPI.class);

        when(observedClockMock.getDateString())
            .thenReturn(OBSERVED_DATE);
        when(clockMock.createClock(OBSERVED_AT))
            .thenReturn(observedClockMock);
        when(sectorMock.getClock())
            .thenReturn(clockMock);
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
            // The other live route. A hulk in orbit over an inhabited world is common knowledge
            // there, so the box's news of it is as current as the system it stands in.
            var notes = readNotesOver(buildSettledDerelictSet(), observedDaysAgo(34.0f));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .isEmpty();
        }

        @Test
        void remarksHowLongAgoAndOnWhatDateAColonyNobodyIsLookingAtWasSeen() {
            // The whole of what the time decides. Both halves are stated: the span is what a
            // reader judges the news by, and the date is what they hold it against.
            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(34.0f));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .contains("last seen 34 days ago (c206.05.12)");
        }

        @Test
        void remarksTodayOnAColonySeenWithinTheDay() {
            // A span short of a day named rather than rounded to nought: "0 days ago" reads as a
            // fault in the box, and the reader is being told the news is fresh.
            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(0.4f));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .contains("last seen today (c206.05.12)");
        }

        @Test
        void remarksADayAgoOnAColonySeenTheDayBefore() {

            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(1.5f));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .contains("last seen a day ago (c206.05.12)");
        }

        @ParameterizedTest
        @MethodSource("kmu.maplayers.politicalmap.base.tooltip.ColonyObservationNotesTest"
            + "#listSpansStandingOnAWordingThreshold")
        void remarksTheOlderWordingOnASpanStandingExactlyOnAThreshold(
                float elapsedDays,
                String expectedNote) {

            // Both thresholds are compared with a strict less-than, and every other case here sits
            // well clear of them - so the boundary itself is the one place the comparison could be
            // loosened without a single assertion noticing. A day that has fully elapsed is a day
            // ago, not today.
            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(elapsedDays));

            assertThat(notes.resolveLastSeenNote(DERELICT_ID))
                .contains(expectedNote);
        }

        @Test
        void remarksWholeDaysOnASpanCarryingPartOfAnother() {
            // The clock reports a fraction, and every other case hands over a span that happens to
            // be whole. Part of a day is not another day: rounding here would report a colony seen
            // this morning as seen tomorrow.
            var notes = readNotesOver(buildDerelictSet(), observedDaysAgo(34.9f));

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

    // The two spans the wording turns on, each stated exactly rather than either side of it: a
    // span of one whole day is the first that is no longer today, and one of two whole days the
    // first that is no longer the day before. Paired with the remark each is due, so a threshold
    // loosened by one comparison names the case it broke.
    static Stream<Arguments> listSpansStandingOnAWordingThreshold() {
        return Stream.of(
            Arguments.of(1.0f, "last seen a day ago (c206.05.12)"),
            Arguments.of(2.0f, "last seen 2 days ago (c206.05.12)"));
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

    // A hulk nobody was ever aboard: neutral's, and carrying vanilla's abandoned-station condition,
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
    // reporting the given span since. The two are stubbed apart because the game's clock is what
    // turns a stamp into a span, and a case is about the span.
    private ColonySightings observedDaysAgo(float elapsedDays) {

        when(clockMock.getElapsedDaysSince(OBSERVED_AT))
            .thenReturn(elapsedDays);

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
