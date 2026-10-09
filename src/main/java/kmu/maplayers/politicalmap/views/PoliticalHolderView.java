package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.holders.alliances.BlocAffiliation;
import kmu.maplayers.ownermap.owners.holders.base.HolderCategories;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPaintedView;
import kmu.maplayers.ownermap.picker.OwnerMetrics;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.picker.OwnerStatsRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.politicalmap.refresh.PoliticalMapRefreshSignal;
import kmu.mods.nexerelin.NexerelinAlliances;

import java.util.function.Function;

/**
 * A view of the political map: what its three views answer alike, stated once between the holder
 * seam and them.
 *
 * <p>Here rather than on {@link HolderPaintedView}, because every answer names this layer's own
 * inputs - the alliance set and its refresh signal - which a layer painting other holders would
 * have in front of it.
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
     * Who stands together in a contest: the live alliance set, identity wherever Nexerelin is
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
        return new BlocAffiliation(NexerelinAlliances.resolveGrouping());
    }

    /**
     * The categories every view of this layer paints in: the holder categories, read off the
     * settings rows they have always been read from.
     *
     * @return the holder layers' categories
     */
    @Override
    default OwnerCategories resolveCategories() {
        return HolderCategories.INSTANCE;
    }

    /**
     * This view's picker over one sampling of its grouping: the walk is read under it, and the rows
     * are gated, named and badged under it.
     *
     * <p>Sampled once here rather than by each view, because the alliances view samples Nexerelin
     * live. Sampled per read, the numbers and the rows naming them could come off two different
     * memberships.
     *
     * @param <S>             the view's own metrics type
     * @param sector          the sector the rows are read from; null yields an empty read
     * @param statsReader     the view's own walk, read under the sampled grouping
     * @param vocabularyModes the view's own sort modes, which the rows' metrics are ranked by
     * @return the view's picker beside the walk's presence
     */
    default <S extends OwnerMetrics> OwnerPickerRead<RankedOwner<S>> readPickerUnderOneGrouping(
            SectorAPI sector,
            Function<HolderGrouping, OwnerStatsRead<S>> statsReader,
            ListSortModes<RankedOwner<S>> vocabularyModes) {

        var grouping = resolveGrouping();

        return buildBlocPickerRead(sector, grouping, statsReader.apply(grouping), vocabularyModes);
    }
}
