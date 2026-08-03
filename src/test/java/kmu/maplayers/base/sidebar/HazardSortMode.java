package kmu.maplayers.base.sidebar;

import java.util.Comparator;

/**
 * Test fixture: a set of sort modes no part of the political map declares - the stand-in for
 * whatever a second layer ranks its picker list by. An enum, since that is the shape a layer is
 * expected to reach for. Alpha runs ascending by default while the two numeric modes run
 * descending, so a suite can tell a mode's own default direction from the active sort's live one.
 * Only {@link #RADIUS} declares a trailing value; the other two leave the seam's blank default, so
 * a suite can tell a mode that writes a number onto its rows from one that shows none.
 */
enum HazardSortMode implements ListSortMode<Hazard> {
    ALPHA(
        "alpha",
        "hazard_sort_alpha",
        SortDirection.ASCENDING,
        Comparator.comparing(Hazard::displayName, String.CASE_INSENSITIVE_ORDER)),
    SEVERITY(
        "severity",
        "hazard_sort_severity",
        SortDirection.DESCENDING,
        Comparator.comparingInt(Hazard::severity)),
    RADIUS(
        "radius",
        "hazard_sort_radius",
        SortDirection.DESCENDING,
        Comparator.comparingInt(Hazard::radius)) {

        // The one mode that writes its number onto the rows, so a suite can tell a drawn trailing
        // value apart from the blank the other two leave.
        @Override
        public String resolveTrailingValue(Hazard hazard) {
            return String.valueOf(hazard.radius());
        }
    };

    private final String persistenceKey;
    private final String labelKey;
    private final SortDirection defaultDirection;

    // The mode's key low-to-high; comparator(direction) runs it as asked, forward or reversed.
    private final Comparator<Hazard> ascendingOrder;

    HazardSortMode(
            String persistenceKey,
            String labelKey,
            SortDirection defaultDirection,
            Comparator<Hazard> ascendingOrder) {

        this.persistenceKey = persistenceKey;
        this.labelKey = labelKey;
        this.defaultDirection = defaultDirection;
        this.ascendingOrder = ascendingOrder;
    }

    @Override
    public String persistenceKey() {
        return persistenceKey;
    }

    @Override
    public String labelKey() {
        return labelKey;
    }

    @Override
    public SortDirection defaultDirection() {
        return defaultDirection;
    }

    @Override
    public Comparator<Hazard> comparator(SortDirection direction) {
        return direction == SortDirection.ASCENDING
            ? ascendingOrder
            : ascendingOrder.reversed();
    }
}
