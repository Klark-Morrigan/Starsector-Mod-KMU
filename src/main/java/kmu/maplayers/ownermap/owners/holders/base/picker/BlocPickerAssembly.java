package kmu.maplayers.ownermap.owners.holders.base.picker;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPaintedView;
import kmu.maplayers.ownermap.picker.OwnerMetrics;
import kmu.maplayers.ownermap.picker.OwnerPickerRead;
import kmu.maplayers.ownermap.picker.OwnerStatsRead;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.picker.SelectableOwner;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The assembly behind {@link HolderPaintedView#buildBlocPickerRead}: one bloc walk's read turned into
 * the picker a view offers. Held apart from the view seam so the seam reads as its contract.
 *
 * <p>Reached only through {@link HolderPaintedView#buildBlocPickerRead}, which states what it
 * guarantees. Public because that seam sits in the package above.
 *
 * <p>In the holder picker package rather than beside the core picker types, since it asks the view
 * for its gate, and the core picker package stays free of the view seam that builds on it.
 */
public final class BlocPickerAssembly {

    private BlocPickerAssembly() {
    }

    /**
     * The body of {@link HolderPaintedView#buildBlocPickerRead}, whose documentation states the
     * contract.
     *
     * @param <S>             the calling view's own metrics type
     * @param view            the view whose gate picks the rows
     * @param sector          the sector a bloc's standing is read from
     * @param grouping        the grouping the walk folded under
     * @param reading         the view's reading over that grouping, which labels and badges the rows
     * @param statsRead       the walk's totals and the systems behind them, in walk order
     * @param vocabularyModes the calling layer's own sort modes
     * @return the rows the view offers paired with the vocabulary ranking them, beside the walk's
     *         presence
     */
    public static <S extends OwnerMetrics> OwnerPickerRead<RankedOwner<S>> buildBlocPickerRead(
            HolderPaintedView view,
            SectorAPI sector,
            HolderGrouping grouping,
            OwnerReading reading,
            OwnerStatsRead<S> statsRead,
            ListSortModes<RankedOwner<S>> vocabularyModes) {

        return new OwnerPickerRead<>(
            new ListPicker<>(
                buildSelectableBlocs(view, grouping, reading, statsRead.statsByOwnerId()),
                composeOfferedSortModes(sector, grouping, vocabularyModes)),
            statsRead.presenceIndex());
    }

    // The row half of the assembly: each gated bloc paired with its metrics, in walk order. Private
    // because the pairing is only ever half an answer - a list of rows with no vocabulary cannot be
    // ranked and no presence beside it cannot be lit - so the whole read is the only thing worth
    // offering a view.
    //
    // One reading for every row, the caller's over the walk's own grouping, so the rows name and
    // badge each bloc exactly as the map built over that grouping does.
    private static <S extends OwnerMetrics> List<RankedOwner<S>> buildSelectableBlocs(
            HolderPaintedView view,
            HolderGrouping grouping,
            OwnerReading reading,
            Map<String, S> statsByBlocId) {

        var isSelectable = view.resolveSelectableBlocGate(grouping);
        var selectableBlocs = new ArrayList<RankedOwner<S>>();

        for (var entry : statsByBlocId.entrySet()) {

            var blocId = entry.getKey();
            if (!isSelectable.test(blocId)) {
                continue;
            }

            var identity = new SelectableOwner(
                blocId,
                reading.resolveName(blocId, OwnerNameFormatChoice.SHORT),
                reading.resolveCrestPath(blocId));

            selectableBlocs.add(new RankedOwner<>(identity, entry.getValue()));
        }
        return selectableBlocs;
    }

    // The whole vocabulary the picker is offered: the calling layer's own modes, then the standing
    // mode shared by every view. Appended last, so it takes the bottom row of the sort selector
    // beneath the numbers the layer is painted by; the fallback mode is the layer's own, since the
    // standing is a criterion a player picks rather than one a fresh save should open on.
    private static <S extends OwnerMetrics> ListSortModes<RankedOwner<S>> composeOfferedSortModes(
            SectorAPI sector,
            HolderGrouping grouping,
            ListSortModes<RankedOwner<S>> vocabularyModes) {

        // No sector is no relations to read and no rows to rank - the read this builds is empty -
        // so the vocabulary stands as its layer declared it rather than gaining a mode with nothing
        // behind it to read.
        if (sector == null) {
            return vocabularyModes;
        }

        var offeredModes = new ArrayList<ListSortMode<RankedOwner<S>>>(vocabularyModes.modes());

        offeredModes.add(
            new BlocStandingSortMode<>(BlocStandingReader.createForSector(sector, grouping)));

        return new ListSortModes<>(offeredModes, vocabularyModes.defaultMode());
    }
}
