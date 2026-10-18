package kmu.maplayers.base.hover;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * Builds the hover a case poses when what it is about is the cell under the cursor rather than the
 * system that cell names.
 *
 * <p>A hover over a cell carries the live system its key named, so a hand-built one needs a system
 * too. The cases here never read it - a highlight, a park or an expiry turns on the key alone - so a
 * bare stand-in serves, and a case that does read the system builds its hover with the real one.
 */
public final class MapHoverFixture {

    private MapHoverFixture() {
        // fixture of static builders, no instances.
    }

    /**
     * @param hoveredSystemKey        the cell under the cursor
     * @param clusterMemberSystemKeys every cell of its cluster, the hovered one included
     * @return a hover over that cell, carrying a stand-in for the system it names
     */
    public static MapHover buildHoverOn(
            SystemKey hoveredSystemKey,
            List<SystemKey> clusterMemberSystemKeys) {

        return new MapHover(hoveredSystemKey, mock(StarSystemAPI.class), clusterMemberSystemKeys);
    }
}
