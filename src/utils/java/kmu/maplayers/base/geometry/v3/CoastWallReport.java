package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.WallKind;
import kmu.maplayers.base.geometry.walls.DiscUnionBoundary;
import kmu.maplayers.base.geometry.walls.Walls;

import java.util.ArrayList;
import java.util.Locale;

/**
 * What became of the coast's own reaches once they were offered as walls.
 *
 * <p>A reach that is drawn and a reach that is LAID are different things. The coast draws every
 * step it walks; only a step long enough to hold a channel is offered as a wall, and only a
 * wall the trace can attach is laid. A gap can therefore be closed on screen and open to the
 * trace - which is a fill missing under a line that says the void was shut in, and the one
 * fault a picture cannot distinguish from a fill that is merely small.
 *
 * <p>A map-wide report rather than an answer about one place, so it is printed for every sector
 * whether or not anyone has picked a point on it: what decides whether a rule is worth changing
 * is how many reaches it refuses, not that it refused this one.
 */
public final class CoastWallReport {

    // How near two of the coast's landings on one cell must be to count as a tight turn: a
    // mouth's width, which is how far round a circle a tangent wall must go to be a channel
    // clear of its own line.
    private static final double TIGHT_TURN_WITHIN = 2000;

    private CoastWallReport() {
    }

    /**
     * Reports what the walk made of every reach the coast offered it.
     *
     * @param laid the coast with its walls down
     */
    public static void reportCoastWalls(LaidCoast laid) {

        var offered = laid.offered();
        var walls = laid.walls();
        var atCells = laid.atCells();

        System.out.printf(
            Locale.ROOT,
            "coast reaches offered %d: at the cells' own reach %s | a channel out %s%n",
            offered.size(),
            WallRefusals.summariseRefusals(atCells, walls, offered),
            WallRefusals.summariseRefusals(laid.atDrawnReach(), walls, offered));

        WallRefusals.reportEachRefusal(atCells, walls, offered, "reach");
        reportWallCrossings(atCells, laid.atDrawnReach(), walls);
        reportWallSideStray(atCells, walls, laid.parameters());
        reportCoastTurns(atCells, walls);
    }

    // How many laid walls cross another, which is the one thing on this map bounded by
    // something the walk never asks about. Counted at both reaches, since a pair that misses
    // at one can meet at the other.
    private static void reportWallCrossings(
            DiscUnion atCells,
            DiscUnion atDrawn,
            Walls walls) {

        System.out.printf(
            Locale.ROOT,
            "walls crossing another wall: %d at the cells' own reach, %d a channel out%n",
            DiscUnionBoundary.findWallCrossings(atCells, walls).size(),
            DiscUnionBoundary.findWallCrossings(atDrawn, walls).size());
    }

    // How far a laid wall's two drawn sides sit from the wall itself.
    //
    // A wall's drawn ends are taken from the edges of the mouth it opens, and a mouth is as
    // wide as the channel is - measured round the circle. Across a wall that leaves a cell
    // along its tangent, being a channel clear of the line means travelling a long way round,
    // so those edges can sit far from the wall while being the right distance from its line.
    // The pocket then closes on a line that is nowhere near the coast it is supposed to close
    // on, which is what a spike out to sea is.
    //
    // The bridges have this check already and read 0 - a bridge crosses its circles steeply,
    // so its mouth edges are where the bridge is. This is the same question asked of the other
    // kind of wall.
    private static void reportWallSideStray(
            DiscUnion union,
            Walls walls,
            SectorGeometryParameters parameters) {

        var worst = 0.0;
        double[] worstAt = null;

        for (var chord : DiscUnionBoundary.findAttachableChords(union, walls)) {

            if (chord.kind() != WallKind.COAST_REACH) {
                continue;
            }
            for (var side : DiscUnionBoundary.findChordSides(union, chord, walls)) {

                for (var point : side) {

                    var away = Segments.computeDistanceToPoint(
                        chord.findStart(), chord.findEnd(), point);

                    if (away > worst) {
                        worst = away;
                        worstAt = point;
                    }
                }
            }
        }

        System.out.printf(
            Locale.ROOT,
            "worst coast wall side strays %.0f from its own reach%s (the channel is %.0f)%n",
            worst,
            worstAt == null
                ? ""
                : String.format(Locale.ROOT, ", at %.0f,%.0f", worstAt[0], worstAt[1]),
            parameters.borderInset());
    }

    // How tightly the coast turns on the cells it turns on.
    //
    // Two reaches leaving one cell land on it somewhere, and how far apart those landings are
    // is what decides whether their mouths nest - a mouth is a channel wide measured round the
    // circle, which for a tangent reach is a thousand units and more. A pair landing closer
    // than that is a cell the coast barely touches, kept as a corner it then has to cut.
    private static void reportCoastTurns(
            DiscUnion union,
            Walls walls) {

        var laid = new ArrayList<Chord>();

        for (var chord : DiscUnionBoundary.findAttachableChords(union, walls)) {

            if (chord.kind() == WallKind.COAST_REACH) {
                laid.add(chord);
            }
        }

        var turns = 0;
        var tight = 0;

        for (var one = 0; one < laid.size(); one++) {
            for (var other = one + 1; other < laid.size(); other++) {

                var shared = findSharedCell(laid.get(one), laid.get(other));

                if (shared < 0) {
                    continue;
                }
                turns++;

                var apart = Points.computeDistance(
                    laid.get(one).findEndOn(shared), laid.get(other).findEndOn(shared));

                if (apart < TIGHT_TURN_WITHIN) {

                    tight++;
                    System.out.printf(
                        Locale.ROOT,
                        "  coast turns on cell %d: reaches %d-%d and %d-%d land %.0f apart%n",
                        shared,
                        laid.get(one).fromCircle(),
                        laid.get(one).toCircle(),
                        laid.get(other).fromCircle(),
                        laid.get(other).toCircle(),
                        apart);
                }
            }
        }

        System.out.printf(
            Locale.ROOT,
            "the coast turns on a cell %d times, %d of them within %.0f%n",
            turns,
            tight,
            TIGHT_TURN_WITHIN);
    }

    // The cell two walls share, or none. Two reaches of one coast meet on the cell the coast
    // turned on, which is the only pair worth measuring.
    private static int findSharedCell(
            Chord one,
            Chord other) {

        if (one.fromCircle() == other.fromCircle() || one.fromCircle() == other.toCircle()) {
            return one.fromCircle();
        }

        if (one.toCircle() == other.fromCircle() || one.toCircle() == other.toCircle()) {
            return one.toCircle();
        }
        return -1;
    }
}
