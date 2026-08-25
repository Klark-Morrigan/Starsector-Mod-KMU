package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared stubbing for the suites that pin a view's picker: every one of them has to satisfy the two
 * reads the shared option assembly makes of a listed bloc - the sector resolving the faction by id,
 * and that faction's display name - before it can assert anything about which blocs survive or how
 * their rows read. One home for it so a suite names a bloc in a line, and so the assembly gaining a
 * third read is one edit rather than one per layer.
 */
public final class SelectableBlocFixtures {

    private SelectableBlocFixtures() {
    }

    /**
     * Stubs a faction the sector resolves by id under a display name.
     *
     * @param sectorMock  the sector the assembly resolves the bloc's colour faction through
     * @param factionId   the bloc id, which for an ungrouped view is the faction's own id
     * @param displayName the short name the row draws
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
