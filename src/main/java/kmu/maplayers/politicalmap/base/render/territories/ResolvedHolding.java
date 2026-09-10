package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.politicalmap.base.politics.holders.HolderResolution;

import java.util.Set;

/**
 * Everything a build learns about the sector before it shapes a cell: who holds each system, which
 * systems anything stands in, and where the spotlit bloc lives among the settled systems nobody
 * holds.
 *
 * <p>One value because the three are one reading of the sector - each scan walks every system, and
 * the second and third are asked of what the first left out - and because they are what a rebuild
 * is allowed to keep. A style pick moves nothing in the sector, so a rebuild it owes can paint over
 * the holding the previous one resolved instead of walking the economy again for an answer it
 * already holds. What it may not keep across is a sector that moved, which is the caller's to know:
 * this carries no record of what it was resolved under.
 *
 * <p>Immutable, its collections being the resolve's own answers, so a build that adopts it copies
 * what it needs and the value can be handed to the next one untouched.
 *
 * @param resolution               who paints each system and which owned systems draw as a fill
 *                                 exception
 * @param inhabitedSystemIds       every system something stands in, whoever holds it
 * @param spotlitPresenceSystemIds the settled systems the spotlit bloc lives in that no holder was
 *                                 resolved for; empty off filter
 */
public record ResolvedHolding(
    HolderResolution resolution,
    Set<String> inhabitedSystemIds,
    Set<String> spotlitPresenceSystemIds) {
}
