package kmu.politicalmap.domain;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.Polygons;
import kmlib.math.geometry.VoronoiCellBuilder;
import kmlib.starsector.systems.StarSystems;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the political map's border geometry from the live sector.
 *
 * <p>Takes every star system's hyperspace position, partitions the sector
 * into one Voronoi cell per system (bounded to a zone-of-control radius), and
 * offsets each cell's edges inward so neighbouring provinces leave a uniform
 * channel between them. Returns the borders as plain line segments; it carries
 * no rendering or GL concern, so the partition can be unit tested against a
 * stub sector, independent of how it is drawn.
 */
public final class PoliticalMapBorderGeometry {
    // How far a system's cell may reach from it - its zone of control. Caps a
    // lone system's reach into the void and rounds frontier borders; close
    // systems still meet along their bisectors. Tuning knob for the look.
    private static final double MAX_CELL_RADIUS = 5000.0;

    // Distance each border edge is offset inward from its true Voronoi edge,
    // so two neighbours leave a uniform channel of twice this between them.
    private static final double BORDER_OFFSET = 150.0;

    private PoliticalMapBorderGeometry() {
    }

    /**
     * Builds the province border segments for {@code sector}.
     *
     * @param sector the sector whose star systems seed the partition
     * @return border segments as {x1, y1, x2, y2} in hyperspace world
     *         coordinates; empty when there are no star systems
     */
    public static List<double[]> buildBorderSegments(SectorAPI sector) {
        // Seeds are every star system, not just the populated ones: the
        // partition is computed over all systems so cell borders stay fixed
        // regardless of which systems are populated or known. Choosing which
        // cells to draw (populated, or discovered when hidden) is a
        // render-layer concern, not a narrowing of the seed set.
        List<double[]> sites = StarSystems.getHyperspacePositions(sector);
        List<List<double[]>> cells = VoronoiCellBuilder.buildCells(sites, MAX_CELL_RADIUS);

        List<double[]> segments = new ArrayList<>();
        for (List<double[]> cell : cells) {
            segments.addAll(Polygons.offsetEdgesInward(cell, BORDER_OFFSET));
        }
        return segments;
    }
}
