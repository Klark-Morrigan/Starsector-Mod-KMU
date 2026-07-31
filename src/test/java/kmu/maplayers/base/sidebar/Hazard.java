package kmu.maplayers.base.sidebar;

/**
 * Test fixture: one row of a picker list no part of the political map declares - the stand-in
 * item a second layer's sidebar list would rank. Carries a name and two numerics so the foreign
 * sort modes ({@link HazardSortMode}) have distinct keys to rank and flip on.
 */
record Hazard(
    String name,
    int severity,
    int radius) {
}
