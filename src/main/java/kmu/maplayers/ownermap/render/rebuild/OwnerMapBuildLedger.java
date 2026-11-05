package kmu.maplayers.ownermap.render.rebuild;

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

import java.util.Set;

/**
 * What an owner map's standing build was made from - what its cells were cut from, what its
 * clusters were built from and what its owners were resolved under - and each frame's comparison
 * against it: whether the cells have to be recut, whether the clusters have to be rebuilt, and
 * whether the owners the last rebuild read can be painted over again rather than read afresh.
 *
 * <p>Kept apart from the cache that acts on the answer, because the two run at different cadences:
 * nearly every frame the map is open asks this and stops, while a rebuild is the rare frame. The
 * answer comes off revisions, the sidebar picks, settings read again only when LunaLib reports
 * a change, and the shades the standing build painted in, asked of its owner reading a few getters
 * per owner. So it opens no reading of the sector.
 *
 * <p>Its entries advance only when {@link FullOwnerMapRebuild} reports a stage complete, or the
 * cache a fold, so a rebuild that threw part way is asked again next frame rather than being taken
 * as done.
 *
 * <p>The board every revision is read off is the machinery's own, so a signal raised in another
 * sector cannot restyle these cells - and a signal raised in this one cannot fail to.
 */
final class OwnerMapBuildLedger {

    // The settings revision's seed, below any the settings can report, so the first frame reads
    // what the cells are cut from.
    private static final int UNREAD_SETTINGS_REVISION = -1;

    // The machinery installed on the sector the map draws, whose board every revision here is
    // read off.
    private final SectorMapMachinery machinery;

    // The drawing layer's body preferences, stored under that layer's own keys - which every rebuild
    // samples the picks a bake is under from.
    private final OwnerMapBodyPreferences bodyPreferences;

    // What the clusters were built from. Compared whole rather than folded into a hash, so two
    // readings that differ always rebuild, however their values happen to fold. Null is the
    // never-built state, which no reading equals, so the first frame builds.
    private ContentKey lastContentKey;

    // What the standing owners were resolved under: the content key's owners half, the inputs that
    // reach the resolve and none of the picks that only reach the paint. A rebuild that owes no new
    // reading of the sector - a style pick moved and nothing else - paints over what the last one
    // read rather than walking the sector for the same answer. Null until owners are first resolved.
    private OwnersKey lastOwnersKey;

    // Whether a frame left the standing map behind the sector: it drained marked systems it did not
    // fold, or it threw. Nothing else remembers the drained systems, and no revision moves for them,
    // so this is what makes the next frame rebuild rather than paint the old owner until something
    // unrelated moves.
    private boolean isRebuildOwed;

    // The shades the standing build painted in (see PaintedPalettes), which no key above follows.
    // Null while no production view stands, so nothing is watched: before the first build, and under
    // the debug overlay, a diagnostic view that takes a recolour at its next rebuild.
    private PaintedPalettes paintedPalettes;

    // What the cells currently held were cut from (see CellCutInputs for why that is one value).
    // Null (not a zero reading) is the never-cut state, since every reading the record can hold is
    // one the player can actually be under - and it is what makes a cache's first frame cut,
    // whatever the signal happens to say.
    private CellCutInputs lastCellCut;

    // The last sampling of what the cells are cut from, and the settings revision it was read
    // under. Its seed inputs and visibility rules are all KMU LunaLib fields, and every change to
    // one moves that revision, so while it holds they are reused rather than read again - a dozen
    // field reads and a gate resolve saved on every frame the map is open.
    private CellCutInputs sampledCellCut;
    private int sampledSettingsRevision = UNREAD_SETTINGS_REVISION;

    OwnerMapBuildLedger(SectorMapMachinery machinery, OwnerMapBodyPreferences bodyPreferences) {

        this.machinery = machinery;
        this.bodyPreferences = bodyPreferences;
    }

    /**
     * Whether the owners the last rebuild read still say who owns what.
     *
     * <p>They do unless the sector moved under them - a marked system, or a recut, which admits or
     * drops systems - or the rule reading them did, which the owners key holds. Never while
     * nothing is standing to keep, and never once a shade they were painted in has moved: each owner
     * carries the shades it was resolved in, so painting over it would redraw the old colours.
     *
     * @param staleHalves       this frame's decision
     * @param hasStandingOwners whether owners from an earlier rebuild are held at all
     * @param staleSystemKeys   the systems marked stale since the last drain, each a system the
     *                          sector moved under
     * @return whether the standing owners may be painted over rather than read afresh
     */
    boolean canReuseStandingOwners(
            StaleHalves staleHalves,
            boolean hasStandingOwners,
            Set<SystemKey> staleSystemKeys) {

        return hasStandingOwners
            && staleSystemKeys.isEmpty()
            && !staleHalves.staleness().isCellCutStale()
            && staleHalves.ownersKey().equals(lastOwnersKey)
            && !hasAnyPaintedPaletteMoved();
    }

    /**
     * What this frame owes, decided off revisions, settings and the shades the standing build
     * painted in, so it costs next to nothing on a frame that owes nothing - which is nearly all of
     * them.
     *
     * @param view            the view being painted, whose live inputs and spotlight pick are
     *                        folded
     * @param memoryScope     the screen being painted for, whose panel holds every sidebar
     *                        preference the picks are sampled under
     * @param hasNothingBuilt whether neither base view has been built yet, which forces a build
     *                        whatever the keys say
     * @return the decision, carrying every sampled input the rebuild it may owe has to build under
     */
    StaleHalves decideWhatIsStale(
            OwnerPaintedView view,
            ScreenMemoryScope memoryScope,
            boolean hasNothingBuilt) {

        var cellCut = sampleCellCutInputs();

        // The rebuild's one sampling of the sidebar preferences, taken here beside the cut's for
        // the same reason: every stage that bakes one reads this reading, and the staleness
        // question below is asked of the values rather than of the counters their flips raise.
        //
        // Sampled under the screen the frame handed over, which is the screen it also took the view
        // off, so the picks a rebuild bakes and the view it builds under name one panel.
        var contentInputs = ContentInputs.sampleForView(view, bodyPreferences, memoryScope);

        // Read once and shared by both keys below, so the two cannot be taken over two readings of
        // the settings or the view's live inputs.
        var viewKey = readViewKey(view);
        var contentKey = new ContentKey(viewKey, contentInputs);

        return new StaleHalves(
            cellCut,
            contentKey,
            new OwnersKey(viewKey, contentInputs.selectedOwnerId()),
            decideStaleness(cellCut, hasNothingBuilt, contentKey));
    }

    /**
     * The two readings a recut moved between, for the line a caller writes about it.
     *
     * <p>Described here because this is the only class keeping both: the standing reading is a baseline
     * and never leaves, so a caller that wanted to name the transition itself would have to be
     * handed the very value the decision exists to keep to itself.
     *
     * <p>Read before the cut is recorded, which is the order a rebuild takes anyway: recording
     * advances the standing reading to the one this would otherwise still be comparing against.
     *
     * @param staleHalves the decision the recut is being taken under
     * @return the transition, naming the never-cut state where nothing has been cut yet
     */
    String describeCellCutTransition(StaleHalves staleHalves) {
        return "from " + lastCellCut + " to " + staleHalves.cellCut();
    }

    /**
     * The shade found moved since the standing build painted in it, for the line a rebuild writes.
     *
     * <p>Read before the rebuild records the shades it paints in, for the reason
     * {@link #describeCellCutTransition} is read before the cut is recorded.
     *
     * @return what moved and its two values, or {@link PaintedPalettes#NO_MOVED_PALETTE} where nothing
     *         has or nothing is watched
     */
    String describeMovedPalette() {

        return paintedPalettes == null
            ? PaintedPalettes.NO_MOVED_PALETTE
            : paintedPalettes.describeMovedPalette();
    }

    /**
     * Records that the cells were recut under this frame's decision, so the next frame compares
     * against what they were actually cut from.
     *
     * @param staleHalves the decision the cut was taken under
     */
    void recordCellsCut(StaleHalves staleHalves) {
        lastCellCut = staleHalves.cellCut();
    }

    /**
     * Records that the clusters were rebuilt under this frame's decision. Called only after the
     * rebuild completes, so a thrown rebuild is retried next frame rather than taken as done.
     *
     * @param staleHalves the decision the rebuild was taken under
     */
    void recordContentRebuilt(StaleHalves staleHalves) {

        lastContentKey = staleHalves.contentKey();
        isRebuildOwed = false;
    }

    /**
     * Records that the next frame owes a full rebuild that reads the sector for itself: the systems
     * marked since the last drain were dropped rather than folded, or the frame threw part way.
     */
    void recordRebuildOwed() {
        isRebuildOwed = true;
    }

    /**
     * Records that the owners were read afresh under this frame's decision, so a later rebuild
     * that owes no new reading can tell they were resolved under the rule now in force.
     *
     * @param staleHalves the decision the owners were resolved under
     */
    void recordOwnersResolved(StaleHalves staleHalves) {
        lastOwnersKey = staleHalves.ownersKey();
    }

    /**
     * Records the shades the standing view was painted in, so each frame after asks whether the game
     * still answers them. Taken after every rebuild, and after every fold, which may paint an owner
     * the build did not.
     *
     * @param paintedPalettes the shades, or null where no production view stands, which leaves
     *                        nothing watched
     */
    void recordPalettesPainted(PaintedPalettes paintedPalettes) {
        this.paintedPalettes = paintedPalettes;
    }

    // The cells recut when what they would be cut from differs from what they were, and the
    // clusters rebuild over any recut, since every cell they group was reseeded. Short of a recut,
    // the clusters rebuild before the first build, whatever the keys say; on any change to what
    // they were built from; after a frame dropped marked systems it could not fold; and when a shade
    // they were painted in moved, which seeds no cell.
    //
    // Decided before the cache folds a marked batch, so a frame that sees a moved shade rebuilds the
    // whole map and drains the marks with it, rather than a batch repainting one cell in the new
    // shade beside the same owner's other cells still in the old one. The shades are asked last, being the one question
    // here that costs more than a compare.
    private Staleness decideStaleness(
            CellCutInputs cellCut,
            boolean hasNothingBuilt,
            ContentKey contentKey) {

        if (!cellCut.equals(lastCellCut)) {
            return Staleness.CUT_AND_CONTENT;
        }

        if (hasNothingBuilt
                || isRebuildOwed
                || !contentKey.equals(lastContentKey)
                || hasAnyPaintedPaletteMoved()) {

            return Staleness.CONTENT;
        }

        return Staleness.NOTHING;
    }

    // Whether the game now answers a shade other than one the standing build painted in.
    private boolean hasAnyPaintedPaletteMoved() {

        return paintedPalettes != null
            && paintedPalettes.hasAnyPaletteMoved();
    }

    // The terms both keys share, read off the board of the machinery this ledger belongs to.
    private ViewKey readViewKey(OwnerPaintedView view) {

        return new ViewKey(
            KmuLunaSettings.getSettingsRevision(),
            view.getId(),
            view.readContentRevisions(machinery.resolveRefreshBoard()));
    }

    // What the cells would be cut from right now (see CellCutInputs).
    //
    // This is also the rebuild's one sampling of the visibility rules: the cut, the fills and the
    // bands all resolve under what it holds, so a gate flipped mid-rebuild cannot leave cells cut
    // under one rule and painted under another.
    private CellCutInputs sampleCellCutInputs() {

        var geometryRevision = machinery.resolveRefreshBoard().getRevision(MapLayerCommonRefreshSignal.GEOMETRY);
        var settingsRevision = KmuLunaSettings.getSettingsRevision();

        if (settingsRevision != sampledSettingsRevision) {

            sampledSettingsRevision = settingsRevision;
            sampledCellCut = new CellCutInputs(
                geometryRevision,
                new CellSeedInputs(
                    KmuOwnerMapGeometrySettings.getOwnerMapCellBoundSegments(),
                    KmuOwnerMapGeometrySettings.getOwnerMapCellRadius()),
                MapVisibilityRules.readFromLunaSettings());

        } else if (geometryRevision != sampledCellCut.geometryRevision()) {

            sampledCellCut = new CellCutInputs(
                geometryRevision,
                sampledCellCut.seedInputs(),
                sampledCellCut.visibilityRules());
        }

        return sampledCellCut;
    }
}
