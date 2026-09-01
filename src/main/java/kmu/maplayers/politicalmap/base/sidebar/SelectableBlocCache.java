package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.RevisionMemo;

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
 * <p>The economy can drift between rebuild triggers (a colony resized without changing holder leaves
 * the settings and grouping revisions untouched), so a metric can lag until the next settings, view,
 * or alliance-set change forces a recompute - the same cadence the overlay's own full territory
 * rebuild reconciles on, so the picker numbers and the painted map stay in step.
 */
public final class SelectableBlocCache {

    // One memo for the whole tab, not one per view: the picker draws a single view at a time, so a
    // switch is a miss on the view id and the switched-in view's picker replaces the previous one.
    // Which is also the constraint on every entry below - one held entry, so callers that do not
    // agree on the view thrash it rather than share it.
    // Held wildcarded because each view's blocs carry that view's own metrics, which is knowledge
    // the memo has no use for - it caches whatever the view answered.
    private static final RevisionMemo<BlocPickerRead<?>> blocCache = new RevisionMemo<>();

    private SelectableBlocCache() {
    }

    /**
     * The selected view's picker under the player's live settings, rebuilt only when the sector, the
     * view, or the revision the list depends on has changed since the last call.
     *
     * @param view   the selected political-map view whose blocs the picker draws
     * @param sector the sector whose economy the list is read from; a null sector resolves to the
     *               view's empty list
     * @return the memoised read - the picker and the presence behind it; the same instance while
     *         nothing it depends on moves
     */
    public static BlocPickerRead<?> resolveBlocPickerRead(
            PoliticalMapView view,
            SectorAPI sector) {

        return blocCache.resolveValue(
            sector,
            view.getId(),
            computeRevision(view),
            () -> view.resolveBlocPickerRead(sector));
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
     * @param sector the sector whose economy the read is taken from; a null sector resolves to the
     *               view's empty read
     * @param blocId the bloc to look up; an id this view never surfaced answers empty
     * @return that bloc's system ids in walk order, never null
     */
    public static Set<String> readPresentSystemIds(
            PoliticalMapView view,
            SectorAPI sector,
            String blocId) {

        return resolveBlocPickerRead(view, sector).presenceIndex().readPresentSystemIds(blocId);
    }

    // The revision the memoised list is valid for: the economy-weighting settings (the dominance
    // rules and the visibility settings both move settingsRevision) and the view's own live grouping inputs
    // (the alliance set, for the alliances view; a constant for the faction view). The filter
    // selection is absent by design - it changes which bloc is lit, never which blocs are listed.
    private static int computeRevision(PoliticalMapView view) {
        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getContentRevision());
    }
}
