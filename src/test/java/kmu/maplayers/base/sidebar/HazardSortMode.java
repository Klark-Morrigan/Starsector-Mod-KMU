package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.util.KmuStrings;

import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;

/**
 * Test fixture: a set of sort modes no part of the political map declares - the stand-in for
 * whatever a second layer ranks its picker list by. An enum, since that is the shape a layer is
 * expected to reach for. Alpha runs ascending by default while the two numeric modes run
 * descending, so a suite can tell a mode's own default direction from the active sort's live one.
 * Only {@link #RADIUS} declares a trailing value; the other two leave the seam's no-runs default, so
 * a suite can tell a mode that writes a number onto its rows from one that shows none.
 *
 * <p>Each label resolves through {@link kmu.util.KmuStrings} under a key of its own, which is what a
 * real layer does with the seam's hand-over-drawn-text rule, so a suite that stubs the strings table
 * exercises the same path the political modes take.
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
        // value apart from the unfilled slot the other two leave. One run in the row's own tone,
        // which is what a mode with no colour opinion of its own answers.
        @Override
        public List<TextSpan> resolveTrailingRuns(Hazard hazard, Color defaultColour) {
            return List.of(new TextSpan(String.valueOf(hazard.radius()), defaultColour));
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

    /**
     * Stubs every mode's label on a mocked strings table, so a suite exercising a picker over these
     * modes resolves drawable text without the live table. A control's labels are copied and reject a
     * null option name, so a suite that skips this fails on the read rather than on what it is about;
     * the constant names stand in for the drawn labels, which no assertion reads.
     *
     * @param stringsMock the mocked strings table these labels resolve through
     */
    static void stubLabelsOn(MockedStatic<KmuStrings> stringsMock) {
        for (var mode : values()) {
            stringsMock
                .when(() -> KmuStrings.get(mode.labelKey))
                .thenReturn(mode.name());
        }
    }

    @Override
    public String persistenceKey() {
        return persistenceKey;
    }

    @Override
    public String resolveLabelText() {
        return KmuStrings.get(labelKey);
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
