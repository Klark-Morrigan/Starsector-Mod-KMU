package kmu.util;

import kmlib.starsector.compatibility.CompatibilityConsumer;

import kmu.KmuMod;

/**
 * KMU as the mod a failed binding costs something: the one way KMU names itself to KMLib's
 * compatibility channel.
 *
 * <p>Every binding KMU takes - a third party's settings, a renderer's internals, the game's own
 * screens - files under KMU's mod ID with a feature key and two sentences out of KMU's strings.
 * Composed once here, so no binding can name the mod differently or read one of its sentences from
 * the other's key.
 *
 * <p>Final class with a private constructor: pure-function utility, no instances.
 */
public final class KmuCompatibilityConsumers {

    private KmuCompatibilityConsumers() {
        // utility class, no instances.
    }

    /**
     * KMU as the consumer of one binding, with its sentences read now.
     *
     * <p>Reads strings.json, so a caller on a path where the binding holds composes this only once
     * the binding has failed.
     *
     * @param featureKey    which of KMU's features the binding serves
     * @param lostKey       the strings.json key of what that feature loses
     * @param unaffectedKey the strings.json key of what it does not
     * @return the consumer a failed binding is filed under
     */
    public static CompatibilityConsumer describeConsumer(
            String featureKey,
            String lostKey,
            String unaffectedKey) {

        return new CompatibilityConsumer(
            KmuMod.MOD_ID,
            featureKey,
            KmuStringKeys.get(lostKey),
            KmuStringKeys.get(unaffectedKey));
    }

    /**
     * The same, where the sentence naming what is lost has slots - one consumer for several
     * features of one kind, each naming itself in that sentence.
     *
     * @param featureKey    which of KMU's features the failure cost
     * @param lostKey       the strings.json key of what that feature loses, a template
     * @param unaffectedKey the strings.json key of what it does not
     * @param lostArguments what fills the lost sentence's slots, in order
     * @return the consumer the failure is filed under
     */
    public static CompatibilityConsumer describeConsumer(
            String featureKey,
            String lostKey,
            String unaffectedKey,
            Object... lostArguments) {

        return new CompatibilityConsumer(
            KmuMod.MOD_ID,
            featureKey,
            KmuStringKeys.format(lostKey, lostArguments),
            KmuStringKeys.get(unaffectedKey));
    }
}
