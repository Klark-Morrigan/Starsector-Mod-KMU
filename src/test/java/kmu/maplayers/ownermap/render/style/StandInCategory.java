package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.MapStyleCategory;

/**
 * Four categories a fixture layer divides its cells into, for suites over the tier's stages.
 *
 * <p>The tier keys its theme on the open {@link MapStyleCategory} and never names one layer's set, so
 * its suites state a set of their own rather than borrowing a layer's. Four, so a suite can still
 * tell an owned cell from a receded one and a settled unowned cell from an empty one.
 */
public enum StandInCategory implements MapStyleCategory {

    // An owner at full strength.
    OWNED,

    // An owner drawn as backdrop.
    RECEDED,

    // A cell nobody owns that something stands in.
    SETTLED,

    // A cell nobody owns and nothing stands in.
    EMPTY
}
