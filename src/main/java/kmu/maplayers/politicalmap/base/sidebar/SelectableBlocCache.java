package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.RevisionMemo;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.settings.KmuLunaSettings;

import java.util.List;
import java.util.Objects;

/**
 * What invalidates the political map's memoised picker list. The memo itself is KMLib's
 * {@link RevisionMemo}; what this adds is the one thing the memo cannot know - which moving values a
 * selectable-bloc list actually depends on, so a stale list is rebuilt and a live one
 * is not. Each list is a full grouped dominance pass over the sector, and the picker resolves its
 * options twice a frame the map is open, so getting that judgement right is what keeps the sidebar
 * from rescanning the whole economy several times a frame.
 *
 * <p>The economy can drift between rebuild triggers (a colony resized without changing holder leaves
 * the settings and grouping revisions untouched), so a metric can lag until the next settings, view,
 * or alliance-set change forces a recompute - the same cadence the overlay's own full territory
 * rebuild reconciles on, so the picker numbers and the painted map stay in step.
 */
public final class SelectableBlocCache {

    // One memo for the whole tab, not one per view: the picker draws a single view at a time, so a
    // switch is a miss on the view id and the switched-in view's list replaces the previous one.
    private static final RevisionMemo<List<SelectableBloc>> blocCache = new RevisionMemo<>();

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
    public static List<SelectableBloc> resolveSelectableBlocs(
            PoliticalMapView view,
            SectorAPI sector) {

        return blocCache.resolveValue(
            sector,
            view.getId(),
            computeRevision(view),
            () -> view.resolveSelectableBlocs(sector));
    }

    // The revision the memoised list is valid for: the economy-weighting settings (the dominance
    // rules and the dev reveal both move settingsRevision) and the view's own live grouping inputs
    // (the alliance set, for the alliances view; a constant for the faction view). The filter
    // selection is absent by design - it changes which bloc is lit, never which blocs are listed.
    private static int computeRevision(PoliticalMapView view) {
        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getContentRevision());
    }
}
