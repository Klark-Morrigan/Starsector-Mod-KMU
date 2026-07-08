package kmu.maplayers.politicalmap.base.render.model;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The built map state a single full rebuild produces and the incremental refresh then
 * edits in place: the two draw lists the renderer paints, plus the derivation inputs an
 * incremental re-shape needs to rebuild a handful of cells against the same ownership
 * and styles the full rebuild used.
 *
 * <p>The styled-cell and faction-territory maps are the render output - the per-cell
 * seam/outline records and each faction's fill and national border. The rest are
 * retained inputs: the owner-by-system and decivilised-system maps say who holds each
 * system, the four styles and the neutral color say how each category draws, and the
 * view plus its resolved grouping say how ownership is grouped and which blocs recede to
 * the independent style - so an incremental re-shape classifies a cell exactly as the
 * full build did.
 *
 * <p>A plain class rather than a record because three of its fields are mutable state,
 * not values: the styled-cell, faction-territory, and owner-by-system maps are mutated
 * in place by the incremental refresh, which replaces just the cells and factions an
 * ownership change touched. The styles, the decivilised set, the neutral color, the
 * view, and the grouping are set once at build and only read after, so an incremental
 * pass re-shapes against the exact inputs the full build baked in.
 */
public final class PoliticalMapDrawables {
    // Render output, mutated in place by the incremental refresh.
    private final Map<String, StyledCell> styledCellBySystemId;
    private final Map<String, FactionTerritory> factionTerritoryByFactionId;
    // Retained derivation inputs. The owner map is mutated in place as systems flip; the
    // rest are set once at build and only read after.
    private final Map<String, DominantOwner> ownerBySystemId;
    private final Set<String> decivilisedSystemIds;
    private final Color neutralColor;
    private final MapStyle factionStyle;
    private final MapStyle independentStyle;
    private final MapStyle decivilisedStyle;
    private final MapStyle uninhabitedStyle;
    // The view whose per-bloc style classifier an incremental re-shape reads, and the
    // grouping snapshot the full build resolved ownership under - held together so the
    // re-shape classifies a cell against the same view and the same once-sampled grouping.
    private final PoliticalMapView view;
    private final OwnershipGrouping grouping;

    public PoliticalMapDrawables(
            Map<String, StyledCell> styledCellBySystemId,
            Map<String, FactionTerritory> factionTerritoryByFactionId,
            Map<String, DominantOwner> ownerBySystemId,
            Set<String> decivilisedSystemIds,
            Color neutralColor,
            MapStyle factionStyle,
            MapStyle independentStyle,
            MapStyle decivilisedStyle,
            MapStyle uninhabitedStyle,
            PoliticalMapView view,
            OwnershipGrouping grouping) {
        this.styledCellBySystemId = styledCellBySystemId;
        this.factionTerritoryByFactionId = factionTerritoryByFactionId;
        this.ownerBySystemId = ownerBySystemId;
        this.decivilisedSystemIds = decivilisedSystemIds;
        this.neutralColor = neutralColor;
        this.factionStyle = factionStyle;
        this.independentStyle = independentStyle;
        this.decivilisedStyle = decivilisedStyle;
        this.uninhabitedStyle = uninhabitedStyle;
        this.view = view;
        this.grouping = grouping;
    }

    // An empty placeholder for the render path to fall back on after a failed first
    // build: the two draw lists are empty so the render is a harmless no-op, and the
    // next frame's retry replaces it with a real build before any incremental pass -
    // which needs the styles - can run, so the null styles here are never read. It carries
    // the active view (the one being drawn when the build failed) rather than naming a
    // concrete view, keeping this model view-agnostic; the identity grouping is an inert
    // default, never read for the same reason.
    public static PoliticalMapDrawables createEmpty(PoliticalMapView view) {
        return new PoliticalMapDrawables(new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashSet<>(), Color.GRAY,
                null, null, null, null, view, OwnershipGrouping.identity());
    }

    public Map<String, StyledCell> getStyledCellBySystemId() {
        return styledCellBySystemId;
    }

    public Map<String, FactionTerritory> getFactionTerritoryByFactionId() {
        return factionTerritoryByFactionId;
    }

    public Map<String, DominantOwner> getOwnerBySystemId() {
        return ownerBySystemId;
    }

    public Set<String> getDecivilisedSystemIds() {
        return decivilisedSystemIds;
    }

    public Color getNeutralColor() {
        return neutralColor;
    }

    public MapStyle getFactionStyle() {
        return factionStyle;
    }

    public MapStyle getIndependentStyle() {
        return independentStyle;
    }

    public MapStyle getDecivilisedStyle() {
        return decivilisedStyle;
    }

    public MapStyle getUninhabitedStyle() {
        return uninhabitedStyle;
    }

    public PoliticalMapView getView() {
        return view;
    }

    public OwnershipGrouping getGrouping() {
        return grouping;
    }

    // True when there is nothing to paint, so the renderer can skip the GL state push
    // entirely.
    public boolean isEmpty() {
        return styledCellBySystemId.isEmpty() && factionTerritoryByFactionId.isEmpty();
    }
}
