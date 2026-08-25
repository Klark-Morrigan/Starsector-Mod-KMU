package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared stubbing for the two reads a view makes of a bloc it names or lists: the sector resolving
 * the faction by id, and that faction's short display name. Both are setup rather than subject
 * wherever they appear, so one home for them lets a suite name a bloc in a line and keeps the pair
 * from drifting between the suites that need it.
 */
public final class SelectableBlocFixtures {

    private SelectableBlocFixtures() {
    }

    /**
     * Stubs a faction the sector resolves by id under a short display name.
     *
     * @param sectorMock  the sector the faction is resolved through
     * @param factionId   the faction's id, which for an ungrouped view is also its bloc id
     * @param displayName the short name the faction reads under
     * @return the stubbed faction, so a suite that also cares about the crest stubs it on the same
     *         mock rather than building a second one the sector does not return
     */
    public static FactionAPI stubNamedFaction(
            SectorAPI sectorMock,
            String factionId,
            String displayName) {

        var factionMock = mock(FactionAPI.class);

        when(sectorMock.getFaction(factionId))
            .thenReturn(factionMock);
        when(factionMock.getDisplayName())
            .thenReturn(displayName);

        return factionMock;
    }
}
