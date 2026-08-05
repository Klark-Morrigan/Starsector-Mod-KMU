package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A sector standing in for the two reads every tooltip line naming a faction makes of one - its long
 * display title and its crest.
 *
 * <p>Held as one fixture because that pair is what {@code FactionPresentation} asks the game for, so
 * every suite in this package that names a faction has to stand the same two stubs up. Written out per
 * suite, they drift: one stubs a crest another leaves null, and each copy goes on agreeing with itself
 * while the suites disagree about what "a faction the sector knows" is.
 *
 * <p>A faction is stubbed onto a sector rather than handed back with one, so a suite needing two of them
 * states each in a line against the same sector - and a faction never stubbed at all is exactly the
 * unknown-id case, which every such suite has a bearing on.
 */
public final class SectorFactionsFake {

    private SectorFactionsFake() {
    }

    /**
     * Builds a sector that knows nothing, for the case where a line has only an id to go on.
     *
     * @return the sector, with no faction stubbed onto it
     */
    public static SectorAPI buildEmptySector() {
        return mock(SectorAPI.class);
    }

    /**
     * Stubs one faction onto {@code sectorMock}, so a suite states a faction's whole presentation in one
     * line rather than in a chain of stubs.
     *
     * @param sectorMock      the sector the faction is stubbed onto
     * @param factionId       the id the faction answers to
     * @param longName        the faction's long display title
     * @param crestSpritePath the faction's crest path; blank stands for a faction with none authored
     */
    public static void stubFaction(
            SectorAPI sectorMock,
            String factionId,
            String longName,
            String crestSpritePath) {

        // The faction is built before the sector is told about it: stubbing one mock inside another
        // stub's argument leaves Mockito mid-stubbing and fails the whole fixture.
        var factionMock = mock(FactionAPI.class);

        when(factionMock.getDisplayNameLong())
            .thenReturn(longName);
        when(factionMock.getCrest())
            .thenReturn(crestSpritePath);

        when(sectorMock.getFaction(factionId))
            .thenReturn(factionMock);
    }
}
