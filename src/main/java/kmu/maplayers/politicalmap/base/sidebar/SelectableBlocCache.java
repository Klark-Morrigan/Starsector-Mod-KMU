package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.settings.KmuLunaSettings;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

/**
 * Memoises the selected view's selectable-bloc list (each option's stats included) so the per-frame
 * sidebar draw reads a cache instead of re-walking the economy. The political-map tab resolves its
 * picker options every frame the map is open - twice a frame, for the render and the hit-test passes -
 * and each list is a full grouped dominance pass over the sector, so without a memo the picker would
 * rescan the whole economy several times a frame. The list changes only when the economy-weighting
 * settings or the view's live grouping change, so it is rebuilt only when that revision moves and the
 * per-frame path is otherwise an int compare.
 *
 * <p>Keyed on the sector identity as well, so a save reloaded in the same session - a fresh sector
 * whose settings and alliance counters happen to match the last - recomputes against the loaded
 * economy rather than serving the previous save's blocs. The held sector reference is weak, so a
 * cached sector never outlives its unload. The list is filter-independent - it lists every selectable
 * bloc, not which one is spotlighted - so the filter revision is deliberately absent from the key: a
 * pick or a clear moves the lit row without invalidating the list.
 *
 * <p>The economy can drift between rebuild triggers (a colony resized without changing holder leaves
 * the settings and grouping revisions untouched), so a metric can lag until the next settings, view,
 * or alliance-set change forces a recompute - the same cadence the overlay's own full territory
 * rebuild reconciles on, so the picker numbers and the painted map stay in step.
 */
public final class SelectableBlocCache {

    // The sector the memo was built against, held weakly so a cached sector never outlives its
    // unload. Starts empty so the first resolve after class load always recomputes.
    private static WeakReference<SectorAPI> cachedSector = new WeakReference<>(null);
    private static String cachedViewId;
    private static int cachedRevision;
    private static List<SelectableBloc> cachedBlocs = List.of();

    private SelectableBlocCache() {
    }

    /**
     * The selected view's selectable blocs under the player's live settings, rebuilt only when the
     * sector, the view, or the revision the list depends on has changed since the last call.
     *
     * @param view   the selected political-map view whose blocs the picker draws
     * @param sector the sector whose economy the list is read from; a null sector resolves to the
     *               view's empty list
     * @return the memoised selectable blocs; the same list instance while nothing it depends on moves
     */
    public static List<SelectableBloc> resolveSelectableBlocs(PoliticalMapView view,
            SectorAPI sector) {
        var revision = computeRevision(view);
        if (sector != cachedSector.get() || !view.getId().equals(cachedViewId)
                || revision != cachedRevision) {
            cachedSector = new WeakReference<>(sector);
            cachedViewId = view.getId();
            cachedRevision = revision;
            cachedBlocs = view.resolveSelectableBlocs(sector);
        }
        return cachedBlocs;
    }

    // The revision the memoised list is valid for: the economy-weighting settings (the dominance
    // rules and the dev reveal both move settingsRevision) and the view's own live grouping inputs
    // (the alliance set, for the alliances view; a constant for the faction view). The filter
    // selection is absent by design - it changes which bloc is lit, never which blocs are listed.
    private static int computeRevision(PoliticalMapView view) {
        return Objects.hash(KmuLunaSettings.getSettingsRevision(), view.getContentRevision());
    }
}
