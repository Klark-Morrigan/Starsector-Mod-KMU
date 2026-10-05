package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;

import kmlib.starsector.compatibility.CompatibilityConsumer;
import kmlib.starsector.compatibility.CompatibilityFailures;
import kmlib.starsector.ui.compatibility.ScreenCompatibilityNotices;

import kmu.maplayers.base.layer.MapLayer;
import kmu.util.KmuCompatibilityConsumers;
import kmu.util.KmuStringKeys;

import org.apache.log4j.Logger;

import java.util.function.Predicate;

/**
 * Tells the player that a map layer threw and was switched off.
 *
 * <p>Without it the layer simply stops drawing, and the only trace is in the log. The player sees an
 * empty map and has no reason to look there. So the switch-off goes through KMLib's notice, the same
 * one a broken binding to another mod uses: on the map at once where the map can hold it, and as the
 * campaign's dialog otherwise.
 *
 * <p>Reporting is guarded on its own. Composing the report reads strings and builds a consumer, and
 * either can fail for the same reason the layer did. A report that fails is logged beside the
 * failure it was for, and the layer stays off either way.
 */
final class SwitchedOffLayerReporter {

    private static final Logger LOG = Global.getLogger(SwitchedOffLayerReporter.class);

    // Leads every layer's feature key, so a report names the layer as one of KMU's map layers rather
    // than as a bare ID.
    private static final String FEATURE_KEY_PREFIX = "map-layer-";

    private final CompatibilityFailures failureRecord;

    // Raises the notice on the screen in force, answering whether it did. Taken rather than called
    // directly because that read walks the live widget tree.
    private final Predicate<CompatibilityFailures> raiseNoticeOnScreen;

    /**
     * @param failureRecord       where the switch-off is recorded
     * @param raiseNoticeOnScreen raises the oldest unreported failure on the screen in force
     */
    SwitchedOffLayerReporter(
            CompatibilityFailures failureRecord,
            Predicate<CompatibilityFailures> raiseNoticeOnScreen) {

        this.failureRecord = failureRecord;
        this.raiseNoticeOnScreen = raiseNoticeOnScreen;
    }

    /**
     * @return the reporter a running game uses: the session's record, raised on the map when it is up
     */
    static SwitchedOffLayerReporter createForSession() {

        return new SwitchedOffLayerReporter(
            CompatibilityFailures.SESSION_RECORD,
            ScreenCompatibilityNotices::showPendingFailureOnScreen);
    }

    /**
     * Records the switch-off once for the session and raises the notice where the screen can hold
     * it. Never throws.
     *
     * @param switchedOffLayer the layer that threw
     * @param cause            what it threw
     */
    void reportLayerSwitchedOff(MapLayer switchedOffLayer, Throwable cause) {

        try {
            failureRecord.recordFeatureFailureOnce(describeConsumerOf(switchedOffLayer), cause);
            raiseNoticeOnScreen.test(failureRecord);

        } catch (LinkageError | RuntimeException reportFailure) {

            LOG.error("Map layer '" + switchedOffLayer.getId() + "' was switched off but could not be"
                + " reported to the player. The failure it was for is logged above.", reportFailure);
        }
    }

    // KMU as the mod that lost the layer, naming the layer as the player knows it: by its tab.
    private static CompatibilityConsumer describeConsumerOf(MapLayer switchedOffLayer) {

        return KmuCompatibilityConsumers.describeConsumer(
            FEATURE_KEY_PREFIX + switchedOffLayer.getId(),
            KmuStringKeys.COMPATIBILITY_LOST_MAP_LAYER,
            KmuStringKeys.COMPATIBILITY_UNAFFECTED_MAP_LAYER,
            switchedOffLayer.resolveTabLabelText());
    }
}
