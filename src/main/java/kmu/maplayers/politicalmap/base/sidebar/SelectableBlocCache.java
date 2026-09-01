package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.widgets.lists.RevisionMemo;

import kmu.maplayers.base.installation.InstalledMachinery;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.base.BlocPickerRead;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.settings.KmuLunaSettings;

import java.util.Objects;
import java.util.Set;

/**
 * What invalidates the political map's memoised picker. The memo itself is KMLib's
 * {@link RevisionMemo}; what this adds is the one thing the memo cannot know - which moving values a
 * view's bloc list actually depends on, so a stale list is rebuilt and a live one
 * is not. Each list is a full grouped dominance pass over the sector, and the picker resolves its
 * options twice a frame the map is open, so getting that judgement right is what keeps the sidebar
 * from rescanning the whole economy several times a frame.
 *
 * <p>The presence behind the rows rides the same memo, since it is the same walk's answer under the
 * same invalidation - a surface lighting a bloc's systems reads it here rather than re-walking, and
 * cannot end up holding presence from one reading beside rows from another.
 *
 * <p>One sector's: a list is a walk of that sector's economy keyed on that sector's revisions, so it
 * is held by that sector's {@link MapLayerInstallation} and discarded with it.
 */
public final class SelectableBlocCache implements InstalledMachinery {

    // The machinery this cache belongs to, and so the sector every list here is walked from. Taken
    // whole rather than as a bare sector so the sector a walk reads and the holder its answer is
    // memoised in cannot name two different ones.
    private final MapLayerInstallation installation;

    // One memo for the whole tab, not one per view: the picker draws a single view at a time, so a
    // switch is a miss on the view id and the switched-in view's picker replaces the previous one.
    // Which is also the constraint on every entry below - one held entry, so callers that do not
    // agree on the view thrash it rather than share it.
    // Held wildcarded because each view's blocs carry that view's own metrics, which is knowledge
    // the memo has no use for - it caches whatever the view answered.
    private final RevisionMemo<BlocPickerRead<?>> blocCache = new RevisionMemo<>();

    // Reached through resolveBlocCacheIn, so the only caches that exist are ones an installation
    // holds - and so go with the sector they were made for.
    SelectableBlocCache(MapLayerInstallation installation) {
        this.installation = installation;
    }

    /**
     * The memoised picker {@code installation}'s sidebar body reads, made on the first ask and
     * released with the installation holding it.
     *
     * <p>The one way to this sector's memo, so the body build drawing the rows and the pass reading
     * the presence behind them cannot end up on two different walks.
     *
     * @param installation the machinery installed on the sector whose picker is being drawn
     * @return that sector's memoised picker
     */
    public static SelectableBlocCache resolveBlocCacheIn(MapLayerInstallation installation) {
        return installation.resolveMachinery(
            SelectableBlocCache.class,
            () -> new SelectableBlocCache(installation));
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
     * living in a system under the dominance views, claiming it under the claims one.
     *
     * @param view   the selected political-map view the bloc was surfaced by
     * @param blocId the bloc to look up; an id this view never surfaced answers empty
     * @return that bloc's system ids in walk order, never null
     */
    public Set<String> readPresentSystemIds(PoliticalMapView view, String blocId) {
        return resolveBlocPickerRead(view).presenceIndex().readPresentSystemIds(blocId);
    }

    /**
     * The selected view's picker over this cache's sector under the player's live settings, rebuilt
     * only when the view or the revision the list depends on has changed since the last call.
     *
     * @param view the selected political-map view whose blocs the picker draws
     * @return the memoised read - the picker and the presence behind it; the same instance while
     *         nothing it depends on moves. A cache over no sector - the detached installation -
     *         resolves to the view's empty list
     */
    public BlocPickerRead<?> resolveBlocPickerRead(PoliticalMapView view) {

        var sector = installation.resolveSector();

        return blocCache.resolveValue(
            view.getId(),
            computeRevision(view, installation.resolveRefreshBoard()),
            () -> view.resolveBlocPickerRead(sector));
    }

    // The revision the memoised list is valid for: the economy-weighting settings (the dominance
    // rules and the visibility settings both move settingsRevision) and the view's own live grouping inputs
    // (the alliance set, for the alliances view; a constant for the faction view). The filter
    // selection is absent by design - it changes which bloc is lit, never which blocs are listed.
    //
    // The view folds its inputs off this installation's board, the same one the walk below reads its
    // sector from: keyed on another sector's counters, a list walked here would never be re-walked
    // when this sector's alliances moved, and would be thrown away when another sector's did.
    private static int computeRevision(PoliticalMapView view, MapLayerRefreshBoard board) {
        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getContentRevision(board));
    }
}
