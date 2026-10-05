package kmu.maplayers.ownermap.owners;

import kmlib.starsector.systems.SystemKey;

import java.util.Map;
import java.util.Set;

/**
 * Everything a build learns about the sector before it shapes a cell, as the layer's owner source
 * answers it: who owns each system and which owned systems draw as a fill exception, which
 * systems anything stands in, and where the spotlit owner lives among the systems nobody owns.
 *
 * <p>One value because the five are one reading of the sector, and because they are what a
 * rebuild is allowed to keep. A style pick moves nothing in the sector, so a rebuild it owes can
 * paint over the owners the previous one resolved instead of asking the source again for an answer
 * it already holds. What it may not keep across is a sector that moved, which is the caller's to
 * know: this carries no record of what it was resolved under.
 *
 * <p>The two exception sets ride beside the owner map rather than being resolved through a second
 * seam, because they are co-produced with it: a system's owner and how its fill is drawn are
 * decided together. The inhabited set is independent of the owner map, deliberately: the map says
 * who this layer gives a system to, and a layer whose rule gives nobody a settled system leaves it
 * unowned for reasons of its own - only this set tells such a system from empty space.
 *
 * <p>Immutable, its collections being the source's own answers, so a build that adopts it copies
 * what it needs and the value can be handed to the next one untouched.
 *
 * @param ownerBySystemKey          the owner of each owned system, keyed by {@link SystemKey}; a
 *                                  system nobody owns is absent, so it draws as an unowned cell
 * @param contestedSystemKeys       the owned systems drawn hatched rather than solid - under a
 *                                  spotlight, the systems the spotlit owner is present in while
 *                                  another owns them; empty when the whole resolution fills solid
 * @param unfilledSystemKeys        the owned systems drawn with no fill rather than solid - owned
 *                                  for border and label, but painting nothing inside the one
 *                                  frontier; empty when the whole resolution fills solid
 * @param inhabitedSystemKeys       every system something stands in, whoever owns it
 * @param spotlitPresenceSystemKeys the inhabited systems the spotlit owner lives in that nobody
 *                                  owns; empty off spotlight
 */
public record ResolvedOwners(
    Map<SystemKey, SystemOwner> ownerBySystemKey,
    Set<SystemKey> contestedSystemKeys,
    Set<SystemKey> unfilledSystemKeys,
    Set<SystemKey> inhabitedSystemKeys,
    Set<SystemKey> spotlitPresenceSystemKeys) {

    /**
     * The owners a source with nothing to say answers: nobody owns anything, nothing stands
     * anywhere, and no pick lives anywhere.
     *
     * @return the empty resolution
     */
    public static ResolvedOwners createEmpty() {
        return new ResolvedOwners(Map.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }
}
