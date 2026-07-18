package kmu.maplayers.politicalmap.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Pins {@link FrontierRedistribution}: with the toggle off, or nothing frontier-empty, the
 * partition passes straight through; with it on, each frontier-empty cell is rewritten into
 * the owners that reach into it (drawing as those owners), a neutral keep-out pocket, and the
 * neutral space past an owner's reach - and the pieces tile the original cell exactly, so no
 * ground is dropped or double-claimed.
 */
final class FrontierRedistributionTest {
    // The disk-approximation segment count; matches the cell bound the fixtures are reasoned
    // about at. High enough that a cap or pocket arc reads as a smooth curve.
    private static final int BOUND_SEGMENTS = 48;

    // A cell radius far larger than any fixture, so an owner's reach never caps: the owner
    // absorbs the whole cell and no neutral capped shard is left.
    private static final double UNBOUNDED_REACH = 1_000_000.0;

    // The pieces of one frontier-empty cell tile it by straight-line clips, so their areas sum
    // to its own; this bounds the rounding slack (a dropped corner sliver) as a fraction of it.
    private static final double AREA_TILING_TOLERANCE = 0.01;

    @Nested
    class Redistribute {

        @Test
        void redistributeReturnsInputUnchangedWhenToggleOff() {
            var cells = Map.of(
                    "a", ownerCellNear(),
                    "E", deadStarCellFacing("a"));
            var grouping = identityGrouping(cells, Map.of("a", "hegemony"));

            var result = FrontierRedistribution.redistribute(
                    cells, grouping,
                    settings(sites("E", 0, 0, "a", 0, 3000), 500, UNBOUNDED_REACH, false),
                    BOUND_SEGMENTS);

            assertThat(result.cellEdgesByCellId()).isSameAs(cells);
            assertThat(result.grouping()).isSameAs(grouping);
        }

        @Test
        void redistributeReturnsInputUnchangedWhenNoCellIsFrontierEmpty() {
            // Two owned cells and nothing empty: there is no dead star to reach into.
            var cells = Map.of(
                    "a", ownerCellNear(),
                    "b", ownerCellNear());
            var grouping = identityGrouping(cells, Map.of("a", "hegemony", "b", "hegemony"));

            var result = FrontierRedistribution.redistribute(
                    cells, grouping,
                    settings(sites("a", 0, 0, "b", 3000, 0), 500, UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(result.cellEdgesByCellId()).isSameAs(cells);
        }

        @Test
        void redistributeLeavesOwnedAndDeepEmptyCellsUntouched() {
            // "deep" borders only the empty "E", so it is deep-empty, not frontier-empty: it
            // must survive as the very same list, drawing as itself.
            var cells = new LinkedHashMap<String, List<CellEdge>>();
            cells.put("a", ownerCellNear());
            cells.put("E", deadStarCellFacing("a", "deep"));
            cells.put("deep", deadStarCellFacing("E"));
            var grouping = identityGrouping(cells, Map.of("a", "hegemony"));

            var result = FrontierRedistribution.redistribute(
                    cells, grouping,
                    settings(sites("E", 0, 0, "a", 0, 3000, "deep", 0, -3000),
                            500, UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(result.cellEdgesByCellId().get("a")).isSameAs(cells.get("a"));
            assertThat(result.cellEdgesByCellId().get("deep")).isSameAs(cells.get("deep"));
            assertThat(result.cellEdgesByCellId()).doesNotContainKey("E");
            assertThat(result.grouping().resolveDrawnSystemIdOf("a")).isEqualTo("a");
            assertThat(result.grouping().resolveDrawnSystemIdOf("deep")).isEqualTo("deep");
        }

        @Test
        void redistributeAbsorbsAFrontierEmptyCellIntoItsLoneOwner() {
            // One owner to the north with unbounded reach: it absorbs all of E but the pocket.
            var deadStar = deadStarCellFacing("a");
            var cells = Map.of("a", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, Map.of("a", "hegemony")),
                    settings(sites("E", 0, 0, "a", 0, 3000), 500, UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(ownedPieceIds(result, "E")).isNotEmpty();
            assertThat(ownedPieceIds(result, "E"))
                    .allSatisfy(id -> assertThat(result.grouping().resolveDrawnSystemIdOf(id))
                            .isEqualTo("a"));
            assertThat(pocketIdOf(result, "E")).isNotNull();
            assertThat(result.grouping().resolveDrawnSystemIdOf(pocketIdOf(result, "E"))).isNull();
            assertThat(cappedShardIds(result, "E")).isEmpty();
            assertThat(totalAreaOfPiecesOf(result, "E"))
                    .isCloseTo(areaOf(deadStar), offset(areaOf(deadStar) * AREA_TILING_TOLERANCE));
        }

        @Test
        void redistributeSplitsAFrontierEmptyCellBetweenRivalOwners() {
            // A dead star between hegemony to the north and tritachyon to the south: each takes
            // its half, and the seam between them is a rival border, not a fused one.
            var deadStar = deadStarCellFacingNorthAndSouth("a", "b");
            var cells = Map.of("a", ownerCellNear(), "b", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, Map.of("a", "hegemony", "b", "tritachyon")),
                    settings(sites("E", 0, 0, "a", 0, 3000, "b", 0, -3000),
                            400, UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(drawnSystemsOfOwnedPieces(result, "E")).containsExactlyInAnyOrder("a", "b");
            assertThat(edgeTargetsOfOwnedPieces(result, "E"))
                    .contains(new EdgeTarget.AcrossSystem("b"))
                    .contains(new EdgeTarget.AcrossSystem("a"));
            assertThat(totalAreaOfPiecesOf(result, "E"))
                    .isCloseTo(areaOf(deadStar), offset(areaOf(deadStar) * AREA_TILING_TOLERANCE));
        }

        @Test
        void redistributeCapsAnOwnerReachAndLeavesTheExcessNeutral() {
            // A big dead star with a lone owner whose reach is smaller than the cell: the owner
            // absorbs only the band within reach, and the far excess stays neutral.
            var deadStar = bigDeadStarCellFacing("a");
            var cells = Map.of("a", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, Map.of("a", "hegemony")),
                    settings(sites("E", 0, 0, "a", 0, 3000), 500, 1600, true),
                    BOUND_SEGMENTS);

            assertThat(ownedPieceIds(result, "E")).isNotEmpty();
            assertThat(cappedShardIds(result, "E")).isNotEmpty();
            assertThat(cappedShardIds(result, "E"))
                    .allSatisfy(id -> assertThat(result.grouping().resolveDrawnSystemIdOf(id))
                            .isNull());
            assertThat(totalAreaOfPiecesOf(result, "E"))
                    .isCloseTo(areaOf(deadStar), offset(areaOf(deadStar) * AREA_TILING_TOLERANCE));
        }

        @Test
        void redistributeFusesCappedShardsOfTwoOwnersAsOneNeutralRegion() {
            // Two same-faction owners around a big dead star, reach capped so a wide neutral
            // remainder is left: the seam between the two owners' capped shards must fuse, so a
            // shard carries a same-territory edge rather than a stroked border down its middle.
            var deadStar = bigDeadStarCellFacing("a", "b");
            var cells = Map.of("a", ownerCellNear(), "b", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, Map.of("a", "hegemony", "b", "hegemony")),
                    settings(sites("E", 0, 0, "a", 0, 3000, "b", 3000, 0), 500, 1800, true),
                    BOUND_SEGMENTS);

            assertThat(cappedShardIds(result, "E")).isNotEmpty();
            assertThat(edgeTargetsOfCappedShards(result, "E")).contains(EdgeTarget.SAME_TERRITORY);
        }

        @Test
        void redistributeFusesTheOwnedWedgesOfAnEnclosedStarsFaction() {
            // One faction surrounds the dead star on all four sides: its wedges must fuse into a
            // continuous cup (a shared owned bisector reads as an interior seam, not a border),
            // with the keep-out pocket left in the middle.
            var deadStar = enclosedStarCell("a", "b", "c", "d");
            var owners = Map.of("a", "hegemony", "b", "hegemony", "c", "hegemony", "d", "hegemony");
            var cells = Map.of("a", ownerCellNear(), "b", ownerCellNear(),
                    "c", ownerCellNear(), "d", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, owners),
                    settings(sites("E", 0, 0, "a", 0, 3000, "b", 3000, 0,
                            "c", 0, -3000, "d", -3000, 0), 1000, UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(anyOwnedWedgeFusesToASibling(result, "E", owners)).isTrue();
            assertThat(pieceIdsWithPrefix(result, "E~pocket")).isNotEmpty();
            assertThat(totalAreaOfPiecesOf(result, "E"))
                    .isCloseTo(areaOf(deadStar), offset(areaOf(deadStar) * AREA_TILING_TOLERANCE));
        }

        @Test
        void redistributeWithholdsNoPocketWhenTheKeepOutIsZero() {
            // Keep-out zero: the owner absorbs right up to the star, no pocket is cut, and the
            // whole cell is still accounted for.
            var deadStar = deadStarCellFacing("a");
            var cells = Map.of("a", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, Map.of("a", "hegemony")),
                    settings(sites("E", 0, 0, "a", 0, 3000), 0, UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(ownedPieceIds(result, "E")).isNotEmpty();
            assertThat(pieceIdsWithPrefix(result, "E~pocket")).isEmpty();
            assertThat(totalAreaOfPiecesOf(result, "E"))
                    .isCloseTo(areaOf(deadStar), offset(areaOf(deadStar) * AREA_TILING_TOLERANCE));
        }

        @Test
        void redistributeLeavesAnUnownedNeighbourUnabsorbed() {
            // The dead star faces owner "a" and another empty cell "u": only "a" absorbs any of
            // it, and the wedge keeps a border facing the still-neutral "u".
            var deadStar = deadStarCellFacingOwnerAndEmpty("a", "u");
            var cells = Map.of("a", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, Map.of("a", "hegemony")),
                    settings(sites("E", 0, 0, "a", 0, 3000, "u", 3000, 0), 500,
                            UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(drawnSystemsOfOwnedPieces(result, "E")).containsExactly("a");
            assertThat(edgeTargetsOfOwnedPieces(result, "E"))
                    .contains(new EdgeTarget.AcrossSystem("u"));
        }

        @Test
        void redistributePassesAFrontierCellThroughUnchangedWhenItsSiteIsUnknown() {
            // Without a site for E the pass cannot place its pocket, so it leaves E exactly as it
            // found it rather than emitting a malformed rewrite.
            var deadStar = deadStarCellFacing("a");
            var cells = Map.of("a", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, Map.of("a", "hegemony")),
                    settings(sites("a", 0, 3000), 500, UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(result.cellEdgesByCellId().get("E")).isSameAs(deadStar);
            assertThat(result.grouping().resolveDrawnSystemIdOf("E")).isEqualTo("E");
        }

        @Test
        void redistributePassesAFrontierCellThroughUnchangedWhenNoOwnerHasASite() {
            // The owner is known but has no site, so no wedge can be carved: E stays the plain
            // neutral cell it already was.
            var deadStar = deadStarCellFacing("a");
            var cells = Map.of("a", ownerCellNear(), "E", deadStar);

            var result = FrontierRedistribution.redistribute(
                    cells, identityGrouping(cells, Map.of("a", "hegemony")),
                    settings(sites("E", 0, 0), 500, UNBOUNDED_REACH, true),
                    BOUND_SEGMENTS);

            assertThat(result.cellEdgesByCellId().get("E")).isSameAs(deadStar);
            assertThat(result.grouping().resolveDrawnSystemIdOf("E")).isEqualTo("E");
        }
    }

    // ----- scene builders -----------------------------------------------------------------

    // A frontier-empty cell centred on the origin whose north edge faces the given owner and
    // whose other edges are the map reach bound.
    private static List<CellEdge> deadStarCellFacing(String northNeighbour) {
        return ccwSquare(0, 0, 1000, EdgeTarget.REACH_BOUND, EdgeTarget.REACH_BOUND,
                new EdgeTarget.AcrossSystem(northNeighbour), EdgeTarget.REACH_BOUND);
    }

    // As above but with a second neighbour across the east edge - a cell touching two owners.
    private static List<CellEdge> deadStarCellFacing(String northNeighbour, String eastNeighbour) {
        return ccwSquare(0, 0, 1000, EdgeTarget.REACH_BOUND,
                new EdgeTarget.AcrossSystem(eastNeighbour),
                new EdgeTarget.AcrossSystem(northNeighbour), EdgeTarget.REACH_BOUND);
    }

    // A dead star between two rivals: north and south edges face an owner each.
    private static List<CellEdge> deadStarCellFacingNorthAndSouth(
            String northNeighbour, String southNeighbour) {
        return ccwSquare(0, 0, 1000, new EdgeTarget.AcrossSystem(southNeighbour),
                EdgeTarget.REACH_BOUND, new EdgeTarget.AcrossSystem(northNeighbour),
                EdgeTarget.REACH_BOUND);
    }

    // A large dead star (so a modest reach caps well inside it) facing one owner to the north.
    private static List<CellEdge> bigDeadStarCellFacing(String northNeighbour) {
        return ccwSquare(0, 0, 2000, EdgeTarget.REACH_BOUND, EdgeTarget.REACH_BOUND,
                new EdgeTarget.AcrossSystem(northNeighbour), EdgeTarget.REACH_BOUND);
    }

    // A large dead star facing one owner north and one east.
    private static List<CellEdge> bigDeadStarCellFacing(String northNeighbour, String eastNeighbour) {
        return ccwSquare(0, 0, 2000, EdgeTarget.REACH_BOUND,
                new EdgeTarget.AcrossSystem(eastNeighbour),
                new EdgeTarget.AcrossSystem(northNeighbour), EdgeTarget.REACH_BOUND);
    }

    // A dead star ringed by one faction: each of the four edges faces an owner (south, east,
    // north, west), matching the four cardinal owner sites the fixture places around it.
    private static List<CellEdge> enclosedStarCell(
            String south, String east, String north, String west) {
        return ccwSquare(0, 0, 1000, new EdgeTarget.AcrossSystem(south),
                new EdgeTarget.AcrossSystem(east), new EdgeTarget.AcrossSystem(north),
                new EdgeTarget.AcrossSystem(west));
    }

    // A dead star whose north edge faces an owner and whose east edge faces another empty cell -
    // the owner absorbs, the empty neighbour must not.
    private static List<CellEdge> deadStarCellFacingOwnerAndEmpty(
            String northOwner, String eastEmpty) {
        return ccwSquare(0, 0, 1000, EdgeTarget.REACH_BOUND,
                new EdgeTarget.AcrossSystem(eastEmpty),
                new EdgeTarget.AcrossSystem(northOwner), EdgeTarget.REACH_BOUND);
    }

    // A small owned cell placed off to the side; its own shape is irrelevant to a neighbour's
    // redistribution (which reads owner sites from the settings), so it only has to exist.
    private static List<CellEdge> ownerCellNear() {
        return ccwSquare(0, 5000, 200, EdgeTarget.REACH_BOUND, EdgeTarget.REACH_BOUND,
                EdgeTarget.REACH_BOUND, EdgeTarget.REACH_BOUND);
    }

    // A counter-clockwise square as four cell edges - bottom, right, top, left - each carrying
    // the matching target.
    private static List<CellEdge> ccwSquare(
            double centreX, double centreY, double half,
            EdgeTarget bottom, EdgeTarget right, EdgeTarget top, EdgeTarget left) {
        var minX = centreX - half;
        var maxX = centreX + half;
        var minY = centreY - half;
        var maxY = centreY + half;
        return List.of(
                new CellEdge(minX, minY, maxX, minY, bottom),
                new CellEdge(maxX, minY, maxX, maxY, right),
                new CellEdge(maxX, maxY, minX, maxY, top),
                new CellEdge(minX, maxY, minX, minY, left));
    }

    private static CellGrouping identityGrouping(
            Map<String, List<CellEdge>> cells, Map<String, String> owners) {
        var drawnSystemByCellId = new LinkedHashMap<String, String>();
        for (var cellId : cells.keySet()) {
            drawnSystemByCellId.put(cellId, cellId);
        }
        return new CellGrouping(drawnSystemByCellId, owners);
    }

    private static FrontierSettings settings(
            Map<String, double[]> sites, double keepOut, double cellRadius, boolean enabled) {
        return new FrontierSettings(sites, keepOut, cellRadius, enabled);
    }

    // Site coordinates as flat triples: id, x, y, id, x, y, ...
    private static Map<String, double[]> sites(Object... idXy) {
        var sites = new LinkedHashMap<String, double[]>();
        for (var i = 0; i < idXy.length; i += 3) {
            sites.put((String) idXy[i],
                    new double[] {((Number) idXy[i + 1]).doubleValue(),
                            ((Number) idXy[i + 2]).doubleValue()});
        }
        return sites;
    }

    // ----- result probes ------------------------------------------------------------------

    private static List<String> ownedPieceIds(RedistributedCells result, String cellId) {
        return pieceIdsWithPrefix(result, cellId + "@");
    }

    private static String pocketIdOf(RedistributedCells result, String cellId) {
        var pockets = pieceIdsWithPrefix(result, cellId + "~pocket");
        return pockets.isEmpty() ? null : pockets.get(0);
    }

    private static List<String> cappedShardIds(RedistributedCells result, String cellId) {
        return pieceIdsWithPrefix(result, cellId + "~void#");
    }

    // Every emitted piece derived from one frontier-empty cell: its owner wedges, its pocket,
    // and its capped shards - all keyed with the cell id as a prefix.
    private static List<String> allPieceIdsOf(RedistributedCells result, String cellId) {
        var ids = new ArrayList<String>();
        ids.addAll(pieceIdsWithPrefix(result, cellId + "@"));
        ids.addAll(pieceIdsWithPrefix(result, cellId + "~"));
        return ids;
    }

    private static List<String> pieceIdsWithPrefix(RedistributedCells result, String prefix) {
        var ids = new ArrayList<String>();
        for (var id : result.cellEdgesByCellId().keySet()) {
            if (id.startsWith(prefix)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static List<String> drawnSystemsOfOwnedPieces(RedistributedCells result, String cellId) {
        var drawnSystems = new ArrayList<String>();
        for (var id : ownedPieceIds(result, cellId)) {
            var drawnSystem = result.grouping().resolveDrawnSystemIdOf(id);
            if (!drawnSystems.contains(drawnSystem)) {
                drawnSystems.add(drawnSystem);
            }
        }
        return drawnSystems;
    }

    private static List<EdgeTarget> edgeTargetsOfOwnedPieces(
            RedistributedCells result, String cellId) {
        return edgeTargetsOf(result, ownedPieceIds(result, cellId));
    }

    private static List<EdgeTarget> edgeTargetsOfCappedShards(
            RedistributedCells result, String cellId) {
        return edgeTargetsOf(result, cappedShardIds(result, cellId));
    }

    // Whether any owned wedge of the cell shares an interior seam with a sibling system - a
    // same-key neighbour across an AcrossSystem edge, which is what makes an enclosed faction's
    // wedges fuse into one cup rather than stroking borders between themselves.
    private static boolean anyOwnedWedgeFusesToASibling(
            RedistributedCells result, String cellId, Map<String, String> groupKeys) {
        for (var id : ownedPieceIds(result, cellId)) {
            var ownKey = result.grouping().resolveGroupKeyOf(id);
            for (var edge : result.cellEdgesByCellId().get(id)) {
                if (edge.target() instanceof EdgeTarget.AcrossSystem
                        && EdgeClassifier.classifyAcross(edge, ownKey, groupKeys)
                                == EdgeClass.INTERIOR_SEAM) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<EdgeTarget> edgeTargetsOf(RedistributedCells result, List<String> ids) {
        var targets = new ArrayList<EdgeTarget>();
        for (var id : ids) {
            for (var edge : result.cellEdgesByCellId().get(id)) {
                targets.add(edge.target());
            }
        }
        return targets;
    }

    private static double totalAreaOfPiecesOf(RedistributedCells result, String cellId) {
        var total = 0.0;
        for (var id : allPieceIdsOf(result, cellId)) {
            total += areaOf(result.cellEdgesByCellId().get(id));
        }
        return total;
    }

    // The area a ring of cell edges encloses, by the shoelace formula over its vertices.
    private static double areaOf(List<CellEdge> edges) {
        var twiceArea = 0.0;
        for (var edge : edges) {
            twiceArea += edge.x1() * edge.y2() - edge.x2() * edge.y1();
        }
        return Math.abs(twiceArea) * 0.5;
    }
}
