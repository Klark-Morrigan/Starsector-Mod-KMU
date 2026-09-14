package kmu.maplayers.base.visibility.observations;

import com.fs.starfarer.api.campaign.CampaignClockAPI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the game's clock reports for one recorded moment: how long ago it was, and the date it
 * falls on.
 *
 * <p>Stubbed rather than computed, so a case states the age it is about instead of arithmetic over
 * two timestamps. Stated once beside the triad because every suite about a remark poses the same
 * two reads - the span a reader judges the news by, and the date they hold it against - and each
 * suite stubbing its own clock would be that many chances to pose them differently.
 *
 * <p>One moment per call, because how many moments a suite weighs is the suite's own subject: a
 * rule about which of several dates wins states each contender, and a suite about a single
 * observation makes the one call.
 */
public final class ObservationClockFixture {

    private ObservationClockFixture() {
    }

    /**
     * Stubs the clock's two reads of the given moment.
     *
     * <p>The date is reported by a second clock built from the stamp, this being the only way the
     * game turns a moment into a date.
     *
     * @param clockMock         the campaign clock the surface reads
     * @param observedTimestamp the moment, as a register stamps it
     * @param elapsedDays       the span the clock is to report since that moment
     * @param date              the date the clock is to report it falling on
     */
    public static void stubMomentOnClock(
            CampaignClockAPI clockMock,
            long observedTimestamp,
            float elapsedDays,
            String date) {

        var observedClockMock = mock(CampaignClockAPI.class);

        when(observedClockMock.getDateString())
            .thenReturn(date);

        when(clockMock.createClock(observedTimestamp))
            .thenReturn(observedClockMock);
        when(clockMock.getElapsedDaysSince(observedTimestamp))
            .thenReturn(elapsedDays);
    }
}
