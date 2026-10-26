package kmu.maplayers.ownermap.render.rebuild;

/**
 * The terms both of a rebuild's keys share, read once a frame: the settings revision, since a
 * settings change restyles every cell over the fixed geometry; the active view, since their owners,
 * styling and labels differ; and the view's own fingerprint of whatever live inputs it samples, so a
 * change to one of those rebuilds without any setting moving.
 *
 * <p>The view is asked for that fingerprint rather than this naming the signals behind it, which is
 * what keeps the shared pipeline free of any one view's vocabulary: a view that samples nothing
 * contributes a constant and is never churned by a change it does not render.
 *
 * @param settingsRevision    KMU's LunaLib settings revision
 * @param viewId              the ID of the view being painted
 * @param viewContentRevision the view's fingerprint of its live inputs, off this sector's board
 */
record ViewKey(
    int settingsRevision,
    String viewId,
    int viewContentRevision) {
}
