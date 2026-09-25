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
import kmu.maplayers.ownermap.owners.holders.HolderProvider;
import kmu.maplayers.ownermap.picker.BlocMetrics;
import kmu.maplayers.ownermap.picker.BlocPickerRead;
import kmu.maplayers.ownermap.picker.BlocStandingSortMode;
import kmu.maplayers.ownermap.picker.BlocStatsRead;
import kmu.maplayers.ownermap.picker.RankedBloc;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.ribbon.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;
import kmu.maplayers.ownermap.sidebar.BodyControlTarget;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * One owner-painted view's rules, read by the shared owner-map pipeline: how the view folds
 * factions into blocs, where each system's holder comes from, how a bloc is styled and named, and
 * what the view adds to the picker, the sidebar body, and the hover box. Gathering them behind one
 * seam makes a new view an added rules object rather than a fork of the render pipeline.
 *
 * <p>The grouping is resolved once per pass and handed back into the per-bloc decisions, so the
 * style classifier and the name resolver stay pure lookups over that one snapshot rather than
 * re-reading a live source per cell.
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
     * The holder grouping this view resolves its pass under: the identity grouping for a view
     * painting every faction as its own bloc, named groups for a view folding several factions into
     * one. Resolved once per rebuild and threaded through the pipeline, so a live set is sampled a
     * single time per pass and every stage keys off the same snapshot.
     *
     * @return the grouping that collapses factions into blocs for this pass
     */
    HolderGrouping resolveGrouping();

    /**
     * Which blocs stand together in a contest this view's bands judge: two blocs this grouping
     * folds together share a system as allies rather than as rivals.
     *
     * <p>Apart from {@link #resolveGrouping}, which decides what a cell is painted as: blocs a view
     * paints apart can still stand together when a system is contested. Declared rather than
     * defaulted, since who stands with whom is the layer's own answer; a layer in which nobody does
     * answers with the identity grouping.
     *
     * <p>Asked once per bake, so every band in it is judged against one reading.
     *
     * @return the grouping a band judges its contest against
     */
    HolderGrouping resolveContestGrouping();

    /**
     * The source this view resolves its per-system holder from. Supplied per view so the
     * pipeline reads holders through one seam without naming a concrete resolver, exactly as it
     * reads {@link #resolveGrouping}.
     *
     * <p>Carries no default, deliberately: a default would name one mechanic's resolver in front
     * of every view, including the views that mechanic does not paint. Views sharing a mechanic
     * answer it once between them where that mechanic's own seam is declared.
     *
     * @return the provider that resolves this view's per-system holder
     */
    HolderProvider resolveHolderProvider();

    /**
     * The mechanic this view's cells are counted by for their presence bands.
     *
     * <p>Answered by every view rather than defaulted here, for the reason a shared base for one
     * mechanic's views exists: a default would have to name that mechanic's planner, and naming it
     * on this seam would put it in front of every view including the ones that mechanic does not
     * paint. Views sharing a mechanic answer it once between them, and a view painted by another
     * answers with its own.
     *
     * <p>Per view at all for the same reason the hover box is: a band explains the fill it sits
     * inside, so counting it by a mechanic other than the one the cell was painted by would lead
     * the band on a bloc the cell is not painted for, or count a set of colonies the fill's own
     * score never saw.
     *
     * @param inputs everything one bake's bands are settled from, sampled once by the bake - the
     *               grouping among it, so a band folds factions into blocs exactly as the fill did
     * @return the planner this view's bands are counted through
     */
    SystemRibbonPlanner resolveRibbonPlanner(RibbonPlanInputs inputs);

    /**
     * Whether a bloc paints in the muted independent cell style rather than the full
     * faction style. A view painting lone factions styles only independent space this way; a
     * view setting groups against a backdrop styles every bloc outside a group this way, so the
     * ungrouped recede while groups stand out in full colour.
     *
     * <p>The bloc's already-resolved {@code adjustment} is supplied so a view that keys the
     * style bundle off desaturation reads the one adjustment the pass actually paints under -
     * every reason to recede already folded in - rather than re-deriving it from a source of
     * its own. A view answering from a narrower source than the palette resolves from would
     * paint a bloc in the desaturation palette while leaving it in the faction bundle.
     *
     * @param blocId     the winning bloc for a system, as resolved under {@code grouping}
     * @param grouping   the grouping this pass resolved, supplied so the test is a pure
     *                   lookup over the once-sampled snapshot rather than a fresh read
     * @param adjustment the dimming and recolouring this bloc draws under, resolved ahead of
     *                   this test so both read one decision
     * @return true when the bloc takes the independent style
     */
    boolean shouldUseIndependentStyle(
        String blocId,
        HolderGrouping grouping,
        ElementStyleAdjustment adjustment);

    /**
     * How a bloc's fills, borders, and name are dimmed or recoloured before the pipeline
     * paints it, applied uniformly wherever the style classification is read. A view with no
     * backdrop of its own never adjusts a bloc ({@link ElementStyleAdjustment#NONE}); a view setting
     * groups against a backdrop dims and/or desaturates every bloc outside a group when the player
     * has asked it to, leaving groups untouched. Resolving it here lets the pipeline apply the two knobs without
     * knowing why a view wanted them, mirroring the {@link #shouldUseIndependentStyle} seam.
     *
     * @param blocId        the winning bloc for a system, as resolved under {@code grouping}
     * @param grouping      the grouping this pass resolved, so the decision is a pure lookup over
     *                      the once-sampled snapshot rather than a fresh read
     * @param contentInputs the preferences this bake sampled, so a view that recedes blocs of its
     *                      own reads its recede out of the rebuild's one sampling rather than off
     *                      the stored preference a second time
     * @return the per-bloc styling adjustment; {@link ElementStyleAdjustment#NONE} to draw the
     *         bloc exactly as classified
     */
    ElementStyleAdjustment resolveBlocStyleAdjustment(
        String blocId,
        HolderGrouping grouping,
        ContentInputs contentInputs);

    /**
     * How the blocs this view recedes of its own accord draw on one screen - a backdrop the view keeps
     * apart from the spotlight's, such as every bloc outside a group. Read once per rebuild into
     * {@link ContentInputs}, so {@link #resolveBlocStyleAdjustment} decides which blocs recede while
     * this decides how far, off one sampling.
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
     * The label a bloc reads under this view: a faction's display name for a lone-faction
     * bloc, the group's name for a grouped bloc. Null when no name resolves, which
     * the label fit treats as an unresolved name and sizes a stand-in band for instead.
     *
     * @param blocId     the winning bloc to name, as resolved under {@code grouping}
     * @param grouping   the grouping this pass resolved
     * @param sector     the sector, from which a faction bloc's display name is read
     * @param nameFormat whether a faction name reads in its short or full form; one of the
     *                   drawn forms, since the whole label build is skipped when the player's
     *                   choice draws no name at all
     * @return the bloc's display name, or null when none resolves
     */
    String resolveName(
        String blocId,
        HolderGrouping grouping,
        SectorAPI sector,
        FactionNameFormatChoice nameFormat);

    /**
     * The spotlight picker this view offers: the blocs it lists - every bloc present on the map, or
     * only the kind the view paints - together with the sort vocabulary that ranks them. Each bloc
     * carries the ID the filter stores, its picker label, and its colour faction's crest. The list
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
     * {@link kmu.maplayers.ownermap.owners.holders.HolderProvider} leaves the same rule
     * off the holder seam.
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
     * <p>The crest comes from the bloc's colour faction, which is a group's leading member and, for
     * a faction bloc, the faction itself, so one lookup serves a grouped and an ungrouped view alike.
     * A bloc with no authored crest keeps its option and simply draws its name alone. The label is
     * this view's own {@link #resolveName}, always in the short form: the picker labels a bloc by its
     * short name regardless of the map's name-format setting, so a long-form map label never widens
     * the sidebar's option rows.
     *
     * <p>It is parameterised on the metrics rather than fixed to one layer's because a
     * view ranks by whatever its own layer is painted from: the identity half of an option is
     * assembled the same way for every view, while the payload half is the calling view's alone. That
     * also keeps this a default method rather than a static - the label is <em>this</em> view's
     * {@link #resolveName}, so no view has to reach into a sibling for a name.
     *
     * <p>The vocabulary it bundles is the caller's own modes with {@link BlocStandingSortMode} behind
     * them, which is why the sector and the grouping are read here for more than the crest. Where a
     * bloc stands with the player is one fact read off the sector rather than a number any
     * mechanic's fold computes, so it is offered from the single point every view's read passes
     * through instead of being declared into each vocabulary - which would copy one fact into every
     * one of them and leave each enum naming a sector it has no access to.
     *
     * @param <S>             the calling view's own metrics type, ranked by that view's vocabulary;
     *                        bounded only by what every option must answer of its metrics, never by
     *                        one layer's numbers
     * @param sector          the sector a bloc's colour faction and its standing with the player are
     *                        read from
     * @param grouping        the grouping the walk folded under, so the colour faction, the name, the
     *                        gate, and a bloc's membership all resolve against the same snapshot the
     *                        numbers came from
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
            BlocStatsRead<S> statsRead,
            ListSortModes<RankedBloc<S>> vocabularyModes) {

        return BlocPickerAssembly.buildBlocPickerRead(this, sector, grouping, statsRead, vocabularyModes);
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
