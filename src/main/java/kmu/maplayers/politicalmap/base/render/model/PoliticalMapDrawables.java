package kmu.maplayers.politicalmap.base.render.model;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
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
 * system, the four styles and the neutral color say how each category draws, the
 * desaturation palette says what a desaturated bloc recolours to, and the view plus its
 * resolved grouping say how ownership is grouped and which blocs recede to the
 * independent style - so an incremental re-shape classifies a cell exactly as the full
 * build did.
 *
 * <p>A plain class rather than a record because three of its fields are mutable state,
 * not values: the styled-cell, faction-territory, and owner-by-system maps are mutated
 * in place by the incremental refresh, which replaces just the cells and factions an
 * ownership change touched. The styles, the decivilised set, the neutral color, the
 * view, and the grouping are set once at build and only read after, so an incremental
 * pass re-shapes against the exact inputs the full build baked in.
 *
 * <p>The filter snapshot rides along the same way: {@code selectedBlocId} is the spotlighted
 * bloc's id (null off filter, and {@link #isFiltering} derives from it), and
 * {@code recedeAdjustment} is the styling every non-spotlighted bloc takes this pass (resolved
 * once from the shared recede toggles). A re-shape and the label rebuild read them back so a
 * filtered cell recedes and a spotlight cluster names itself exactly as the full build did; off
 * filter they are the inert defaults (null, {@link BlocStyleAdjustment#NONE}).
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
    private final FactionPalette desaturationPalette;
    private final MapStyle factionStyle;
    private final MapStyle independentStyle;
    private final MapStyle decivilisedStyle;
    private final MapStyle uninhabitedStyle;
    // The view whose per-bloc style classifier an incremental re-shape reads, and the
    // grouping snapshot the full build resolved ownership under - held together so the
    // re-shape classifies a cell against the same view and the same once-sampled grouping.
    private final PoliticalMapView view;
    private final OwnershipGrouping grouping;
    // The filter snapshot: the spotlighted bloc's id (null off filter, so isFiltering() derives
    // from it), and the styling every non-spotlighted bloc recedes to (resolved once from the
    // shared recede toggles, inert off filter). Held so an incremental re-shape and the label
    // rebuild recede and name exactly as the full build did.
    private final String selectedBlocId;
    private final BlocStyleAdjustment recedeAdjustment;

    public PoliticalMapDrawables(
            Map<String, StyledCell> styledCellBySystemId,
            Map<String, FactionTerritory> factionTerritoryByFactionId,
            Map<String, DominantOwner> ownerBySystemId,
            Set<String> decivilisedSystemIds,
            Color neutralColor,
            FactionPalette desaturationPalette,
            MapStyle factionStyle,
            MapStyle independentStyle,
            MapStyle decivilisedStyle,
            MapStyle uninhabitedStyle,
            PoliticalMapView view,
            OwnershipGrouping grouping,
            String selectedBlocId,
            BlocStyleAdjustment recedeAdjustment) {
        this.styledCellBySystemId = styledCellBySystemId;
        this.factionTerritoryByFactionId = factionTerritoryByFactionId;
        this.ownerBySystemId = ownerBySystemId;
        this.decivilisedSystemIds = decivilisedSystemIds;
        this.neutralColor = neutralColor;
        this.desaturationPalette = desaturationPalette;
        this.factionStyle = factionStyle;
        this.independentStyle = independentStyle;
        this.decivilisedStyle = decivilisedStyle;
        this.uninhabitedStyle = uninhabitedStyle;
        this.view = view;
        this.grouping = grouping;
        this.selectedBlocId = selectedBlocId;
        this.recedeAdjustment = recedeAdjustment;
    }

    // An empty placeholder for the render path to fall back on after a failed first
    // build: the two draw lists are empty so the render is a harmless no-op, and the
    // next frame's retry replaces it with a real build before any incremental pass -
    // which needs the styles - can run, so the null styles here are never read. It carries
    // the active view (the one being drawn when the build failed) rather than naming a
    // concrete view, keeping this model view-agnostic; the identity grouping and the
    // gray-paired desaturation palette are inert defaults, never read for the same reason.
    public static PoliticalMapDrawables createEmpty(PoliticalMapView view) {
        return new PoliticalMapDrawables(new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashSet<>(), Color.GRAY,
                new FactionPalette(Color.GRAY, Color.GRAY),
                null, null, null, null, view, OwnershipGrouping.identity(),
                null, BlocStyleAdjustment.NONE);
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

    public FactionPalette getDesaturationPalette() {
        return desaturationPalette;
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

    // Whether this build spotlights a bloc - it does exactly when a bloc id was selected, so the
    // shared cell and faction builders bypass the view's per-bloc styling seams for the filter's.
    public boolean isFiltering() {
        return selectedBlocId != null;
    }

    // The spotlighted bloc's id this build recedes the rest of the sector around, or null off
    // filter; the label rebuild resolves the filter's synthetic spotlight keys back to its name.
    public String getSelectedBlocId() {
        return selectedBlocId;
    }

    // The styling every non-spotlighted bloc recedes to this pass; BlocStyleAdjustment.NONE off
    // filter, so a bloc no filter recedes draws untouched.
    public BlocStyleAdjustment getRecedeAdjustment() {
        return recedeAdjustment;
    }

    // True when there is nothing to paint, so the renderer can skip the GL state push
    // entirely.
    public boolean isEmpty() {
        return styledCellBySystemId.isEmpty() && factionTerritoryByFactionId.isEmpty();
    }
}
