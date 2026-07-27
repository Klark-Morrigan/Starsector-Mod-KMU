package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.math.geometry.PolygonSmoothing;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.geometry.ShapedCell;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.theme.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.ElementStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.MapCategory;

import java.util.List;

/**
 * Bakes one shaped cell into the {@link StyledCell} the renderer paints, in whichever of the
 * two forms the cell takes.
 *
 * <p>An <em>owned</em> cell contributes only its interior seams: its fill and national border
 * are per cluster, drawn from the tessellated region in {@link FactionTerritory}, because a
 * bloc's cells fuse into one frontier. Keeping the seams is what lets a footprint read as its
 * constituent cells rather than one smooth blob - the spotlighted bloc included, where the
 * solid-to-hatched transition is such a border like any other.
 *
 * <p>A <em>factionless</em> cell (decivilised, or uninhabited) does not fuse, so it has no
 * cluster to inherit from and keeps its own fill and outline. It names no faction palette
 * either, so both its palette slots hold the shared neutral colour - until the pass recedes it,
 * which decivilised ground takes as readily as a bloc does.
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
     * here - the owner, the palette, whether the ground is decivilised - is known per system
     * and not per cell.
     *
     * @param territories this pass's retained ownership, theme, and filter state
     * @param systemId    the system the cell draws as, or null for a cell with no star of its
     *                    own, which draws as plain uninhabited ground - it has no owner to
     *                    colour it and no market to have died
     * @param shaped      the cell's inset shape
     * @return the cell's draw record, or null when it puts no ink on the map
     */
    public static StyledCell buildStyledCellForSystem(
            PoliticalMapTerritories territories,
            String systemId,
            ShapedCell shaped) {

        // A cell the border inset consumed or collapsed comes back with an empty fill
        // (CellShaper via PolygonOffsets.insetSelectedEdges guarantees empty-or-drawable), so
        // there is nothing to fill or stroke.
        if (shaped.fillPolygon().isEmpty()) {
            return null;
        }
        // A cell with no star of its own has no owner to look up, so the null id skips the owner
        // map rather than probing it for a key it does not hold - keeping the null-star path clear
        // of whether the owner map happens to tolerate a null-key get.
        var owner = systemId == null ? null : territories.getOwnerBySystemId().get(systemId);
        return owner == null
                ? buildFactionlessCell(territories, systemId, shaped)
                : buildOwnedCell(territories, owner, shaped);
    }

    // Seams only, in the owner's effective palette. The style and adjustment come from the pass's
    // one styling read, so this cell paints its seams exactly as its cluster paints its fill and
    // border. Desaturating swaps the owner's own palette for the pass's shared desaturation
    // palette, and the opacity multiplier scales every alpha on top of the style's own opacities.
    private static StyledCell buildOwnedCell(
            PoliticalMapTerritories territories,
            DominantOwner owner,
            ShapedCell shaped) {

        var styling = territories.resolveBlocStyling(owner.factionId());
        var style = styling.style();
        var adjustment = styling.adjustment();
        var palette = MapPalettes.resolveEffectivePalette(
                adjustment,
                owner,
                territories.getDesaturationPalette());
        return new StyledCell(
                GlVertexRuns.NO_VERTICES,
                GlVertexRuns.NO_VERTICES,
                VertexRuns.flattenEdgesOfClass(shaped, false),
                resolveDeferredPaintOf(style.fill(), adjustment),
                resolveDeferredPaintOf(style.outer(), adjustment),
                resolvePaintOf(style.inner(), palette, adjustment),
                (float) style.outerWidth(),
                (float) style.innerWidth());
    }

    // A factionless cell's own fill and outline, plus the seam geometry every cell bakes. Its
    // outline's corners are rounded with the same corner settings and gate the cluster borders
    // use, so a lone dead system reads as smoothly as a cluster when rounding is on and stays a
    // sharp Voronoi cell when it is off; the fill is triangulated from that same rounded ring, so
    // it cannot spill past the line its own outline strokes. Drops the cell when neither element
    // puts ink down - a cell is kept for its fill as readily as for its outline.
    private static StyledCell buildFactionlessCell(
            PoliticalMapTerritories territories,
            String systemId,
            ShapedCell shaped) {

        var isDecivilised = isDecivilisedSystem(territories, systemId);
        var style = resolveFactionlessStyleOf(territories, isDecivilised);
        if (!style.outer().isDrawn() && !style.fill().isDrawn()) {
            return null;
        }
        // Factionless ground has no owner palette, so both slots hold the shared neutral colour:
        // whichever slot an element names, it paints neutral - unless the recede desaturates the
        // cell, in which case it recolours off the pass's desaturation palette exactly as a
        // receded bloc does.
        var adjustment = resolveFactionlessAdjustment(territories, isDecivilised);
        var neutralColor = territories.getNeutralColor();
        var palette = MapPalettes.resolveEffectivePalette(
                adjustment,
                new FactionPalette(neutralColor, neutralColor),
                territories.getDesaturationPalette());
        var outline = resolveOutlineOf(shaped, territories.getGlobalStyle().borderSmoothing());
        // Tessellate the fill only when it will actually be painted: uninhabited ground is
        // outline-only and covers most of the sector, so triangulating every one of its cells
        // for a fill no pass emits would be the map's largest wasted rebuild cost.
        return new StyledCell(
                style.fill().isDrawn()
                        ? PolygonTessellator.tessellateToTriangles(List.of(outline))
                        : GlVertexRuns.NO_VERTICES,
                GlVertexRuns.flattenClosedLoopAsSegments(outline),
                VertexRuns.flattenEdgesOfClass(shaped, false),
                resolvePaintOf(style.fill(), palette, adjustment),
                resolvePaintOf(style.outer(), palette, adjustment),
                resolvePaintOf(style.inner(), palette, adjustment),
                (float) style.outerWidth(),
                (float) style.innerWidth());
    }

    // How far a factionless cell recedes this pass. Decivilised ground is part of the "rest of the
    // sector" a spotlight recedes: a dead colony is a political feature drawn in a fill of its own,
    // so leaving it at full strength lets it out-read the bloc the spotlight is meant to isolate.
    // It therefore takes the pass's shared recede, which is the identity off filter, so an
    // unfiltered map draws its dead worlds exactly as before. Uninhabited ground is the empty
    // backdrop the whole map is drawn over rather than anything the spotlight competes with, and
    // its faint outline is what gives the sector its shape, so it never recedes.
    private static BlocStyleAdjustment resolveFactionlessAdjustment(
            PoliticalMapTerritories territories,
            boolean isDecivilised) {

        return isDecivilised ? territories.getRecedeAdjustment() : BlocStyleAdjustment.NONE;
    }

    // Which factionless category a cell falls under: decivilised where a revealed dead colony
    // sits, uninhabited everywhere else.
    private static CategoryStyle resolveFactionlessStyleOf(
            PoliticalMapTerritories territories,
            boolean isDecivilised) {

        return isDecivilised
                ? territories.getCategoryStyle(MapCategory.DECIVILISED)
                : territories.getCategoryStyle(MapCategory.UNINHABITED);
    }

    // Whether a revealed dead colony sits in the system a cell draws as - the one test both the
    // category and the recede read, so a cell cannot take the decivilised style yet miss its
    // recede. A cell with no star of its own names no system to test, so it never reaches the
    // decivilised set - which may be immutable and null-hostile.
    private static boolean isDecivilisedSystem(
            PoliticalMapTerritories territories,
            String systemId) {

        return systemId != null && territories.getDecivilisedSystemIds().contains(systemId);
    }

    // One element's paint as this cell resolves it: its colour picked from the cell's own two
    // palette shades - null for a "No color" choice, so the draw pass skips it - at its style
    // opacity scaled by the bloc's mute.
    private static UiElementPaint resolvePaintOf(
            ElementStyle element,
            FactionPalette palette,
            BlocStyleAdjustment adjustment) {

        return new UiElementPaint(
                MapPalettes.pickPaletteColor(
                        element.color(),
                        palette.primaryColor(),
                        palette.secondaryColor()),
                adjustment.muteOpacity(element.opacity()));
    }

    // The paint for an element this cell leaves to its cluster to draw: no colour, so nothing is
    // emitted per cell. Its opacity still travels, because opacity is a property of the style
    // rather than of whichever pass paints it - the cluster resolves the identical value off the
    // same bundle, so the cell's record stays a complete description of how that element draws.
    private static UiElementPaint resolveDeferredPaintOf(
            ElementStyle element,
            BlocStyleAdjustment adjustment) {

        return new UiElementPaint(null, adjustment.muteOpacity(element.opacity()));
    }

    // The ring a factionless cell strokes and fills from: its inset outline, corner-rounded when
    // the sector-wide gate is on so it matches the cluster borders, raw when it is off. The cell
    // is a single convex inset polygon (all its edges are national border), so it needs no
    // chaining or envelope resolve - only the corner rounding.
    private static List<double[]> resolveOutlineOf(
            ShapedCell shaped,
            BorderSmoothingStyle borderSmoothing) {

        if (!borderSmoothing.shouldRoundCorners()) {
            return shaped.fillPolygon();
        }
        return PolygonSmoothing.roundCorners(
                shaped.fillPolygon(),
                borderSmoothing.cornerRadius(),
                borderSmoothing.cornerSegments(),
                borderSmoothing.chamferAngleRadians());
    }
}
