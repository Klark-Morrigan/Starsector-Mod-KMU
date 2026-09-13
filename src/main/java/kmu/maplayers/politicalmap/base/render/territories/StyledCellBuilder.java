package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.geometry.ShapedCell;
import kmu.maplayers.base.render.clusters.BorderSmoothing;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledCluster;
import kmu.maplayers.base.render.clusters.VertexRuns;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.style.FactionlessStyleResolver;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;

import java.util.List;

/**
 * Bakes one shaped cell into the {@link StyledCell} the renderer paints, in whichever of the
 * two forms the cell takes.
 *
 * <p>An <em>owned</em> cell contributes only its interior seams: its fill and national border
 * are per cluster, drawn from the tessellated cluster in {@link StyledCluster}, because a
 * bloc's cells fuse into one frontier. Keeping the seams is what lets a footprint read as its
 * constituent cells rather than one smooth blob - the spotlighted bloc included, where the
 * solid-to-hatched transition is such a border like any other.
 *
 * <p>A <em>factionless</em> cell (inhabited but unheld, or uninhabited) does not fuse, so it has
 * no cluster to inherit from and keeps its own fill and outline. It names no faction palette
 * either, so both its palette slots hold the shared neutral colour - until the pass recedes it,
 * which a settled factionless cell takes as readily as a bloc does.
 *
 * <p>Shared by the full rebuild and the incremental re-shape, so both classify and style a
 * cell identically.
 */
public final class StyledCellBuilder {

    // Builds only; never instantiated.
    private StyledCellBuilder() {
    }

    /**
     * Builds one cell's draw record, or null when the cell draws nothing at all: an
     * inset-collapsed cell, or a factionless cell with neither its fill nor its outline drawn.
     *
     * <p>Takes the system the cell draws as rather than the cell itself, since everything read
     * here - the holder, the palette, whether the cell is decivilised - is known per system
     * and not per cell.
     *
     * @param territories this pass's retained holding, theme, and filter state
     * @param systemKey   the system the cell draws as, or null for a cell with no star of its
     *                    own, which draws as plain uninhabited - it has no holder to
     *                    colour it and nothing standing in it
     * @param shaped      the cell's inset shape
     * @return the cell's draw record, or null when it puts no ink on the map
     */
    public static StyledCell buildStyledCellForSystem(
            PoliticalMapTerritories territories,
            SystemKey systemKey,
            ShapedCell shaped) {

        // A cell the border inset consumed or collapsed comes back with an empty fill
        // (CellShaper via PolygonOffsets.insetSelectedEdges guarantees empty-or-drawable), so
        // there is nothing to fill or stroke.
        if (shaped.fillPolygon().isEmpty()) {
            return null;
        }
        // A cell with no star of its own has no holder to look up, so the absent system skips the
        // holder map rather than probing it for a key it does not hold - keeping the null-star path
        // clear of whether the holder map happens to tolerate a null-key get.
        var holder = systemKey == null
            ? null
            : territories.getHolderBySystemKey().get(systemKey);
        return holder == null
            ? buildFactionlessCell(territories, systemKey, shaped)
            : buildOwnedCell(territories, holder, shaped);
    }

    // A fused cell: its seams, in the holder's effective palette. Its fill and border are the
    // cluster's, drawn from the cluster's own shape, so this form has no slot for either. The style
    // and adjustment come from the pass's one styling read, so the seams paint exactly as the
    // cluster paints its fill and border. Desaturating swaps the holder's own palette for the pass's
    // shared desaturation palette, and the opacity multiplier scales every alpha on top of the
    // style's own opacities.
    private static StyledCell buildOwnedCell(
            PoliticalMapTerritories territories,
            DominantHolder holder,
            ShapedCell shaped) {

        var styling = territories.resolveBlocStyling(holder.factionId());
        var style = styling.style();
        var adjustment = styling.adjustment();
        var palette = MapPalettes.resolveEffectivePalette(
            adjustment,
            holder,
            territories.getDesaturationPalette());

        return new StyledCell.FusedCell(
            VertexRuns.flattenEdgesOfClass(shaped, false),
            resolvePaintOf(style.inner(), palette, adjustment),
            (float) style.innerWidth());
    }

    // A lone cell: its own fill and outline, and no seam - a factionless cell fuses with nothing,
    // so it has no sibling to divide from. Its outline's corners are rounded with the same corner
    // settings and gate the cluster borders use, so a lone unheld system reads as smoothly as a
    // cluster when rounding is on and stays a sharp Voronoi cell when it is off; the fill is
    // triangulated from that same rounded ring, so it cannot spill past the line its own outline
    // strokes. Drops the cell when neither element puts ink down - a cell is kept for its fill as
    // readily as for its outline.
    private static StyledCell buildFactionlessCell(
            PoliticalMapTerritories territories,
            SystemKey systemKey,
            ShapedCell shaped) {

        // One classification drives both the bundle and the recede, so a cell cannot take the
        // decivilised style yet miss the recede that style is meant to draw under. Classified
        // off what stands in the system rather than off the holder lookup that sent the cell
        // here: an inhabited system this layer's holding does not account for is not the empty
        // backdrop, whatever the absent holder alone would suggest.
        var category = FactionlessStyleResolver.resolveCategoryOf(
            territories.getInhabitedSystemKeys(),
            systemKey);

        var style = territories.getCategoryStyle(category);
        if (!style.outer().isDrawn() && !style.fill().isDrawn()) {
            return null;
        }
        // A factionless cell has no holder palette, so both slots hold the shared neutral colour:
        // whichever slot an element names, it paints neutral - unless the recede desaturates the
        // cell, in which case it recolours off the pass's desaturation palette exactly as a
        // receded bloc does.
        // The spotlit bloc's own presence spares the cell the recede, so a pick's colonies are not
        // sunk merely because this layer's holding could not attribute them to it. The cell keeps
        // its neutral factionless paint either way - full strength is the whole of what presence
        // buys it here. A cell drawn as no system names nowhere anyone could be living, so the
        // absent system skips the set rather than probing one that may be immutable and
        // null-hostile.
        var isSpotlitBlocPresent = systemKey != null
            && territories.getSpotlitPresenceSystemKeys().contains(systemKey);

        var adjustment = FactionlessStyleResolver.resolveRecedeOf(
            category,
            territories.getRecedeAdjustment(),
            isSpotlitBlocPresent);

        // A spared cell paints the lifted neutral rather than the plain one: the recede it was
        // spared only stopped it sinking, and the grey it would have kept is the value the
        // background around it sank from - close enough to read as the same surface. The two
        // palettes are the pass's own, so the lift and the sink are one decision apart.
        var ownPalette = isSpotlitBlocPresent
            ? territories.getPresencePalette()
            : territories.getNeutralPalette();

        var palette = MapPalettes.resolveEffectivePalette(
            adjustment,
            ownPalette,
            territories.getDesaturationPalette());

        // Only the rounding half of the profile: a lone cell has no spikes to sand, so the
        // sanding numbers are not this builder's to hold.
        var outline = resolveOutlineOf(
            shaped,
            territories.getGlobalStyle().borderSmoothing().cornerRounding());

        // Tessellate the fill only when it will actually be painted: an uninhabited cell is
        // outline-only and covers most of the sector, so triangulating every one of its cells
        // for a fill no pass emits would be the map's largest wasted rebuild cost.
        return new StyledCell.LoneCell(
            style.fill().isDrawn()
                ? PolygonTessellator.tessellateToTriangles(List.of(outline))
                : GlVertexRuns.NO_VERTICES,
            GlVertexRuns.flattenClosedLoopAsSegments(outline),
            resolvePaintOf(style.fill(), palette, adjustment),
            resolvePaintOf(style.outer(), palette, adjustment),
            (float) style.outerWidth());
    }

    // One element's paint as this cell resolves it: its colour picked from the cell's own two
    // palette shades - null for a "No color" choice, so the draw pass skips it - at its style
    // opacity scaled by the bloc's mute.
    private static UiElementPaint resolvePaintOf(
            ElementStyle element,
            FactionPalette palette,
            ElementStyleAdjustment adjustment) {

        return new UiElementPaint(
            MapPalettes.pickPaletteColour(
                element.colour(),
                palette),
            adjustment.muteOpacity(element.opacity()));
    }

    // The ring a factionless cell strokes and fills from: its inset outline, corner-rounded when
    // the sector-wide gate is on so it matches the cluster borders, raw when it is off. The cell
    // is a single convex inset polygon (all its edges are national border), so it needs no
    // chaining or envelope resolve - only the corner rounding.
    private static List<double[]> resolveOutlineOf(
            ShapedCell shaped,
            CornerRoundingStyle cornerRounding) {

        if (!cornerRounding.shouldRoundCorners()) {
            return shaped.fillPolygon();
        }
        // Through the shared pass rather than the smoothing library directly, so this outline
        // and the cluster borders around it round to one profile and cannot drift apart.
        return BorderSmoothing.roundLoopCorners(
            shaped.fillPolygon(),
            cornerRounding);
    }
}
