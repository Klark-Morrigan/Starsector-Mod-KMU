package kmu.maplayers.ownermap.sidebar;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.widgets.lists.RevisionMemo;

import kmu.maplayers.base.machinery.InstalledMachinery;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.picker.BlocPickerRead;
import kmu.settings.KmuLunaSettings;

import java.util.Objects;
import java.util.Set;

/**
 * What invalidates an owner map's memoised picker. The memo itself is KMLib's
 * {@link RevisionMemo}; what this adds is the one thing the memo cannot know - which moving values a
 * view's bloc list actually depends on, so a stale list is rebuilt and a live one
 * is not. Each list is a full grouped holder pass over the sector, and the picker resolves its
 * options twice a frame the map is open, so getting that judgement right is what keeps the sidebar
 * from rescanning the whole economy several times a frame.
 *
 * <p>The presence behind the rows rides the same memo, since it is the same walk's answer under the
 * same invalidation - a surface lighting a bloc's systems reads it here rather than re-walking, and
 * cannot end up holding presence from one reading beside rows from another.
 *
 * <p>One sector's: a list is a walk of that sector's economy keyed on that sector's revisions, so it
 * is held by that sector's {@link SectorMapMachinery} and discarded with it. And one layer's: the
 * memo holds a single entry, so two layers sharing one would evict each other's picker on every
 * frame both are listed - each is held under its own layer instead.
 */
public final class SelectableBlocCache implements InstalledMachinery {

    // The machinery this cache belongs to, and so the sector every list here is walked from. Taken
    // whole rather than as a bare sector so the sector a walk reads and the holder its answer is
    // memoised in cannot name two different ones.
    private final SectorMapMachinery machinery;

    // One memo for the whole tab, not one per view: the picker draws a single view at a time, so a
    // switch is a miss on the view ID and the switched-in view's picker replaces the previous one.
    // Which is also the constraint on every entry below - one held entry, so callers that do not
    // agree on the view thrash it rather than share it.
    // Held wildcarded because each view's blocs carry that view's own metrics, which is knowledge
    // the memo has no use for - it caches whatever the view answered.
    private final RevisionMemo<BlocPickerRead<?>> blocCache = new RevisionMemo<>();

    // Reached through resolveBlocCacheIn, so the only caches that exist are ones machinery
    // holds - and so go with the sector they were made for.
    SelectableBlocCache(SectorMapMachinery machinery) {
        this.machinery = machinery;
    }

    /**
     * The memoised picker one layer's sidebar body reads on {@code machinery}'s sector, made on the
     * first ask and released with the machinery holding it.
     *
     * <p>The one way to that memo, so the body build drawing the rows and the pass reading the
     * presence behind them cannot end up on two different walks.
     *
     * @param machinery the machinery installed on the sector whose picker is being drawn
     * @param layerId   the layer whose picker it is, so two layers memoise apart
     * @return that layer's memoised picker on that sector
     */
    public static SelectableBlocCache resolveBlocCacheIn(SectorMapMachinery machinery, String layerId) {
        return machinery.resolveLayerMachinery(
            layerId,
            SelectableBlocCache.class,
            () -> new SelectableBlocCache(machinery));
    }

    /**
     * Drops the memoised read, so a caller still holding this cache is not served the gone sector's
     * rows - nothing about the key would say they were stale, since a sector going away moves no
     * revision.
     */
    @Override
    public void disposeMachinery() {
        blocCache.discardValue();
    }

    /**
     * The systems one bloc was found in under the selected view, off the same memoised read the
     * picker rows come from.
     *
     * <p>A per-bloc lookup rather than the whole index, so a surface asking about the one bloc under
     * the pointer never holds every bloc's set to get at it.
     *
     * <p>Asked under the <em>selected</em> view, the same one the sidebar body asks under: two
     * callers alternating under different views evict each other from the one memo entry below and
     * re-walk the economy every call. The view also decides what being found somewhere means -
     * living in a system under one view, claiming it under another.
     *
     * @param view   the selected view the bloc was surfaced by
     * @param blocId the bloc to look up; an ID this view never surfaced answers empty
     * @return that bloc's systems in walk order, never null
     */
    public Set<SystemKey> readPresentSystemKeys(OwnerPaintedView view, String blocId) {
        return resolveBlocPickerRead(view).presenceIndex().readPresentSystemKeys(blocId);
    }

    /**
     * The selected view's picker over this cache's sector under the player's live settings, rebuilt
     * only when the view or the revision the list depends on has changed since the last call.
     *
     * @param view the selected view whose blocs the picker draws
     * @return the memoised read - the picker and the presence behind it; the same instance while
     *         nothing it depends on moves. A cache over no sector - the detached machinery -
     *         resolves to the view's empty list
     */
    public BlocPickerRead<?> resolveBlocPickerRead(OwnerPaintedView view) {

        var sector = machinery.resolveSector();

        return blocCache.resolveValue(
            view.getId(),
            computeRevision(view),
            () -> view.resolveBlocPickerRead(sector));
    }

    // The revision the memoised list is valid for: the economy-weighting settings (the holding
    // rules and the visibility settings both move settingsRevision) and the view's own live inputs
    // (its live grouping source where it samples one; a constant for a view sampling nothing). The
    // filter selection is absent by design - it changes which bloc is lit, never which blocs are listed.
    //
    // The view folds its inputs off this machinery's board, the same one the walk above takes its
    // sector from: keyed on another sector's counters, a list walked here would never be re-walked
    // when this sector's groups moved, and would be thrown away when another sector's did.
    private int computeRevision(OwnerPaintedView view) {
        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getContentRevision(machinery.resolveRefreshBoard()));
    }
}
