package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.sidebar.SelectionSlot;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;

/**
 * Every sidebar preference a rebuild bakes into the map rather than applies while drawing it:
 * which bloc is spotlighted, how the sector recedes behind that spotlight, how a non-allied bloc
 * recedes, how a cluster name is spelled, and whether an uninhabited system is outlined.
 *
 * <p>The five are one value because they are one sampling, exactly as {@link CellCutInputs} is for
 * the cut. Each of them reaches several stages of a rebuild - the holding, the fills, the label
 * fit, the bands - and each is stored per save rather than as a LunaLib field, so none of them
 * moves the settings revision that every other style input travels on. Read per stage, a
 * preference flipped mid-rebuild leaves the cells shaped under one answer and named under another;
 * sampled once and carried, every stage bakes the same picks.
 *
 * <p>It is also what tells a rebuild it is owed one: these values are folded into the content
 * revision in place of the refresh counters their flips raise, so two readings holding the same
 * picks are the same bake however many counters have moved between them.
 *
 * @param selectedBlocId            the spotlighted bloc, or null off filter
 * @param filterRecedeAdjustment    how the rest of the sector recedes behind that spotlight;
 *                                  {@link ElementStyleAdjustment#NONE} off filter, so the pick and
 *                                  the recede it implies cannot be read from two samplings
 * @param allianceRecedeAdjustment  how a bloc the alliances view recedes draws; which blocs those
 *                                  are is the view's per-bloc call, not this record's
 * @param nameFormat                how a cluster label spells its holder's name, including whether
 *                                  it draws at all
 * @param isUninhabitedOutlineDrawn whether never-settled space strokes its outline
 */
public record ContentInputs(
    String selectedBlocId,
    ElementStyleAdjustment filterRecedeAdjustment,
    ElementStyleAdjustment allianceRecedeAdjustment,
    FactionNameFormatChoice nameFormat,
    boolean isUninhabitedOutlineDrawn) {

    /**
     * Reads every preference a bake is shaped by, once.
     *
     * <p>The spotlight is per view - each view's picker keeps its own pick - so the view is what
     * the pick is read under, and the recede behind it is resolved here rather than by whoever
     * paints: off filter there is no spotlight for a backdrop to sit behind, so the recede is the
     * identity and a Mute or Desaturate flip made while nothing is spotlighted rebuilds nothing.
     *
     * <p>It is also per screen, since a spotlight is a pick made on one panel, so the screen the
     * frame is painting for is what the pick is read under. The caller supplies it rather than this
     * resolving one, so the picture a frame paints and the picks it paints from cannot name two
     * screens.
     *
     * @param view        the view being painted, whose picker holds the spotlight pick
     * @param memoryScope the screen being painted for, whose panel holds that pick
     * @return this frame's reading of the preferences the bake is under
     */
    public static ContentInputs sampleForView(PoliticalMapView view, ScreenMemoryScope memoryScope) {

        var selectedBlocId =
            FilterSelection.getSelectedIdOf(new SelectionSlot(memoryScope, view.getId()));

        // TODO: read the four below under memoryScope too - the recede sets, the name format and the
        // outline flag are still one slot per sector, so only the spotlight is this screen's own.

        return new ContentInputs(
            selectedBlocId,
            selectedBlocId == null
                ? ElementStyleAdjustment.NONE
                : RecedePreferences.FILTER.resolveRecedeAdjustment(),
            RecedePreferences.ALLIANCE_NON_ALLIED.resolveRecedeAdjustment(),
            NameFormatPreference.getSelectedNameFormat(),
            UninhabitedOutlinePreference.isOutlineDrawn());
    }

    /**
     * The inert reading a build that sampled nothing carries - the placeholder the render path
     * falls back on after a failed first build, where no stage got far enough to read a
     * preference.
     *
     * <p>Named here rather than spelt out by each such caller for the reason
     * {@code MapStyling.createEmpty} is: "nothing sampled yet" is one value every such pass
     * shares, and the defaults it holds only look arbitrary until one of them turns out not to be.
     *
     * @return the inert reading: nothing spotlighted, nothing receded, full names, no outline
     */
    public static ContentInputs createEmpty() {
        return new ContentInputs(
            null,
            ElementStyleAdjustment.NONE,
            ElementStyleAdjustment.NONE,
            FactionNameFormatChoice.FULL,
            false);
    }

    /**
     * This same reading with the spotlight dropped, for a pass that paints the whole sector
     * whatever the picker holds - the debug border-tracing view, which resolves real dominant
     * holders and recedes nothing.
     *
     * <p>Dropped rather than replaced with {@link #createEmpty()}, because such a pass is still
     * subject to every other pick: it draws the names the player asked for, in the format they
     * asked for.
     *
     * @return this reading with no bloc spotlighted and no filter recede
     */
    public ContentInputs clearFilterPick() {
        return new ContentInputs(
            null,
            ElementStyleAdjustment.NONE,
            allianceRecedeAdjustment,
            nameFormat,
            isUninhabitedOutlineDrawn);
    }

    /**
     * @return whether a bloc is spotlighted, which is exactly when one was picked - the gate the
     *         shared cell and faction builders bypass the view's per-bloc styling for the filter's
     */
    public boolean isFiltering() {
        return selectedBlocId != null;
    }
}
