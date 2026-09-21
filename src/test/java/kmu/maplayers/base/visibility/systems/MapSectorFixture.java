package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Posing a sector the way the drawn-set rule reads one: the systems it lists, the star anchors
 * its hyperspace holds, and an economy for each system's colonies to be listed in.
 *
 * <p>Stated once here because every suite that drives the rule, or a read over it, had otherwise
 * wired the same three stubs for itself - and a sector wired differently by one suite is a case
 * posed against a world its neighbours are not.
 *
 * <p>Two shapes cover what a case wants of the routes onto the map. An <em>unrouted</em> sector
 * holds no star anchor, so no system is drawn by access and each case decides admission through
 * what it stages on a system - a colony, a decivilised world, the force override. A
 * <em>star-anchored</em>
 * sector holds a visible anchor into each system, so a system with a jump point is drawn by
 * access as the sector's core is. A case wanting hyperspace of its own hands one over.
 *
 * <p>The economy is a mock listing nothing, so a system is uninhabited until a case lists a
 * colony in it. Stubbed at all because the colony walk reads it to tell a listed market from an
 * unlisted one, and answers empty without one - an unstubbed economy would leave every case
 * posing an empty sector.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class MapSectorFixture {

    private MapSectorFixture() {
        // fixture of static wiring, no instances.
    }

    /**
     * A sector listing the given systems with no route onto the map: its hyperspace holds no star
     * anchor and its economy lists nothing.
     */
    public static SectorAPI buildUnroutedSectorOf(StarSystemAPI... systems) {
        return buildSectorOf(buildHyperspaceHolding(), systems);
    }

    /**
     * A sector listing the given systems with a visible star anchor into each, so a system that
     * also carries a jump point is drawn by access.
     */
    public static SectorAPI buildStarAnchoredSectorOf(StarSystemAPI... systems) {

        var anchors = new ArrayList<JumpPointAPI>();

        for (var system : systems) {
            anchors.add(buildStarAnchorTo(system));
        }
        return buildSectorOf(buildHyperspaceHolding(anchors.toArray(JumpPointAPI[]::new)), systems);
    }

    /**
     * A sector listing the given systems under the hyperspace handed over, for a case whose
     * routes onto the map are neither of the two shapes above.
     */
    public static SectorAPI buildSectorOf(LocationAPI hyperspace, StarSystemAPI... systems) {

        // The economy finishes its own wiring before the sector's opens, as the hyperspace handed
        // in already has, so Mockito sees no stubbing nested inside another.
        var economyMock = mock(EconomyAPI.class);
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systems));
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);
        when(sectorMock.getHyperspace())
            .thenReturn(hyperspace);

        return sectorMock;
    }

    /**
     * Hyperspace holding the given star anchors; none poses hyperspace no system is drawn from.
     */
    public static LocationAPI buildHyperspaceHolding(JumpPointAPI... anchors) {

        var hyperspaceMock = mock(LocationAPI.class);

        when(hyperspaceMock.getEntities(JumpPointAPI.class))
            .thenReturn(List.of(anchors));

        return hyperspaceMock;
    }

    /**
     * A star anchor leading into the system, drawn on the map until a case tags it otherwise -
     * which is what lets a hidden variant read as the one thing it changes.
     */
    public static JumpPointAPI buildStarAnchorTo(StarSystemAPI system) {

        var anchorMock = mock(JumpPointAPI.class);

        when(anchorMock.isStarAnchor())
            .thenReturn(true);
        when(anchorMock.getDestinationStarSystem())
            .thenReturn(system);

        return anchorMock;
    }

    /**
     * Lists markets in one system's economy, over a sector built here. The economy is read into a
     * local before the stubbing opens, so Mockito sees no stubbing nested inside another.
     */
    public static void listMarketsIn(SectorAPI sector, StarSystemAPI system, MarketAPI... markets) {

        var economyMock = sector.getEconomy();

        when(economyMock.getMarkets(system))
            .thenReturn(List.of(markets));
    }
}
