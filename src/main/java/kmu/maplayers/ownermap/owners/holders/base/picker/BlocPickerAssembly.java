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
import java.util.function.Predicate;

/**
 * One bloc walk's read turned into the picker a view offers, so a view declares only what
 * distinguishes it - which of the present blocs are targets, and which vocabulary ranks them -
 * rather than repeating the crest, name and option assembly every view resolves identically.
 *
 * <p>It takes the walk's read whole rather than its totals and its presence apart. The two are one
 * reading of the sector ({@link OwnerStatsRead}), so passing them separately would let a view hand
 * over a map from one walk and an index from another.
 *
 * <p>The crest and the label are the view's own reading's, over the walk's grouping, so the rows name
 * and badge each bloc exactly as the map does. A bloc with no crest keeps its row and draws its name
 * alone. The label is always the short form, so a long-form map label never widens the sidebar's
 * rows.
 *
 * <p>The vocabulary is the view's own modes with {@link BlocStandingSortMode} behind them. Where a
 * bloc stands with the player is one fact read off the sector rather than a number any mechanic's
 * fold computes, so it is offered here, where every view's read passes, instead of being declared
 * into each vocabulary.
 *
 * <p>Reached only through {@link HolderPaintedView#readPickerUnderOneGrouping}, and public because
 * that seam sits in the package above. In the holder picker package rather than beside the core
 * picker types, since it asks the view for its gate and reading.
 */
public final class BlocPickerAssembly {

    private BlocPickerAssembly() {
    }

    /**
     * @param <S>             the calling view's own metrics type
     * @param view            the view whose gate picks the rows and whose reading labels them
     * @param sector          the sector a bloc's standing, name and crest are read from
     * @param grouping        the grouping the walk folded under, which the gate, the reading and the
     *                        standing read under too
     * @param statsRead       the walk's totals and the systems behind them, in walk order, which
     *                        the returned rows preserve
     * @param vocabularyModes the calling layer's own sort modes
     * @return the rows the view offers paired with the vocabulary ranking them, beside the walk's
     *         presence
     */
    public static <S extends OwnerMetrics> OwnerPickerRead<RankedOwner<S>> buildBlocPickerRead(
            HolderPaintedView view,
            SectorAPI sector,
            HolderGrouping grouping,
            OwnerStatsRead<S> statsRead,
            ListSortModes<RankedOwner<S>> vocabularyModes) {

        return new OwnerPickerRead<>(
            new ListPicker<>(
                buildSelectableBlocs(
                    view.resolveSelectableBlocGate(grouping),
                    view.resolveOwnerReading(sector, grouping),
                    statsRead.statsByOwnerId()),
                composeOfferedSortModes(sector, grouping, vocabularyModes)),
            statsRead.presenceIndex());
    }

    // The row half of the assembly: each gated bloc paired with its metrics, in walk order. Private
    // because the pairing is only ever half an answer - a list of rows with no vocabulary cannot be
    // ranked and no presence beside it cannot be lit - so the whole read is the only thing worth
    // offering a view.
    private static <S extends OwnerMetrics> List<RankedOwner<S>> buildSelectableBlocs(
            Predicate<String> isSelectable,
            OwnerReading reading,
            Map<String, S> statsByBlocId) {

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
