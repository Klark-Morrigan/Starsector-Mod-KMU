package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;

/**
 * The sweep that records what each system's own inhabitants can see of the colonies a revelation
 * gate holds back, run on the substrate's own cadence under whatever layers happen to be standing.
 *
 * <p>Beside the register it writes into rather than beside the poll that drives it, so the
 * register's whole write side is in one package. It names no poll type: the refresh machinery adapts
 * it, which keeps this package from importing the machinery that already imports it. Why the sweep
 * is the substrate's rather than a layer's, and what the second reading it opens costs, are stated in
 * the refresh package's README.
 *
 * <p>One reading per sweep, opened here and discarded with it. A colony index alone rather than a
 * whole visibility pass: the sweep asks each system only who lives there, so the drawn-set scan a
 * pass opens beside the index would be paid for and never read. One knowledge for the whole sweep
 * on the same reasoning - opening one folds the sector's alliances, and a sweep opening one per
 * place would refold them for every system in the sector on every poll. It is the fog-only reading
 * rather than any layer's, because what a place's inhabitants can see is a fact about the place: a
 * reveal reaching it would write down observations nobody made.
 */
public final class ColonySightingsPoll {

    // The sector this sweeps. Held from construction rather than resolved per sweep, for the reason
    // every listener beside it is built against one sector: a sweep that read the running game's
    // sector would write another sector's observations into the one the player has loaded.
    private final SectorAPI sector;

    /**
     * @param sector the sector this sweeps; null sweeps nothing rather than throwing, the switch
     *               being flippable with no game loaded
     */
    public ColonySightingsPoll(SectorAPI sector) {
        this.sector = sector;
    }

    /**
     * Writes down what each system's own inhabitants can see of the colonies a revelation gate
     * holds back.
     *
     * <p>Nothing downstream waits on it: the gate keeps its own live reading of a place, so an
     * install where this never ran still shows what the player can plainly see. What the write buys
     * is that the reading survives the witnesses.
     */
    public void recordSightingsByInhabitants() {

        // Null mid-load, before the sector stands up, and null again for a sector holding no
        // systems - both a sweep over nothing rather than a fault.
        var systems = sector == null ? null : sector.getStarSystems();

        if (systems == null) {
            return;
        }
        var colonies = new SectorPassIndex(sector);
        var observing = ColonyKnowledge.observingUnderTheFog();

        for (var system : systems) {
            SectorColonySightings.recordSightingsByInhabitants(
                sector,
                system,
                colonies.readColoniesIn(system),
                observing);
        }
    }
}
