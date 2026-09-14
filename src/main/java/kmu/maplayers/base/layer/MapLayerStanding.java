package kmu.maplayers.base.layer;

import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * What one layer needs of a sector while its tab is on the bar, and how to take it back.
 *
 * <p>A layer is registered once for the process, while its listeners, its polls and its save heals
 * are one sector's - so a layer states the pair rather than standing anything up itself, and
 * {@link MapLayerStandings} decides when each half is owed. That is what lets a tab the player took
 * off the bar stop costing: the roster still carries the layer, and nothing of it is running.
 *
 * <p>Both halves are reached with the sector they concern rather than resolving one, for the reason
 * every installer here is: a layer stood up on the sector a load handed down must not take its
 * listeners back off whichever sector happens to be loaded when the player rearranges their bar.
 *
 * <p>A pair is asked for repeatedly and is expected to be idempotent in each direction: standing up
 * what already stands leaves one registration, and standing down what never stood is a no-op.
 */
public interface MapLayerStanding {

    /**
     * Stands this layer up on one sector: whatever it needs registered, healed or polling while its
     * tab is on the bar.
     *
     * @param sector the sector to stand up on
     */
    void standLayerUpOn(SectorAPI sector);

    /**
     * Takes all of that back, so a layer nobody can reach is not still listening, polling or
     * marking an overlay stale that nothing draws.
     *
     * @param sector the sector to stand down from
     */
    void standLayerDownFrom(SectorAPI sector);
}
