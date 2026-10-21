package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.ControlRows;
import kmu.maplayers.base.geometry.EdgeInsetRule;

import java.util.List;

/**
 * The three answers to which edges take the border channel, as every inset radio offers them.
 *
 * <p>One list for the cells' radio and the void's, so a reader setting both constructions to one
 * answer picks the same words twice rather than translating between two vocabularies.
 */
final class EdgeInsetPicks {

    static final List<ControlRows.Pick<EdgeInsetRule>> INSET_RULE_PICKS = List.of(
        new ControlRows.Pick<>("By ownership", EdgeInsetRule.AT_EVERY_BORDER),
        new ControlRows.Pick<>("None", EdgeInsetRule.NOWHERE),
        new ControlRows.Pick<>("All", EdgeInsetRule.EVERYWHERE));

    private EdgeInsetPicks() {
    }
}
