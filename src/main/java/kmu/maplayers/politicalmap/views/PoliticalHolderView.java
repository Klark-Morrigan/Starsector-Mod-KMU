package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.holders.alliances.BlocAffiliation;
import kmu.maplayers.ownermap.owners.holders.base.HolderCategories;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPaintedView;
import kmu.maplayers.ownermap.owners.holders.factions.FactionOwnerReading;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.politicalmap.refresh.PoliticalMapRefreshSignal;
import kmu.mods.nexerelin.NexerelinAlliances;

/**
 * A view of the political map: what its three views answer alike, stated once between the holder
 * seam and them.
 *
 * <p>Here rather than on {@link HolderPaintedView}, because these are this layer's choices: it
 * repaints on and judges contests by the alliance set, and paints factions in the holder categories.
 * A layer painting other holders would have them in front of it.
 */
public interface PoliticalHolderView
        extends HolderPaintedView {

    /**
     * The alliance set's revision, which every view of this layer repaints on.
     *
     * <p>Each view has its own reason. The alliances view paints its blocs out of the set. The other
     * two paint factions apart, but their bands lay a contested run only against a bloc the painter
     * is not allied with, so membership moving in play must repaint those runs.
     *
     * @param board the refresh board of the sector this ask is about
     * @return the content fingerprint of the alliance set
     */
    @Override
    default int getContentRevision(MapLayerRefreshBoard board) {
        return PoliticalMapRefreshSignal.computeAllianceContentRevision(board);
    }

    /**
     * Who stands together in a contest: the live alliance set, nobody wherever Nexerelin is
     * absent.
     *
     * <p>The same whatever a view paints as one bloc, which is what keeps it apart from
     * {@link #resolveGrouping}: the factions and claims views paint allies apart and still read them
     * as allies in a contest.
     *
     * @return the live alliance set, read as who stands together
     */
    @Override
    default OwnerAffiliation resolveContestAffiliation() {
        return new BlocAffiliation(NexerelinAlliances.resolveFactionAlliances());
    }

    /**
     * The categories every view of this layer paints in.
     *
     * @return the holder layers' categories
     */
    @Override
    default OwnerCategories resolveCategories() {
        return HolderCategories.INSTANCE;
    }

    /**
     * Every bloc read as the lone faction whose ID it is, which is right for a view painting each
     * faction as its own bloc. A view folding factions into groups answers with a reading that adds
     * the groups.
     *
     * @param sector   the sector a faction's shades, name and crest are read from
     * @param grouping the grouping the rebuild sampled, which a lone faction's reading has no use for
     * @return the faction reading over the sector
     */
    @Override
    default OwnerReading resolveOwnerReading(SectorAPI sector, HolderGrouping grouping) {
        return new FactionOwnerReading(sector);
    }
}
