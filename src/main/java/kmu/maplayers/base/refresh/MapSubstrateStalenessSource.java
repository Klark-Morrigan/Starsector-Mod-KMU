package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;

/**
 * What the substrate re-reads on its own cadence, under whatever layers happen to be standing:
 * the sweep that records what each system's own inhabitants can see of the colonies a revelation
 * gate holds back.
 *
 * <p>Stales nothing, and no baseline turns on it - a poll is the shape the sweep needs, not a claim
 * about any drawing. Why the sweep is the substrate's rather than a layer's, and what the second
 * reading it opens costs, are stated in this package's README.
 *
 * <p>One reading per poll, opened here and discarded with it. A colony index alone rather than a
 * whole visibility pass: the sweep asks each system only who lives there, so the drawn-set scan a
 * pass opens beside the index would be paid for and never read. One knowledge for the whole sweep
 * on the same reasoning - opening one folds the sector's alliances, and a sweep opening one per
 * place would refold them for every system in the sector on every poll. It is the fog-only reading
 * rather than any layer's, because what a place's inhabitants can see is a fact about the place: a
 * reveal reaching it would write down observations nobody made.
 */
public class MapSubstrateStalenessSource implements MapLayerStalenessSource {

    // The sector this sweeps. Held from construction rather than resolved per poll, for the reason
    // every listener beside it is built against one sector: a poll that read the running game's
    // sector would write another sector's observations into the one the player has loaded.
    private final SectorAPI sector;

    /**
     * @param sector the sector this sweeps; null polls nothing rather than throwing, the switch
     *               being flippable with no game loaded
     */
    public MapSubstrateStalenessSource(SectorAPI sector) {
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
    @Override
    public void markChangesSinceLastPoll() {

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
