package kmu.maplayers.ownermap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.controls.specs.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.ownermap.holding.ColonyReadRules;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.picker.BlocMetrics;
import kmu.maplayers.ownermap.picker.BlocPickerRead;
import kmu.maplayers.ownermap.picker.BlocStandingSortMode;
import kmu.maplayers.ownermap.picker.BlocStatsRead;
import kmu.maplayers.ownermap.picker.RankedBloc;
import kmu.maplayers.ownermap.render.style.OwnerCategories;
import kmu.maplayers.ownermap.sidebar.BodyControlTarget;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * One owner-painted view's rules, read by the shared owner-map pipeline: where its owners come
 * from and how each one looks, the categories they draw in, and what the view adds to the picker,
 * the sidebar body, and the hover box. Gathering them behind one seam makes a new view an added
 * rules object rather than a fork of the render pipeline.
 *
 * <p>The core asks a view two things about its owners - which systems each one holds, and how
 * each one looks - and nothing else in the core knows what an owner is. Both are answered once
 * per rebuild, in one call, and carried through every stage, so styling, naming and the owner map
 * itself are pure lookups over one snapshot rather than re-reads of a live source per cell.
 */
public interface OwnerPaintedView {

    /**
     * This view's stable ID - the string the active-view selection serialises into the save and
     * the view registry resolves a stored pick back to. Frozen once shipped, since renaming it
     * silently resets a save that selected this view to the default.
     *
     * @return the view's save-stable ID
     */
    String getId();

    /**
     * The localisation key for this view's label on the view-selector radio - the segment the
     * player clicks to activate it. A key rather than the resolved string so the segment follows
     * the player's language and the resolution stays with the view radio that draws it.
     *
     * @return the {@code KmuStringKeys} key for this view's radio-segment label
     */
    String getSegmentLabelKey();

    /**
     * A revision fingerprint folding every live input this view samples, folded into the
     * drawables' content token so a change to any of them forces a rebuild even when no
     * setting moved. A view composes it from its own sources ({@link
     * kmlib.math.hashing.Fingerprints#compute}), so its number of live inputs can grow
     * without widening this contract. A view sampling nothing live folds no sources and returns a
     * constant, never triggering a rebuild on its own. This is what lets the shared renderer
     * invalidate on a view's live data without naming any concrete view - each view declares its
     * own fingerprint.
     *
     * <p>The player's own preferences are not folded here: the bake samples those with every other
     * setting and folds them in as values, so a flip that leaves a preference where it was rebuilds
     * nothing.
     *
     * <p>The board arrives as an argument because a view is a stateless strategy every sector's
     * machinery shares, while the counters it folds are one sector's: a view resolving a board of
     * its own would answer one sector's ask off another sector's revisions, which is a wrong number
     * rather than a stale one - the drawing stands as it was and no signal can move the token that
     * would rebuild it.
     *
     * @param board the refresh board of the sector this ask is about, whose revisions the view
     *              folds
     * @return this view's content fingerprint over {@code board}; a constant for a view with no
     *         live inputs
     */
    int getContentRevision(MapLayerRefreshBoard board);

    /**
     * This view's two answers about its owners for one rebuild: the {@link OwnerReading} every
     * shade, name, crest, recede and category is looked up in, and the {@link OwnerSource} that
     * says which systems each owner holds - resolved together, under one sampling.
     *
     * <p>One call rather than two, so the two answers are resolved under one sampling of whatever
     * the view reads live and cannot describe two different folds.
     *
     * <p>The tier retains what comes back for every stage after - the label fit and the incremental
     * refresh included - which is also what keeps the tier from working any of it out for itself
     * off what an owner key happens to mean.
     *
     * <p>Carries no default, deliberately: a default would name one kind of owner's answers in
     * front of every view, including the views that kind does not paint. Views sharing a kind
     * answer it once between them where that kind's own seam is declared.
     *
     * @param sector the sector the rebuild reads; null for a rebuild over no sector, which the
     *               answers treat as holding nothing
     * @return this view, its reading and its source for the rebuild
     */
    ViewReading resolveViewReading(SectorAPI sector);

    /**
     * The categories this view's cells divide into: which exist and how each is styled, which one
     * an owner at full strength draws in, and which one a cell nobody owns falls to.
     *
     * <p>Carries no default: how a map divides is the vocabulary of whoever paints it, and a
     * default would name one kind of owner's division in front of every view.
     *
     * @return this view's categories
     */
    OwnerCategories resolveCategories();

    /**
     * How the owners this view recedes of its own accord draw on one screen - a backdrop the view
     * keeps apart from the spotlight's, such as every bloc outside a group. Read once per rebuild into
     * {@link ContentInputs}, so the owner reading decides which owners recede while this decides how
     * far, off one sampling.
     *
     * <p>Asked of the view rather than held by the tier, because the backdrop and the toggles behind it
     * are the view's own: the tier learns only that a view recedes something, never what. Defaults to
     * receding nothing, which is what a view without such a backdrop answers.
     *
     * @param memoryScope the screen being painted for, whose panel holds the view's toggles
     * @return the adjustment this view's own backdrop draws under;
     *         {@link ElementStyleAdjustment#NONE} for a view that recedes nothing of its own
     */
    default ElementStyleAdjustment resolveViewRecedeAdjustment(ScreenMemoryScope memoryScope) {
        return ElementStyleAdjustment.NONE;
    }

    /**
     * The spotlight picker this view offers: the blocs it lists - every bloc present on the map, or
     * only the kind the view paints - together with the sort vocabulary that ranks them. Each bloc
     * carries the ID the filter stores, its picker label, and its crest. The list
     * is what the picker draws and what
     * {@link kmu.maplayers.base.sidebar.FilterSelection} heals a stale saved selection against, so a
     * bloc that is no longer here is no longer spotlightable.
     *
     * <p>Answered as a {@link BlocPickerRead}, so the same walk hands back where each bloc was found
     * beside the rows it was found for. A view resolves that index from the aggregation it already
     * runs for the numbers, which is what lets a surface light a bloc's systems without a second read
     * of the sector - and without the two readings being able to disagree.
     *
     * <p>The list and the vocabulary are answered together because which blocs a view offers, what
     * numbers those blocs carry, and which of its mechanic's metrics rank them are one decision: a
     * view painted by one mechanic must never be handed a vocabulary reading numbers its blocs do not
     * carry. That is why the return type is wildcarded - the metrics a view's blocs carry are its
     * own, so the layer above passes the picker on without naming them.
     *
     * <p>What a view declares is that mechanic's half. {@link #buildBlocPickerRead} appends the one
     * ranking no vocabulary can declare - where a bloc stands with the player, read off the sector
     * rather than off any fold - so the vocabulary the picker carries is wider than the one the view
     * handed over.
     *
     * <p>There is no new per-bloc seam behind the list: a view says which of its blocs are targets
     * (every bloc, or only the grouped ones) through {@link #resolveSelectableBlocGate}, and
     * {@link #buildBlocPickerRead} assembles the read the same way for every view. The convenience
     * overload reads the player's live visibility settings so a caller with no pass of its own need
     * not thread them.
     *
     * <p>What it does <em>not</em> take is the rule any one mechanic weighs by. Who a picker lists
     * is settled by the sector's colonies, and how a listed bloc's numbers are arrived at is the
     * painting layer's own business - so a seam naming a weighting rule would hand every view a
     * knob only some of them spend, and read the settings behind it for the ones that do not. A
     * layer that weighs takes its rule beside this, the way
     * {@link OwnerSource} leaves the same rule off the owner seam.
     *
     * <p>The spotlight is optional: the default offers an empty read, so a view with no list to
     * spotlight inherits one rather than overriding with two arguments it would ignore. A view
     * opts into the spotlight by overriding this, the same way it opts into its own body controls.
     *
     * @param sector          the sector whose colonies decide who is listed; null yields an empty
     *                        read
     * @param colonyReadRules what the player may be shown of a colony and what a decivilised world
     *                        counts as, so a bloc is offered on the strength of the very colonies
     *                        the map paints it for
     * @return this view's picker - its blocs in the order the source walk surfaces them, and the
     *         vocabulary ranking them - beside where that walk found each bloc; empty when no bloc
     *         qualifies, and empty by default for a view with no spotlight
     */
    default BlocPickerRead<?> resolveBlocPickerRead(
            SectorAPI sector,
            ColonyReadRules colonyReadRules) {
        return BlocPickerRead.empty();
    }

    /**
     * This view's picker under the player's live colony rules ({@link
     * ColonyReadRules#readFromLunaSettings}) - the entry the sidebar and the stale-selection heal
     * call, neither holding a running pass's rules.
     *
     * @param sector the sector whose colonies decide who is listed; null yields an empty read
     * @return this view's picker and presence under the player's live settings; empty when no bloc
     *         qualifies
     */
    default BlocPickerRead<?> resolveBlocPickerRead(SectorAPI sector) {
        return resolveBlocPickerRead(sector, ColonyReadRules.readFromLunaSettings());
    }

    /**
     * Turns one bloc walk's read into the picker read a view offers, so an overriding view declares
     * only what distinguishes it - which of the present blocs are targets, and which vocabulary
     * ranks them - rather than repeating the crest, name, and option assembly every view resolves
     * identically.
     *
     * <p>It takes the walk's read whole rather than its totals and its presence apart. The two are
     * one reading of the sector ({@link BlocStatsRead}), so passing them separately would let a view
     * hand over a map from one walk and an index from another, which is precisely the disagreement
     * the paired read exists to make impossible.
     *
     * <p>The crest and the label are the handed owner reading's, which the calling view resolves
     * over the walk's own grouping, so the rows name and badge each bloc exactly as the map does. A
     * bloc with no crest keeps its option and simply draws its name alone. The label is always in
     * the short form: the picker labels a bloc by its short name regardless of the map's name-format
     * setting, so a long-form map label never widens the sidebar's option rows.
     *
     * <p>It is parameterised on the metrics rather than fixed to one layer's because a
     * view ranks by whatever its own layer is painted from: the identity half of an option is
     * assembled the same way for every view, while the payload half is the calling view's alone. That
     * also keeps this a default method rather than a static - the gate is <em>this</em> view's, so
     * no view has to reach into a sibling for it.
     *
     * <p>The vocabulary it bundles is the caller's own modes with {@link BlocStandingSortMode} behind
     * them, which is why the sector and the grouping are read here beside the reading. Where a
     * bloc stands with the player is one fact read off the sector rather than a number any
     * mechanic's fold computes, so it is offered from the single point every view's read passes
     * through instead of being declared into each vocabulary - which would copy one fact into every
     * one of them and leave each enum naming a sector it has no access to.
     *
     * @param <S>             the calling view's own metrics type, ranked by that view's vocabulary;
     *                        bounded only by what every option must answer of its metrics, never by
     *                        one layer's numbers
     * @param sector          the sector a bloc's standing with the player is read from
     * @param grouping        the grouping the walk folded under, so the gate and a bloc's
     *                        membership resolve against the same snapshot the numbers came from
     * @param reading         the view's reading of its owners over that same grouping, which names
     *                        and badges each row; handed in rather than resolved here so the rows
     *                        and the numbers come off one sampling
     * @param statsRead       the walk's totals and the systems behind them, in the order it surfaced
     *                        them, which the returned rows preserve
     * @param vocabularyModes the calling layer's own modes - the numbers its rows carry - bundled
     *                        with those rows so a row can only ever be sorted by numbers it carries
     * @return the rows this view offers paired with the vocabulary that ranks them, beside the whole
     *         walk's presence
     */
    default <S extends BlocMetrics> BlocPickerRead<RankedBloc<S>> buildBlocPickerRead(
            SectorAPI sector,
            HolderGrouping grouping,
            OwnerReading reading,
            BlocStatsRead<S> statsRead,
            ListSortModes<RankedBloc<S>> vocabularyModes) {

        return BlocPickerAssembly.buildBlocPickerRead(
            this, sector, grouping, reading, statsRead, vocabularyModes);
    }

    /**
     * Which of the present blocs this view offers as spotlight targets. A bloc reaches the test only
     * when the walk already found it, so this decides what is offered among those, never who is
     * present.
     *
     * <p>Defaults to offering every one of them, which is the answer for a view whose blocs are all
     * of a kind. A view whose walk surfaces blocs it does not paint - a view painting groups, where
     * a lone faction is present but is not a group - narrows it here.
     *
     * @param grouping the grouping the blocs were folded under, so a gate that asks what a bloc is
     *                 (a group, a lone faction) reads the same snapshot the numbers came from
     * @return the test a present bloc's ID passes to be listed
     */
    default Predicate<String> resolveSelectableBlocGate(HolderGrouping grouping) {
        return blocId -> true;
    }

    /**
     * The body controls this view contributes to its layer's tab, appended beneath the shared
     * sub-options and the view selector while this view is the selected one. A view that adds no
     * controls of its own returns an empty list (the default), so the body carries only the shared
     * rows; a view with a backdrop of its own returns the toggles behind it, so those show solely
     * under that view.
     * The sidebar body grows downward to fit whatever rows a view adds, so a view opts into its own
     * controls without any layout knowing which view asked.
     *
     * <p>The panel arrives as an argument for the reason the board does on {@link #getContentRevision}:
     * a view is a stateless strategy every sector's machinery shares, so a control it builds has no
     * sector of its own to raise on and no screen of its own to write under.
     *
     * @param target the panel this body was opened on, carried into whatever controls the view
     *               contributes so their writes repaint that sector and are filed as that screen's own
     * @return this view's own body controls, top to bottom; empty when the view adds none
     */
    default List<ControlSpec> getViewBodyControls(BodyControlTarget target) {
        return List.of();
    }

    /**
     * The hover tooltip this view shows for the star system under the cursor, or empty when it shows
     * none. The layer renderer answers the framework's tooltip seam with whatever the active view
     * supplies here, and the shared dispatcher draws it - nothing when it supplies nothing - so a view
     * opts into a tooltip by injecting one rather than flipping a flag. A view injects the box that
     * explains the mechanic its own fills were painted by, since a box read off another mechanic
     * would describe holders the cells do not show. Defaulting to empty makes "no tooltip" the base
     * case, the same shape as {@link #resolveBlocPickerRead} defaulting to no spotlight, so a new
     * view opts in only when it has a tooltip to show.
     *
     * @return this view's hover tooltip, or empty for a view that shows none
     */
    default Optional<MapHoverTooltip> resolveHoverTooltip() {
        return Optional.empty();
    }
}
