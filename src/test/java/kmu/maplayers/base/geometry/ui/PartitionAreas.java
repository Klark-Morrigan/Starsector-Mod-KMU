package kmu.maplayers.base.geometry.ui;

import kmu.maplayers.base.geometry.v4.VoidPartition;

/**
 * The two areas a tier's lines are judged by: the whole void, which a line may divide but not
 * shrink, and the open sea, which no lake tier reaches.
 *
 * <p>Shared by every suite that lays a lake tier, so each tier is held to one reading of "the
 * void came back whole" rather than to a copy that can drift.
 */
final class PartitionAreas {

    private PartitionAreas() {
    }

    // Every piece's area together, holes cut out, which is the void the partition covers.
    static double measureVoid(VoidPartition partition) {

        var area = 0.0;

        for (var piece : partition.collectPieces()) {
            area += piece.measureArea();
        }
        return area;
    }

    // The one piece with holes, which is the sea.
    static double measureSea(VoidPartition partition) {

        for (var piece : partition.collectPieces()) {

            if (!piece.holes().isEmpty()) {
                return piece.measureArea();
            }
        }
        throw new IllegalStateException("no sea to measure");
    }
}
