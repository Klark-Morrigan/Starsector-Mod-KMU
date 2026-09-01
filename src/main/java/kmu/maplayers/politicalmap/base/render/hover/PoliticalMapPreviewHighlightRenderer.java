package kmu.maplayers.politicalmap.base.render.hover;

import kmu.maplayers.base.hover.HoverHighlightRenderer;
import kmu.maplayers.base.hover.PreviewHighlightGeometry;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.sidebar.FilterHoverSlot;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.sidebar.SelectableBlocCache;

/**
 * Lights every system the bloc under the sidebar picker's pointer is present in, over the map that
 * is already painting - what picking that row would spotlight, shown without picking it.
 *
 * <p>Nothing of the paint moves for it: no rebuild, no refilter, no re-clustering, no border
 * re-trace. The frame's own draw lists are the only geometry it traces, and the assembly over them
 * is memoised, so a pointer resting on one row costs nothing after the first frame.
 *
 * <p>The shade is keyed on the bloc rather than on any cell of it, which is what separates this
 * from the cursor's highlight beside it. A previewed bloc lights cells that rivals hold and cells a
 * spotlight has sunk to grey, and both would answer in someone else's colour - or in none - if the
 * shade were read off what the cell was painted as.
 *
 * <p>It cannot collide with the cursor's highlight: the sidebar parks the map hover while the
 * pointer is over it, so the cursor's is already dark on every frame this one could draw.
 */
public final class PoliticalMapPreviewHighlightRenderer {

    // The sector whose picker is being previewed - where the hovered bloc is read from, where the
    // systems behind it are read from, and whose factions the shade is read from. Taken whole
    // rather than as the three reads separately, so they cannot end up naming different sectors.
    private final MapLayerInstallation installation;

    // Held rather than resolved per frame: the assembly retains what it built for the previewed
    // set, so a pointer resting on one row re-clips and re-tessellates nothing.
    private final PreviewHighlightGeometry geometry = new PreviewHighlightGeometry();

    /**
     * @param installation the machinery installed on the sector whose map this draws over, holding
     *                     both the picker's hover slot and the presence behind its rows
     */
    public PoliticalMapPreviewHighlightRenderer(MapLayerInstallation installation) {
        this.installation = installation;
    }

    /**
     * Paints the previewed bloc's systems for one map frame, or nothing when no row is under the
     * pointer.
     *
     * @param territories the draw lists the frame painted, which is the only geometry the preview
     *                    traces and the theme it draws to
     * @param factor      the per-vertex scale the map applies to world coordinates
     * @param alphaMult   the map's own fade, applied on top of every element's opacity
     */
    public void renderPreviewOnMap(
            PoliticalMapTerritories territories,
            float factor,
            float alphaMult) {

        var paint = resolvePreviewPaint(territories);
        if (!paint.isPainting()) {
            return;
        }
        HoverHighlightRenderer.renderHighlightOnMap(
            paint.highlight(),
            paint.colour(),
            territories.getGlobalStyle().previewHighlight(),
            factor,
            alphaMult);
    }

    // What this frame's preview lights up and in what shade, or nothing.
    //
    // The three tests run cheapest first, and the order matters: the hovered row is a map lookup
    // and answers "nothing" on almost every frame the map is open, so a frame previewing nothing
    // reads neither the theme nor the sector's factions and never reaches the assembly at all.
    //
    // Package-private so the decision is answerable without a live map, the emission below it
    // being pure GL.
    PreviewHighlightPaint resolvePreviewPaint(PoliticalMapTerritories territories) {

        var view = territories.getView();
        var previewedBlocId = FilterHoverSlot
            .resolveHoverSlotIn(installation)
            .getHoveredIdOf(view.getId());

        if (previewedBlocId == null) {
            return PreviewHighlightPaint.NONE;
        }
        // Resolved before the geometry for the reason the cursor's entry resolves its own colour
        // first: a shade the player has switched off ("No color") stands every frame the map is
        // open, and it is what says whether the assembly is worth running at all.
        var colour = MapPalettes.pickBlocPaletteColour(
            territories.getGlobalStyle().previewHighlight().colour(),
            installation.resolveSector(),
            territories.getGrouping(),
            previewedBlocId);

        if (colour == null) {
            return PreviewHighlightPaint.NONE;
        }
        // Read under the view the frame painted, which is also the view the sidebar's rows were
        // listed under: the presence rides the same memo those rows came from, so asking under any
        // other view would both evict that memo and answer about a list the pointer is not on.
        var presentSystemIds = SelectableBlocCache
            .resolveBlocCacheIn(installation)
            .readPresentSystemIds(view, previewedBlocId);

        return new PreviewHighlightPaint(
            geometry.resolveHighlightFor(
                new PoliticalMapHoverHighlightSource(territories),
                previewedBlocId,
                presentSystemIds),
            colour);
    }
}
