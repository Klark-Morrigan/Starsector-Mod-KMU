package kmu.maplayers.base.render;

import kmlib.logging.SessionWarning;

import org.apache.log4j.Logger;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Says once a session that the map layers were drawn by something that is not a map.
 *
 * <p>{@code renderOnMap} is the sector map's hook by contract, and everything the map-layer frame
 * does rests on that: the cursor read inverts whichever transform the running pass has bound, and
 * divides by the {@code factor} that pass supplied. Both are only meaningful if the pass really is
 * a map's. Nothing enforces it, though - the hook is a plain method on a terrain plugin, and any
 * mod that walks the sector's terrain can call it with a transform and a zoom of its own.
 *
 * <p>What that costs is not a crash but a quiet wrongness: a cursor pixel inverted against a
 * foreign transform still yields a world point, and in hyperspace - where campaign coordinates are
 * the sector map's own coordinates - that point lands inside real cells. The map layers then light
 * and announce systems the pointer is nowhere near, with no map open at all. Nothing about that
 * reads as a transform problem to whoever hears it, which is why it is worth a line naming the
 * pass rather than leaving each report to be diagnosed from the symptom.
 *
 * <p>The stack is the whole value of the line, since the caller is exactly what cannot be worked
 * out from inside the pass. It rides in on a throwable that was never thrown and never will be -
 * purely a carrier for the frames, not a failure being reported.
 *
 * <p>Once per session, at WARN. Once because this sits in a per-frame path and a repeat says
 * nothing the first did not; at WARN because a line the default log level swallows is not a
 * warning. The presence read is asked only while the warning is still owed, that read being a walk
 * of the live widget tree.
 *
 * <p>Never throws. It is a report about the frame and not part of drawing one, so a read that
 * cannot be made costs the report and nothing else - the overlay keeps painting. A failed read
 * spends the warning too, so it is said once rather than retried every frame for the session.
 */
public final class ForeignMapPassWarning {

    // What the live tree holds around any embedded map, or null when it could not be read. Supplied
    // rather than called for here, for the reason the presence read is: both walk a widget tree
    // that only exists in a running game, so a class that reached for either directly would work
    // only inside one.
    private final Supplier<String> describeEmbeddedMapHosts;

    // Whether a map is on screen at all, as a supplied read rather than one composed here: the
    // hosts a map can be showing on are not this class's knowledge, and a supplied read is the
    // caller's to answer without this class holding a widget tree to walk.
    private final BooleanSupplier isAnyMapShowing;

    private final SessionWarning warning;

    /**
     * @param isAnyMapShowing          whether either host is showing a map, from
     *                                 {@code MapPresence#isAnyMapShowing}
     * @param describeEmbeddedMapHosts what the live tree holds around any map embedded outside
     *                                 those hosts, from
     *                                 {@code EmbeddedMapHostTrace#describeEmbeddedMapHosts} -
     *                                 asked only when a line is owed, being a full tree walk
     * @param logger                   the calling surface's own logger, so the line is attributed
     *                                 to the surface that was drawn rather than to this reporter
     */
    public ForeignMapPassWarning(
            BooleanSupplier isAnyMapShowing,
            Supplier<String> describeEmbeddedMapHosts,
            Logger logger) {

        this.isAnyMapShowing = isAnyMapShowing;
        this.describeEmbeddedMapHosts = describeEmbeddedMapHosts;
        this.warning = new SessionWarning(logger);
    }

    /**
     * Reports this pass, if no map is on screen and nothing has been reported yet this session.
     *
     * <p>Call at the top of a map-layer render pass, before anything is resolved or drawn, so the
     * stack is the one that actually reached the surface.
     */
    public void warnOnceIfNoMapIsShowing() {

        // Asked before the read, not after, because the read is the expensive half: with the line
        // already said there is nothing a widget walk could tell us that would be printed.
        if (warning.hasWarnedThisSession()) {
            return;
        }
        try {
            if (isAnyMapShowing.getAsBoolean()) {
                return;
            }
            // The tree read, not the stack, is what can name an owner: a mod builds its widget once
            // and the engine renders it forever after, so the frames below are all the engine's.
            // Null when the tree could not be walked, which the line says rather than hides.
            var embeddedMapHosts = describeEmbeddedMapHosts.get();

            warning.warnOnce(
                "KMU map layers rendered on a pass with no map on screen. The map layers cannot"
                    + " trust that pass's transform, so hover may resolve cells the pointer is not"
                    + " over. Any mod-owned class named below owns the widget that drove it; "
                    + (embeddedMapHosts == null ? "the tree could not be walked" : embeddedMapHosts),
                new Throwable("Stack of the pass that drew the map layers"));

        } catch (Throwable presenceReadFailed) {
            // The read walks the live widget tree and reaches core classes, neither of which every
            // game build is obliged to present. Letting that out would take the overlay away over a
            // line of diagnostics, which inverts what the two are worth.
            warning.warnOnce(
                "KMU map layers could not read whether a map is on screen; the foreign-pass warning"
                    + " is off for the rest of this session.",
                presenceReadFailed);
        }
    }
}
