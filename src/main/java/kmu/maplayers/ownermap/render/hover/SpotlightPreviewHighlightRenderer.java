package kmu.maplayers.ownermap.render.hover;

import kmu.maplayers.base.hover.HoverHighlightRenderer;
import kmu.maplayers.base.hover.PreviewHighlightGeometry;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.MapFrame;
import kmu.maplayers.base.sidebar.FilterHoverSlot;
import kmu.maplayers.base.sidebar.MapLayerStoreNamespace;
import kmu.maplayers.base.sidebar.PickerScope;
import kmu.maplayers.base.theme.HoverHighlightStyle;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.style.MapPalettes;
import kmu.maplayers.ownermap.render.style.SectorBlocPalettes;
import kmu.maplayers.ownermap.sidebar.SelectableBlocCache;

import java.awt.Color;

/**
 * Lights every system the bloc under the spotlight picker's pointer is present in, over the map that
 * is already painting - what picking that row would spotlight, shown without picking it.
 *
 * <p>The preview for any layer offering the tier's spotlight picker: which rows exist and what each
 * selects are the picker's, read back through the same memo and hover slot the picker writes, so a
 * layer answers the preview seam with this rather than with one of its own.
 *
 * <p>Nothing of the paint moves for it: no rebuild, no refilter, no re-clustering, no border
 * re-trace. The frame's own draw lists are the only geometry it traces, and the assembly over them
 * is memoised, so a pointer resting on one row costs nothing after the first frame.
 *
 * <p>The shade is keyed on the bloc rather than on any cell of it, which is what separates this
 * from the cursor's highlight beside it. A previewed bloc lights cells that rivals hold and cells a
 * spotlight has sunk to grey, and both would answer in someone else's colour - or in none - if the
 * shade were read off what the cell was painted as. It comes through
 * {@link SectorBlocPalettes}, the shared reader of what colour a bloc is, so no two surfaces
 * painting for a bloc can come to disagree - including on the bloc whose colour faction has gone
 * from the sector, which none of them paints.
 *
 * <p>It cannot collide with the cursor's highlight: the sidebar parks the map hover while the
 * pointer is over it, so the cursor's is already dark on every frame this one could draw.
 */
public final class SpotlightPreviewHighlightRenderer implements OwnerMapPreviewHighlight {

    // The sector whose picker is being previewed - where the hovered bloc is read from, where the
    // systems behind it are read from, and whose factions the shade is read from. Taken whole
    // rather than as the three reads separately, so they cannot end up naming different sectors.
    private final SectorMapMachinery machinery;

    // The layer whose picker is previewed, which is the key its memo is held under.
    private final String layerId;

    // The namespace the layer's picker stores its picks under, which is also the one it reports a
    // hover under: a view named the same by another mod's picker is a different list.
    private final MapLayerStoreNamespace storeNamespace;

    // Held rather than resolved per frame: the assembly retains what it built for the previewed
    // set, so a pointer resting on one row re-clips and re-tessellates nothing.
    private final PreviewHighlightGeometry geometry = new PreviewHighlightGeometry();

    /**
     * @param machinery the machinery installed on the sector whose map this draws over, holding
     *                  both the picker's hover slot and the presence behind its rows
     * @param layerId        the layer whose picker is previewed, so the presence is read off that
     *                       layer's own memo rather than another layer's
     * @param storeNamespace the namespace the layer's picker reports its hover under
     */
    public SpotlightPreviewHighlightRenderer(
            SectorMapMachinery machinery,
            String layerId,
            MapLayerStoreNamespace storeNamespace) {

        this.machinery = machinery;
        this.layerId = layerId;
        this.storeNamespace = storeNamespace;
    }

    /**
     * Paints the previewed bloc's systems for one map frame, or nothing when no row is under the
     * pointer.
     *
     * @param clusters the draw lists the frame painted, which is the only geometry the preview
     *                    traces and the theme it draws to
     * @param mapFrame    the scale every coordinate is multiplied by, and the map's own fade
     *                    applied on top of every element's opacity
     */
    @Override
    public void renderPreviewOnMap(
            OwnerMapClusters clusters,
            MapFrame mapFrame) {

        var paint = resolvePreviewPaint(clusters);
        if (!paint.isPainting()) {
            return;
        }
        HoverHighlightRenderer.renderHighlightOnMap(
            paint.highlight(),
            paint.colour(),
            readPreviewTierOf(clusters),
            mapFrame);
    }

    // What this frame's preview lights up and in what shade, or nothing.
    //
    // The three tests run cheapest first, and the order matters: the hovered row is a map lookup
    // and answers "nothing" on almost every frame the map is open, so a frame previewing nothing
    // reads neither the theme nor the sector's factions and never reaches the assembly at all.
    //
    // Package-private so the decision is answerable without a live map, the emission below it
    // being pure GL.
    PreviewHighlightPaint resolvePreviewPaint(OwnerMapClusters clusters) {

        // The view the frame painted, off the inputs the build was baked under.
        var view = clusters.getBuildInputs().viewGrouping().view();

        // The view's ID under the layer's own store namespace, which is how the picker reported the
        // hover: the ID is opaque, so a view named the same by another mod's picker is a different
        // list and must not preview here.
        var previewedBlocId = FilterHoverSlot
            .resolveHoverSlotIn(machinery)
            .getHoveredIdOf(new PickerScope(storeNamespace, view.getId()));

        if (previewedBlocId == null) {
            return PreviewHighlightPaint.NONE;
        }
        // Resolved before the geometry for the reason the cursor's entry resolves its own colour
        // first: a shade the player has switched off ("No color") stands every frame the map is
        // open, and it is what says whether the assembly is worth running at all.
        var colour = resolveShadeOf(clusters, previewedBlocId);

        if (colour == null) {
            return PreviewHighlightPaint.NONE;
        }
        // Read under the view the frame painted, which is also the view the sidebar's rows were
        // listed under: the presence rides the same memo those rows came from, so asking under any
        // other view would both evict that memo and answer about a list the pointer is not on.
        var presentSystemKeys = SelectableBlocCache
            .resolveBlocCacheIn(machinery, layerId)
            .readPresentSystemKeys(view, previewedBlocId);

        return new PreviewHighlightPaint(
            geometry.resolveHighlightFor(
                new OwnerMapHoverHighlightSource(clusters),
                previewedBlocId,
                presentSystemKeys),
            colour);
    }

    // The tier a previewed set is lit to. One reader for both the slot the shade is picked from
    // and the weights the pass burns, so the decision and the emission cannot come off two
    // different readings of the theme.
    private static HoverHighlightStyle readPreviewTierOf(OwnerMapClusters clusters) {
        return clusters.getGlobalStyle().previewHighlight();
    }

    // The one shade the whole previewed set burns in, or null when it has none to burn - the
    // player having pointed the tier at no shade, or the bloc's colour faction having gone from
    // the sector. The second is the band rule's own answer, inherited rather than restated: a bloc
    // the map cannot name is one neither surface paints.
    private Color resolveShadeOf(OwnerMapClusters clusters, String blocId) {

        var palette = new SectorBlocPalettes(
                machinery.resolveSector(),
                clusters.getBuildInputs().viewGrouping().grouping())
            .readBlocPalette(blocId);

        return palette == null
            ? null
            : MapPalettes.pickPaletteColour(readPreviewTierOf(clusters).colour(), palette);
    }
}
