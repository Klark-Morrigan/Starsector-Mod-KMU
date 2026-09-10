package kmu.maplayers.base.visibility.structures;

/**
 * A way a structure can be out of action, as somebody observing it would find it.
 *
 * <p>A set of these rather than a flag per state, because they are one axis: what was established
 * about whether the structure works. Carried as separate booleans they were two arguments of the
 * same type standing side by side, which a caller can swap without the compiler or a codec noticing
 * - and each further state cost a field, an argument at every construction, and a branch wherever
 * the set is spelt out.
 *
 * <p>Both are read off the structure and neither is a claim about who put it in that state.
 */
public enum StructureFault {

    /** Knocked out by a factory reset - a lapsing state somebody did to it. */
    DISRUPTED,

    /** Out of action of its own accord: built broken, or never repaired since. */
    NON_FUNCTIONAL
}
