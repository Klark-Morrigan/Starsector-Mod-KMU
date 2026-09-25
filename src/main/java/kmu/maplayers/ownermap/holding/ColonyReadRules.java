package kmu.maplayers.ownermap.holding;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;

import java.util.Objects;

/**
 * What an owner-painted layer reads a colony set under: what the player may be shown of a colony,
 * and what a decivilised world counts as once shown.
 *
 * <p>Two different questions, carried as one value because every pass spends both and a caller
 * holding one has no business resolving the other for itself. A surface handed the visibility rule
 * and left to assume the habitation one would decide, out of sight of the player, whether a
 * decivilised world paints a fill - and a signature taking the two in a row would say nothing
 * about which of them a call site had thought about.
 *
 * <p>Paired here rather than inside {@link ColonyVisibility}, which is shared with the pass
 * deciding whether a system appears on the map at all. That pass has no use for a habitation
 * position and would ignore one, and a rule living in a bundle one reader honours while another
 * ignores it is worse than no bundle.
 *
 * <p>The two parts are two different types, so they cannot be transposed at a call site, and
 * neither has a default: a rule is resolved where a rebuild begins, from values the opener already
 * holds, so an unstated one is that resolve having gone wrong rather than a caller with nothing to
 * say.
 *
 * @param colonyVisibility            what lifts the fog over a colony the player has not found,
 *                                    and the gates holding back what a bare fog would leak
 * @param decivilisedColonyHabitation whether a decivilised world amounts to somebody living in its
 *                                    system
 */
public record ColonyReadRules(
    ColonyVisibility colonyVisibility,
    DecivilisedColonyHabitation decivilisedColonyHabitation) {

    public ColonyReadRules {
        Objects.requireNonNull(colonyVisibility, "colonyVisibility");
        Objects.requireNonNull(decivilisedColonyHabitation, "decivilisedColonyHabitation");
    }
}
