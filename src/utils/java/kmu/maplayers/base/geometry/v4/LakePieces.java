package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.NamedRegion;
import kmu.maplayers.base.geometry.SectorFixture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The lake tier's own pieces of a partition, sorted by kind and named.
 *
 * <p>One pass over the pieces, so each is read once and lands in exactly one list: water and
 * margin are disjoint because a piece is of one kind, and a piece the tier never touched is in
 * neither. The names come out of the same pass, so a name is always of a piece that is filled.
 *
 * @param water  the pieces inside a lake's coast, in the order the walk closed them
 * @param margin the bays behind the reaches, in the same order
 * @param names  every one of those pieces as a named region, water and margin alike
 */
public record LakePieces(
    List<Face> water,
    List<Face> margin,
    List<NamedRegion> names) {

    /**
     * Sorts a partition's pieces into the lake tier's two kinds, and names each.
     *
     * @param pieces     the partition's pieces, as the walk closed them
     * @param coastWalls the lake coast's walls as laid, which say which side of a reach a
     *                   piece is on
     * @param lakeRings  the cells round each lake the trace drew a coast for
     * @param fixture    the sector, for the sites and the system IDs a piece is named from
     * @return the tier's pieces and their names
     */
    public static LakePieces collectLakePieces(
            List<Face> pieces,
            List<LabelledWall> coastWalls,
            List<Set<Integer>> lakeRings,
            SectorFixture fixture) {

        var water = new ArrayList<Face>();
        var margin = new ArrayList<Face>();
        var names = new ArrayList<NamedRegion>();

        for (var piece : pieces) {

            var kind = LakeTier.readKind(piece, coastWalls, lakeRings);

            if (kind.isEmpty()) {
                continue;
            }

            if (kind.get() == LakeTier.Kind.LAKE) {
                water.add(piece);
            } else {
                margin.add(piece);
            }
            names.add(NamedRegion.nameRegion(
                LakeTier.namePiece(piece, kind.get(), fixture.getSites(), fixture.getSystemIds()),
                piece.boundary()));
        }
        return new LakePieces(List.copyOf(water), List.copyOf(margin), List.copyOf(names));
    }
}
