package kmu.maplayers.base.geometry.v3;

/**
 * One straight reach of a smoothed coast: the two vertices it runs between.
 *
 * @param from the vertex it leaves
 * @param to   the vertex it arrives at
 */
public record CoastReach(
    CoastVertex from,
    CoastVertex to) {
}
