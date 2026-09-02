package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one moment every suite about a colony's remark is posed around: when the observation was made,
 * and the date the game's clock reports for it.
 *
 * <p>Shared because the stamp travels the whole route - it is written into the register at the visit
 * and read back out of it wherever a remark is composed - so a suite exercising one end and a suite
 * exercising both must be talking about the same moment or they are pinning two different stories.
 *
 * <p>What a case expects a remark to <em>say</em> is deliberately not here. Each case states its own
 * span and its own composed sentence as literals, so the wording is pinned where it is read rather
 * than agreed with a constant that would be edited alongside it.
 */
final class ColonyObservationFixture {

    /** When the observation was made, as the register stamps it. */
    static final long OBSERVED_AT = 4_200L;

    /** The date the clock reports for that moment, as a remark names it. */
    static final String OBSERVED_DATE = "c206.05.12";

    private ColonyObservationFixture() {
    }

    /**
     * Stubs the campaign clock onto a sector at that moment: the stamp a write takes, and the date a
     * later read turns it back into.
     *
     * <p>The span since is left to the caller, that being what a case about a remark's wording
     * varies. It is stubbed apart from the stamp because the game's own clock is what turns one into
     * the other, so a case states the age it is about rather than arithmetic over two timestamps.
     *
     * <p>The date is reported by a second clock built from the stamp, this being the only way the
     * game turns a moment into a date.
     *
     * @param sectorMock the sector the clock is read off
     * @return the clock, so a case may state the span it is about
     */
    static CampaignClockAPI installObservedClock(SectorAPI sectorMock) {

        var observedClockMock = mock(CampaignClockAPI.class);

        when(observedClockMock.getDateString())
            .thenReturn(OBSERVED_DATE);

        var clockMock = mock(CampaignClockAPI.class);

        when(clockMock.getTimestamp())
            .thenReturn(OBSERVED_AT);
        when(clockMock.createClock(OBSERVED_AT))
            .thenReturn(observedClockMock);
        when(sectorMock.getClock())
            .thenReturn(clockMock);

        return clockMock;
    }
}
