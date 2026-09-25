package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.picker.BlocMetrics;
import kmu.maplayers.ownermap.picker.BlocPickerRead;
import kmu.maplayers.ownermap.picker.BlocStandingReader;
import kmu.maplayers.ownermap.picker.BlocStandingSortMode;
import kmu.maplayers.ownermap.picker.BlocStatsRead;
import kmu.maplayers.ownermap.picker.RankedBloc;
import kmu.maplayers.ownermap.picker.SelectableBloc;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The assembly behind {@link OwnerPaintedView#buildBlocPickerRead}: one bloc walk's read turned into
 * the picker a view offers. Held apart from the view seam so the seam reads as its contract; package
 * private because a view reaches it only through that default, which states what it guarantees.
 *
 * <p>In this package rather than beside the picker types, since it asks the view for its gate and its
 * names, and the picker package stays free of the view seam that builds on it.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
final class BlocPickerAssembly {

    private BlocPickerAssembly() {
        // utility class, no instances.
    }

    /**
     * The body of {@link OwnerPaintedView#buildBlocPickerRead}, whose documentation states the
     * contract.
     *
     * @param <S>             the calling view's own metrics type
     * @param view            the view whose gate picks the rows and whose names label them
     * @param sector          the sector a bloc's colour faction and its standing are read from
     * @param grouping        the grouping the walk folded under
     * @param statsRead       the walk's totals and the systems behind them, in walk order
     * @param vocabularyModes the calling layer's own sort modes
     * @return the rows the view offers paired with the vocabulary ranking them, beside the walk's
     *         presence
     */
    static <S extends BlocMetrics> BlocPickerRead<RankedBloc<S>> buildBlocPickerRead(
            OwnerPaintedView view,
            SectorAPI sector,
            HolderGrouping grouping,
            BlocStatsRead<S> statsRead,
            ListSortModes<RankedBloc<S>> vocabularyModes) {

        return new BlocPickerRead<>(
            new ListPicker<>(
                buildSelectableBlocs(view, sector, grouping, statsRead.statsByBlocId()),
                composeOfferedSortModes(sector, grouping, vocabularyModes)),
            statsRead.presenceIndex());
    }

    // The row half of the assembly: each gated bloc paired with its metrics, in walk order. Private
    // because the pairing is only ever half an answer - a list of rows with no vocabulary cannot be
    // ranked and no presence beside it cannot be lit - so the whole read is the only thing worth
    // offering a view.
    private static <S extends BlocMetrics> List<RankedBloc<S>> buildSelectableBlocs(
            OwnerPaintedView view,
            SectorAPI sector,
            HolderGrouping grouping,
            Map<String, S> statsByBlocId) {

        var isSelectable = view.resolveSelectableBlocGate(grouping);
        var selectableBlocs = new ArrayList<RankedBloc<S>>();

        for (var entry : statsByBlocId.entrySet()) {
            var blocId = entry.getKey();
            if (!isSelectable.test(blocId)) {
                continue;
            }
            var colourFaction = sector.getFaction(grouping.resolveColourFactionId(blocId));
            var identity = new SelectableBloc(
                blocId,
                view.resolveName(blocId, grouping, sector, FactionNameFormatChoice.SHORT),
                FactionCrests.resolveCrestPath(colourFaction));

            selectableBlocs.add(new RankedBloc<>(identity, entry.getValue()));
        }
        return selectableBlocs;
    }

    // The whole vocabulary the picker is offered: the calling layer's own modes, then the standing
    // mode shared by every view. Appended last, so it takes the bottom row of the sort selector
    // beneath the numbers the layer is painted by; the fallback mode is the layer's own, since the
    // standing is a criterion a player picks rather than one a fresh save should open on.
    private static <S extends BlocMetrics> ListSortModes<RankedBloc<S>> composeOfferedSortModes(
            SectorAPI sector,
            HolderGrouping grouping,
            ListSortModes<RankedBloc<S>> vocabularyModes) {

        // No sector is no relations to read and no rows to rank - the read this builds is empty -
        // so the vocabulary stands as its layer declared it rather than gaining a mode with nothing
        // behind it to read.
        if (sector == null) {
            return vocabularyModes;
        }
        var offeredModes = new ArrayList<ListSortMode<RankedBloc<S>>>(vocabularyModes.modes());

        offeredModes.add(
            new BlocStandingSortMode<>(BlocStandingReader.createForSector(sector, grouping)));

        return new ListSortModes<>(offeredModes, vocabularyModes.defaultMode());
    }
}
