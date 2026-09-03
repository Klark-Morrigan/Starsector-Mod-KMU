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
 * <p>Only the colony's half of the route is stubbed here: the clock the sector hands out, stamping
 * a write at this moment. What that clock reports for a recorded moment - the span since, and the
 * date - is every axis's read alike, so a case states it through the shared
 * {@link kmu.maplayers.base.visibility.observations.ObservationClockFixture} beside the age it is
 * about.
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
     * Stubs the campaign clock onto a sector at that moment, so a visit writes the stamp a later
     * read recalls.
     *
     * @param sectorMock the sector the clock is read off
     * @return the clock, so a case may state what it reports for the moment
     */
    static CampaignClockAPI installObservedClock(SectorAPI sectorMock) {

        var clockMock = mock(CampaignClockAPI.class);

        when(clockMock.getTimestamp())
            .thenReturn(OBSERVED_AT);
        when(sectorMock.getClock())
            .thenReturn(clockMock);

        return clockMock;
    }
}
