package kmu.maplayers.base.tooltip.layout;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;

import org.apache.log4j.Logger;

import java.util.function.Supplier;

/**
 * The hover box one sector last composed, kept while the cursor rests on its cell.
 *
 * <p>A box is drawn every frame the cursor is over a cell, and composing one reads the sector:
 * the claim decree, the ranking, and at the deeper levels a walk of every colony in the system. All
 * of that answers the same thing frame after frame while the cursor is still, so it is read once
 * and laid out again from this.
 *
 * <p>One entry, because the cursor is over one cell at a time. Kept until something says the box
 * would read differently:
 *
 * <ul>
 *   <li>another box, system or detail level is asked for;</li>
 *   <li>the cursor reached a cell again, stepping off and back onto this one included
 *       ({@link MapHoverState#getCellArrivalCount()});</li>
 *   <li>a layer rebuilt what it draws ({@link MapLayerCommonRefreshSignal#DRAW_LISTS}), which every
 *       setting, colony event and alliance change that moves the map leads to.</li>
 * </ul>
 *
 * <p>What none of those see is the economy drifting under a still cursor - a colony's stability or
 * size moving with no event fired. The box then shows the reading from when the cursor arrived,
 * which is the same lag the map's own fills have between rebuilds, and the next visit to the cell
 * reads it afresh.
 *
 * <p>Held per sector by that sector's machinery rather than by the box, because one box instance
 * serves every sector and what it composed is one sector's reading. Released with the machinery.
 */
public final class CellBoxMemo
        implements InstalledMachinery {

    private static final Logger LOG = Global.getLogger(CellBoxMemo.class);

    // Where the cursor's visits to a cell are counted, on the sector this memo belongs to.
    private final MapHoverState hoverState;

    // Where a rebuild of what the map draws is announced, on the same sector.
    private final MapLayerRefreshBoard refreshBoard;

    // What the last box composed was asked under, and what it came to. Null until a box is composed
    // and again once released.
    private ComposedBoxKey memoisedKey;
    private ComposedCellBox memoisedBox;

    CellBoxMemo(MapHoverState hoverState, MapLayerRefreshBoard refreshBoard) {

        this.hoverState = hoverState;
        this.refreshBoard = refreshBoard;
    }

    /**
     * @param machinery the machinery of the sector the box describes
     * @return that sector's memo, holding nothing until a box is first composed
     */
    public static CellBoxMemo resolveMemoIn(SectorMapMachinery machinery) {

        return machinery.resolveMachinery(
            CellBoxMemo.class,
            () -> new CellBoxMemo(machinery.resolveHoverState(), machinery.resolveRefreshBoard()));
    }

    @Override
    public void disposeMachinery() {

        memoisedKey = null;
        memoisedBox = null;
    }

    /**
     * The box {@code tooltip} composes for {@code system} at {@code detailLevel}: the one held where
     * nothing has moved since it was composed, or a fresh composition held in its place.
     *
     * @param tooltip     the box being drawn, which one entry is held for at a time
     * @param sector      the sector the box describes - named in the key because the machinery
     *                    standing in where no game is loaded is shared by every sector without one
     * @param system      the star system under the cursor
     * @param detailLevel how deep the box is being read
     * @param composeBox  reads the sector and composes the box, asked only where the held one has
     *                    gone stale
     * @return what the box states for that system at that level
     */
    ComposedCellBox resolveBox(
            SystemCellTooltip tooltip,
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel,
            Supplier<ComposedCellBox> composeBox) {

        var key = new ComposedBoxKey(
            tooltip,
            sector,
            SystemKey.readKeyOf(system),
            detailLevel,
            hoverState.getCellArrivalCount(),
            refreshBoard.getRevision(MapLayerCommonRefreshSignal.DRAW_LISTS));

        if (!key.equals(memoisedKey)) {

            memoisedBox = composeBox.get();
            memoisedKey = key;

            logBoxComposed(key);
        }

        return memoisedBox;
    }

    // Traces each composition: which system and level it read, and the two counters it was keyed
    // under, so a box that looks stale in play can be checked against what last read it. Written
    // once per composition rather than per frame, which is the whole point of the memo. Set KMU log
    // verbosity to DEBUG in LunaLib to see it.
    private static void logBoxComposed(ComposedBoxKey key) {

        if (!LOG.isDebugEnabled()) {
            return;
        }

        LOG.debug("Hover box composed; system=" + key.systemKey()
            + " level=" + key.detailLevel()
            + " cellArrivals=" + key.cellArrivalCount()
            + " drawListsRevision=" + key.drawListsRevision());
    }

    /**
     * Everything a held box is answerable to. The box and the sector are compared by identity, there
     * being nothing else to tell two of either apart by.
     *
     * @param tooltip           the box composed
     * @param sector            the sector it was composed over
     * @param systemKey         the system it was composed for
     * @param detailLevel       the level it was read to
     * @param cellArrivalCount  the cursor's visits to a cell when it was composed
     * @param drawListsRevision the rebuilds of what the map draws when it was composed
     */
    private record ComposedBoxKey(
        SystemCellTooltip tooltip,
        SectorAPI sector,
        SystemKey systemKey,
        HoverTooltipDetailLevel detailLevel,
        int cellArrivalCount,
        int drawListsRevision) {
    }
}
