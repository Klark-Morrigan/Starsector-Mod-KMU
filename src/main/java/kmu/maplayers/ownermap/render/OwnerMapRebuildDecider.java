package kmu.maplayers.ownermap.render;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferences;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuOwnerMapGeometrySettings;

import java.util.Objects;
import java.util.Set;

/**
 * Decides what a frame owes an owner map: whether the cells have to be recut, whether the
 * clusters have to be rebuilt, and whether the holding the last rebuild read can be painted
 * over again rather than read afresh.
 *
 * <p>Apart from the cache that acts on the answer, because the two run at different cadences.
 * Nearly every frame the map is open asks this and stops: the answer comes off revisions and
 * settings alone, costs a handful of int compares, and opens no reading of the sector. A rebuild
 * is the rare frame, and it opens one. Holding the decision beside the rebuild paths put the
 * cheap question and the expensive answer behind one class, where a reader of either had to read
 * both.
 *
 * <p>What it holds is the baselines the question is asked against - the revisions the standing
 * map was built under and what its cells were cut from - and those advance only when the cache
 * reports a stage complete, so a rebuild that threw part way is asked again next frame rather
 * than being taken as done.
 *
 * <p>The board every revision is read off is the machinery's own, so a signal raised in another
 * sector cannot restyle these cells - and a signal raised in this one cannot fail to.
 */
final class OwnerMapRebuildDecider {

    // The content revision's seed, below any revision the settings fold can report on a built
    // map, so a cache's first frame finds the draw lists stale and builds them.
    private static final int UNBUILT_REVISION = -1;

    // The machinery installed on the sector the map draws, whose board every revision here is
    // read off.
    private final SectorMapMachinery machinery;

    // The drawing layer's body preferences, stored under that layer's own keys - which every rebuild
    // samples the picks a bake is under from.
    private final OwnerMapBodyPreferences bodyPreferences;

    // The revision the clusters were built against. They rebuild on a content change
    // (settings) or whenever the geometry itself was rebuilt. Starts at the unbuilt seed, so the
    // first frame builds.
    private int lastContentRevision = UNBUILT_REVISION;

    // The revision the standing holding was resolved under: the content revision's holding half,
    // the inputs that reach the resolve and none of the picks that only reach the paint. A rebuild
    // that owes no new reading of the sector - a style pick moved and nothing else - paints over
    // what the last one read rather than walking the economy for the same answer.
    private int lastHoldingRevision = UNBUILT_REVISION;

    // What the cells currently held were cut from. One value rather than a revision, the seed
    // inputs and the dev toggles side by side, because each of the three recuts every cell and
    // the question asked of them is the single one they answer together: would the cells be cut
    // differently now. Null (not a zero reading) is the never-cut state, since every reading the
    // record can hold is one the player can actually be under - and it is what makes a cache's
    // first frame cut, whatever the signal happens to say.
    private CellCutInputs lastCellCut;

    OwnerMapRebuildDecider(SectorMapMachinery machinery, OwnerMapBodyPreferences bodyPreferences) {
        this.machinery = machinery;
        this.bodyPreferences = bodyPreferences;
    }

    /**
     * Whether the holding the last rebuild read still says who holds what.
     *
     * <p>It does unless the sector moved under it - a marked system, or a recut, which admits or
     * drops systems - or the rule reading it did, which the holding revision folds. Never while
     * nothing is standing to keep.
     *
     * @param staleHalves        this frame's decision
     * @param hasStandingHolding whether a holding from an earlier rebuild is held at all
     * @param staleSystemKeys    the systems marked stale since the last drain, each a system the
     *                           sector moved under
     * @return whether the standing holding may be painted over rather than read afresh
     */
    public boolean canReuseStandingHolding(
            StaleHalves staleHalves,
            boolean hasStandingHolding,
            Set<SystemKey> staleSystemKeys) {

        return hasStandingHolding
            && staleSystemKeys.isEmpty()
            && !staleHalves.isCellCutStale()
            && staleHalves.holdingRevision() == lastHoldingRevision;
    }

    /**
     * What this frame owes, decided off revisions and settings alone so it costs nothing on a
     * frame that owes nothing - which is nearly all of them.
     *
     * @param view            the view being painted, whose live inputs and spotlight pick are
     *                        folded
     * @param memoryScope     the screen being painted for, whose panel holds every sidebar
     *                        preference the picks are sampled under
     * @param hasNothingBuilt whether neither base view has been built yet, which forces a build
     *                        even if the content revision happens to match its unbuilt seed
     * @return the decision, carrying every sampled input the rebuild it may owe has to build under
     */
    public StaleHalves decideWhatIsStale(
            OwnerPaintedView view,
            ScreenMemoryScope memoryScope,
            boolean hasNothingBuilt) {

        // What the cells would be cut from right now. The reachable-set revision alone does not
        // answer that: the frontier resolution, the cell reach and the two visibility overrides
        // each reseed every cell without it moving, so all four are read into one value and the
        // staleness question is asked of that value rather than of the signal.
        //
        // This is also the rebuild's one sampling of the visibility rules: the cut, the fills and
        // the bands all resolve under what it holds, so a gate flipped mid-rebuild cannot leave
        // cells cut under one rule and painted under another.
        var cellCut = new CellCutInputs(
            machinery.resolveRefreshBoard().getRevision(MapLayerCommonRefreshSignal.GEOMETRY),
            new CellSeedInputs(
                KmuOwnerMapGeometrySettings.getOwnerMapCellBoundSegments(),
                KmuOwnerMapGeometrySettings.getOwnerMapCellRadius()),
            MapVisibilityRules.readFromLunaSettings());

        var isCellCutStale = !cellCut.equals(lastCellCut);

        // The rebuild's one sampling of the sidebar preferences, taken here beside the cut's for
        // the same reason: every stage that bakes one reads this reading, and the staleness
        // question below is asked of the values rather than of the counters their flips raise.
        //
        // Sampled under the screen the frame handed over, which is the screen it also took the view
        // off, so the picks a rebuild bakes and the view it builds under name one panel.
        var contentInputs = ContentInputs.sampleForView(view, bodyPreferences, memoryScope);

        // TODO: when only geometry changed (isCellCutStale), reshape just the cells
        // CellGeometryCache rebuilt - the system that gained or lost access and every cell
        // it touches - rather than the full clusters rebuild the cache runs. Have
        // updateFromSector report its affected-cell set and drive a targeted reshape from it, the
        // geometry-side analogue of the marked-system fold.

        // Read once and folded into both revisions below, so the two cannot be taken over two
        // readings of the settings or the view's live inputs.
        var viewRevision = computeViewRevision(view);

        var contentRevision = computeContentRevision(viewRevision, contentInputs);
        var isContentStale = isCellCutStale
            || hasNothingBuilt
            || contentRevision != lastContentRevision;

        return new StaleHalves(
            cellCut,
            isCellCutStale,
            contentInputs,
            contentRevision,
            computeHoldingRevision(viewRevision, contentInputs),
            isContentStale);
    }

    /**
     * The two readings a recut moved between, for the line a caller writes about it.
     *
     * <p>Described here because this is the only holder of both: the standing reading is a baseline
     * and never leaves, so a caller that wanted to name the transition itself would have to be
     * handed the very value the decision exists to keep to itself.
     *
     * <p>Read before the cut is recorded, which is the order a rebuild takes anyway: recording
     * advances the standing reading to the one this would otherwise still be comparing against.
     *
     * @param staleHalves the decision the recut is being taken under
     * @return the transition, naming the never-cut state where nothing has been cut yet
     */
    public String describeCellCutTransition(StaleHalves staleHalves) {
        return "from " + lastCellCut + " to " + staleHalves.cellCut();
    }

    /**
     * Records that the cells were recut under this frame's decision, so the next frame compares
     * against what they were actually cut from.
     *
     * @param staleHalves the decision the cut was taken under
     */
    public void recordCellsCut(StaleHalves staleHalves) {
        lastCellCut = staleHalves.cellCut();
    }

    /**
     * Records that the clusters were rebuilt under this frame's decision. Called only after the
     * rebuild completes, so a thrown rebuild is retried next frame rather than taken as done.
     *
     * @param staleHalves the decision the rebuild was taken under
     */
    public void recordContentRebuilt(StaleHalves staleHalves) {
        lastContentRevision = staleHalves.contentRevision();
    }

    /**
     * Records that the holding was read afresh under this frame's decision, so a later rebuild
     * that owes no new reading can tell it was resolved under the rule now in force.
     *
     * @param staleHalves the decision the holding was resolved under
     */
    public void recordHoldingResolved(StaleHalves staleHalves) {
        lastHoldingRevision = staleHalves.holdingRevision();
    }

    // The clusters-staleness token, folding everything the cells are styled under: the view
    // revision below, and the sidebar picks this frame sampled.
    //
    // The picks are folded as the values sampled rather than as the counters their flips raise,
    // because a counter reports that somebody clicked something while the values answer the only
    // question a rebuild turns on: would this build come out the same. Two readings holding the
    // same picks are one bake whatever has been clicked between them, and a screen switch moves
    // every pick while no counter moves at all.
    private static int computeContentRevision(int viewRevision, ContentInputs contentInputs) {
        return Objects.hash(viewRevision, contentInputs);
    }

    // The content revision's holding half: the same fold over only what reaches the resolve. Of the
    // sidebar picks that is the spotlight alone - the two recedes, the name format and the outline
    // reach the paint and nothing before it. The view revision stays in whole, since the rule a
    // view resolves holding by can read LunaLib fields, and a view that groups factions into blocs
    // moves its holding whenever those groups move. A style pick moves the content revision and
    // leaves this one where it was, which is what lets the rebuild it owes keep the holding.
    private static int computeHoldingRevision(int viewRevision, ContentInputs contentInputs) {
        return Objects.hash(viewRevision, contentInputs.selectedBlocId());
    }

    // The terms both revisions share, folded once: the settings revision, since a settings change
    // restyles every cell over the fixed geometry; the active view, since their grouping, styling
    // and labels differ; and the view's own fingerprint of whatever live inputs it samples, so a
    // change to one of those rebuilds without any setting moving.
    //
    // The view is asked for that fingerprint rather than this naming the signals behind it, which is
    // what keeps the shared pipeline free of any one view's vocabulary: a view that samples nothing
    // contributes a constant and is never churned by a change it does not render.
    private int computeViewRevision(OwnerPaintedView view) {
        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getId(),
            view.getContentRevision(machinery.resolveRefreshBoard()));
    }
}
