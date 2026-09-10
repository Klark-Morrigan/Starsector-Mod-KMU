package kmu.maplayers.base.layer;

import kmu.maplayers.base.machinery.InstalledMachinery;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which layers are standing on one sector, and so what a change to the player's arrangement has to
 * act on: an id entering the hidden list is a layer to take back only if it was up, and one leaving
 * it is a layer to stand up only if it was not.
 *
 * <p>Held per machinery rather than per layer, because standing is a fact about a layer
 * <em>on a sector</em>: the same registered layer may be up on one sector and never have been asked
 * about another. It rides in as {@link InstalledMachinery} for the reason a renderer does - the
 * machinery owns the lifetime without naming what it holds.
 *
 * <p>Disposal forgets rather than stands anything down. What machinery is disposed for is a
 * sector the load has already replaced, and everything a layer registers on a sector is transient -
 * so there is nothing left to take back, and a stand-down aimed at it would reach the sector that
 * replaced it.
 *
 * <p>Concurrent for the reason the index holding it is: a settings change and a load both write on
 * the campaign thread while a frame resolves the same machinery on the render thread.
 */
public final class StandingLayers implements InstalledMachinery {

    // The ids of the layers standing on this sector. A set rather than a flag per registered layer,
    // since the roster is not settled at any one moment - a mod may register after this was made.
    private final Set<String> standingLayerIds = ConcurrentHashMap.newKeySet();

    @Override
    public void disposeMachinery() {
        standingLayerIds.clear();
    }

    /**
     * @param layerId the layer's registered id
     * @return whether that layer is standing on this sector
     */
    public boolean isLayerStanding(String layerId) {
        return standingLayerIds.contains(layerId);
    }

    /**
     * Records which side of the standing line one layer is now on.
     *
     * <p>Written where the stand-up or stand-down is asked for rather than once it has returned, so
     * a half that fails leaves the layer recorded as it was asked to be: the way back is the
     * direction that undoes whatever it managed, and repeating the failing call on every later
     * arrangement would only fail again.
     *
     * @param layerId    the layer's registered id
     * @param isStanding whether it is standing on this sector from now on
     */
    public void recordLayerStanding(String layerId, boolean isStanding) {

        if (isStanding) {
            standingLayerIds.add(layerId);
        } else {
            standingLayerIds.remove(layerId);
        }
    }
}
