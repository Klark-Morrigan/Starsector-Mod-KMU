package kmu.maplayers.ownermap.owners;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;

import java.util.Objects;

/**
 * Who owns one system and how its fill draws inside that owner's border: the per-system answer of
 * an owner source, as {@link ResolvedOwners} is the whole-sector one.
 *
 * <p>The fill state travels with the owner because the two are decided together. A rule that leaves
 * a system unfilled or hatched decides so in the same breath as it names the owner, so a batch
 * re-deriving one system has to fold both: folding the owner alone would leave the system drawn in
 * whatever state the last rebuild gave it.
 *
 * <p>A system nobody owns fills solid, there being no owner's border for any other state to draw
 * inside. The constructor refuses the other combination.
 *
 * @param owner     who owns the system, or null where nobody does and the cell draws unowned
 * @param fillState how the system fills inside its owner's border; solid for a system nobody owns
 */
public record ResolvedSystemOwner(
    SystemOwner owner,
    FillState fillState) {

    /** The answer for a system nobody owns. */
    public static final ResolvedSystemOwner UNOWNED = new ResolvedSystemOwner(null, FillState.SOLID);

    public ResolvedSystemOwner {

        Objects.requireNonNull(fillState, "fillState");

        if (owner == null && fillState != FillState.SOLID) {
            throw new IllegalArgumentException("A system nobody owns fills solid, not " + fillState);
        }
    }

    /**
     * The answer for a rule that fills every system it gives anybody solid.
     *
     * @param owner who owns the system, or null where nobody does
     * @return the owner filling solid, or {@link #UNOWNED} for a null owner
     */
    public static ResolvedSystemOwner createSolidlyFilledBy(SystemOwner owner) {

        return owner == null
            ? UNOWNED
            : new ResolvedSystemOwner(owner, FillState.SOLID);
    }
}
