package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;

/**
 * What the substrate re-reads on its own cadence, under whatever layers happen to be standing.
 *
 * <p>One passenger today, and it writes rather than reads. A colony a revelation gate holds back is
 * observed by the people living around it, and that observation has to be recorded or the colony
 * drops off the map the day its last neighbour dies - yet nothing in the engine announces a
 * derelict arriving among witnesses. So the sweep needs a poll, and the poll is this one.
 *
 * <p>The substrate's rather than any one layer's, because the record is shared. Several map
 * families read it, and a layer is something the player can take off the bar - so a record whose
 * accrual followed one layer would have its gaps decided by an unrelated preference. Nothing would
 * report that: what is lost is a colony's witnesses, months later, on a map that has since been put
 * back.
 *
 * <p>Stales nothing, and no baseline turns on it. A poll is the shape it needs - a sector-wide
 * sweep on the cadence an unannounced arrival deserves - rather than a claim about what has gone
 * out of date, so this raises on no refresh board and holds no diff against a previous call.
 *
 * <p>One reading per poll, opened here and discarded with it. A colony index alone rather than a
 * whole visibility pass: the sweep asks each system only who lives there, so the drawn-set scan a
 * pass opens beside the index would be paid for and never read. One knowledge for the whole sweep
 * on the same reasoning - opening one folds the sector's alliances, and a sweep opening one per
 * place would refold them for every system in the sector on every poll. It is the fog-only reading
 * rather than any layer's, because what a place's inhabitants can see is a fact about the place: a
 * reveal reaching it would write down observations nobody made.
 *
 * <p>The cost is a second reading of the sector's colonies, this poll's alongside the political
 * map's, where one served both while the sweep rode that one. Taken as it stands: a reading handed
 * from the framework down to a layer is the frame sequence's shape rather than this poll's, and the
 * profiling scopes already in place are what would say whether it is worth arranging sooner.
 *
 * <p>The sector loop is here rather than inside the register because the cadence was this poll's
 * decision in the first place, and because the register is written place by place over sets a
 * caller already holds - every other caller being a pass that has walked the sector for its own
 * reasons.
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
        var colonies = new SystemColoniesIndex(sector);
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
