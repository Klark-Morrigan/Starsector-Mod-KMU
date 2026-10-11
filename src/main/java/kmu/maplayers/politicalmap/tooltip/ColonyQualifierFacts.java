package kmu.maplayers.politicalmap.tooltip;

import kmu.maplayers.base.visibility.colonies.ColonyKind;

import java.util.Objects;

/**
 * What a box has found out about one colony that its own arithmetic does not state: what sort of
 * place it is, anything its layer finds first, and how it is out of plain view.
 *
 * <p>The colony's name is not among them, although one rule reads it: the line already carries it,
 * so a name here would be a second copy of the label a caller had just built the line from - free
 * to name a different colony than the line does.
 *
 * @param kind              what sort of place the colony is; null states nothing, an unstated kind
 *                          being no grounds for a finding
 * @param leadingFinding    a finding the painting layer states ahead of every other, already
 *                          worded for the reader - what its own mechanic makes of this colony;
 *                          null states none
 * @param concealment       how the colony is out of plain view, if it is
 * @param isListedByEconomy whether the economy's own set holds this colony, as the walk that
 *                          selected it decided
 */
record ColonyQualifierFacts(
    ColonyKind kind,
    String leadingFinding,
    ColonyConcealment concealment,
    boolean isListedByEconomy) {

    ColonyQualifierFacts {
        Objects.requireNonNull(concealment, "concealment");
    }
}
