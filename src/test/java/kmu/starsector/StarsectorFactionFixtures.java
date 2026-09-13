package kmu.starsector;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.awt.Color;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Factions carrying the two authored UI shades everything this mod paints a faction in is read
 * from - the bright colour and the dark one, and nothing else about a faction.
 *
 * <p>One home for them because the pair is read the same way wherever it is read, and a suite
 * stubbing its own is restating that mapping rather than asserting on it. The two shades arrive
 * stated rather than one derived from the other, so a case asserting on the dark one names a
 * literal instead of computing the expectation it is about to check.
 */
public final class StarsectorFactionFixtures {

    // Fixtures only; never instantiated.
    private StarsectorFactionFixtures() {
    }

    /**
     * A faction answering its two authored shades.
     *
     * @param brightColour the faction's bright colour
     * @param darkColour   the faction's dark colour
     * @return the faction mock
     */
    public static FactionAPI buildFactionShaded(Color brightColour, Color darkColour) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getBrightUIColor())
            .thenReturn(brightColour);
        when(factionMock.getDarkUIColor())
            .thenReturn(darkColour);

        return factionMock;
    }

    /**
     * A sector holding that one faction under {@code factionId} and nothing under any other ID, so
     * a case can tell a lookup that went through the ID it meant from one that fell back.
     *
     * <p>The faction is built before the sector's own stubbing opens: it is itself a mock, and
     * building one inside another stub reads to Mockito as a stub left unfinished.
     *
     * @param factionId    the ID the faction answers to
     * @param brightColour the faction's bright colour
     * @param darkColour   the faction's dark colour
     * @return the sector mock
     */
    public static SectorAPI buildSectorShadingFaction(
            String factionId,
            Color brightColour,
            Color darkColour) {

        var factionMock = buildFactionShaded(brightColour, darkColour);
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getFaction(factionId))
            .thenReturn(factionMock);

        return sectorMock;
    }
}
