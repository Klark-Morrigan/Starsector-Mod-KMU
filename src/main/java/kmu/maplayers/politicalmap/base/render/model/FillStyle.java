package kmu.maplayers.politicalmap.base.render.model;

/**
 * How a faction territory's interior is painted: a solid tessellated fill, or a diagonal hatch.
 *
 * <p>Every territory is {@link #SOLID} except the political-map filter's contested cluster - the
 * systems the spotlighted bloc is present in but does not dominate - which draws {@link #HATCHED}
 * to read as "mine, but contested" against the solid cluster it holds outright. The flag rides on
 * {@link FactionTerritory} so the render layer picks the fill primitive per territory without
 * re-deriving why a cluster is contested; the hatch geometry itself is generated downstream.
 */
public enum FillStyle {
    /** A solid tessellated-triangle fill - every territory outside the filter's contested cluster. */
    SOLID,
    /** A diagonal hatch fill - the filter's present-but-dominated (contested) cluster. */
    HATCHED
}
