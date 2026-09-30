package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.ColourRows;
import kmu.desktop.ui.swing.ControlRows;
import kmu.desktop.ui.swing.ToggleTree;
import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.List;

import javax.swing.JPanel;

/**
 * The v4 void construction, beside v3 rather than under it.
 *
 * <p>Each construction with its own tree and its own root. What puts them side by side is that
 * they answer one question two ways: a reader judges v4 by setting v3 down and picking it up
 * again, which wants two switches at one level rather than one pick that can only ever show
 * one of them.
 */
final class VoidV4Section extends PanelSection {

    // v4's pieces of void, and the stretches of cell border facing them. The pieces' key is
    // what a saved window already holds the switch under, so it is kept as it stands rather
    // than renamed with the field it writes.
    private static final String VOID_PIECES = "showBareVoid";

    private static final String LANDABLE_FRONTAGE = "showLandableFrontageV4";

    // The tiers of the stack, one branch each, in the order they go down: a tier's lines, then
    // its fills, then its names, each a switch under the tier's own roll-up. A branch holds
    // only what has landed, and a substep lands into its slot rather than onto the end of a
    // list - so the tree has the shape of the stack from the first tier on, and a reader who
    // learnt it at one tier finds the next in the same place.
    private static final String LAKES_BRANCH = "lakesV4";

    private static final String LAKE_COAST = "showLakeCoastV4";

    private static final String LAKE_BRIDGES = "showLakeBridgesV4";

    // A rule of the bridges rather than a layer, so it sits under them and in no roll-up: a
    // roll-up turning it on with the layers would claim to show something there is nothing
    // separate to see.
    private static final String THIN_LAKE_BRIDGES = "shouldThinLakeBridgesV4";

    // Not a layer, so not in the roll-up above it: it changes how the pieces are drawn rather
    // than whether they are, and a roll-up that turned it on with the layers would claim to have
    // switched on something there is no separate thing to see.
    private static final String VOID_INSET_RULE = "voidInsetRule";

    // Held for the reason the pieces' switch key is: a saved window already has it.
    private static final String VOID_PIECES_COLOUR = "bareVoidColour";

    // v4's own, beside it rather than inside it. The two constructions answer the same question
    // differently, so neither is a branch of the other - and a reader comparing them wants each
    // to fold away whole, which a shared tree cannot offer.
    private static final CollapsibleSection.SectionKeys VOID_V4_KEYS =
        CollapsibleSection.SectionKeys.forSection("voidV4");

    VoidV4Section(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    @Override
    JPanel buildSection() {

        return CollapsibleSection.buildSection(
            VOID_V4_KEYS,
            "Void pockets - v4",
            new CollapsibleSection.MasterSwitch(
                false, on -> settings.showVoidV4 = on, refreshes::refreshVoidV4),
            SettingRows.buildSectionBody(this::addRows));
    }

    // v4's layers, in the same shape v3's are: a tree with a roll-up at its root, the pieces
    // first, then a branch per tier in the order the tiers go down, and the frontage last,
    // after everything it is a diagnostic of.
    //
    // The floor that decides which holes are lakes at all is not here: it sits under Global
    // geometry with the frontage floor, because both constructions are built under it and a
    // dial of v4's own is how the two would come to disagree about what a lake is.
    //
    // The colours sit beside the switches rather than with the cells' colours. Two
    // constructions are compared by their fills, so which colour each is drawn in is part of
    // using this section rather than a decision about the palette.
    private void addRows(JPanel controls) {

        controls.add(ToggleTree.buildToggleTree(
            refreshes::refreshVoidV4,
            ToggleTree.Row.ofRollUp(
                TreeDepths.ROOT, "allVoidV4Layers", "Every v4 layer",
                VOID_PIECES, LAKE_COAST, LAKE_BRIDGES, LANDABLE_FRONTAGE),
            ToggleTree.Row.ofSwitch(TreeDepths.BRANCH, new ToggleTree.Switch(
                VOID_PIECES,
                "Pieces",
                true,
                on -> settings.showVoidPiecesV4 = on)),
            ToggleTree.Row.ofRollUp(TreeDepths.BRANCH, LAKES_BRANCH, "Lakes", LAKE_COAST, LAKE_BRIDGES),
            ToggleTree.Row.ofSwitch(TreeDepths.LEAF, new ToggleTree.Switch(
                LAKE_COAST,
                "Coast",
                false,
                on -> settings.showLakeCoastV4 = on)),
            ToggleTree.Row.ofSwitch(TreeDepths.LEAF, new ToggleTree.Switch(
                LAKE_BRIDGES,
                "Bridges",
                false,
                on -> settings.showLakeBridgesV4 = on)),
            ToggleTree.Row.ofSwitch(TreeDepths.RULE, new ToggleTree.Switch(
                THIN_LAKE_BRIDGES,
                "Thin shared-anchor bridges",
                true,
                on -> settings.shouldThinLakeBridgesV4 = on)),
            ToggleTree.Row.ofSwitch(TreeDepths.BRANCH, new ToggleTree.Switch(
                LANDABLE_FRONTAGE,
                "Landable frontage",
                false,
                on -> settings.showLandableFrontageV4 = on))));

        // Under the pieces it acts on, rather than with the colours: it is the same layer
        // drawn another way, and a reader comparing true against inset reaches for it while
        // reading that layer.
        //
        // The same three picks in the same words the cells' own radio offers, so a reader
        // setting both constructions to one answer is picking the same thing twice rather than
        // translating between two vocabularies.
        controls.add(ControlRows.buildRadio(
            VOID_INSET_RULE,
            "Void insets",
            List.of(
                new ControlRows.Pick<>("By ownership", EdgeInsetRule.AT_EVERY_BORDER),
                new ControlRows.Pick<>("None", EdgeInsetRule.NOWHERE),
                new ControlRows.Pick<>("All", EdgeInsetRule.EVERYWHERE)),
            rule -> settings.voidInsetRule = rule,
            refreshes::refreshVoidV4));

        controls.add(ColourRows.buildColour(
            VOID_PIECES_COLOUR,
            "Pieces",
            ViewerSettings.VOID_PIECES_V4_DEFAULT,
            colour -> settings.voidPiecesV4Colour = colour,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColour(
            "lakeCoastV4Colour",
            "Lake coast reaches",
            ViewerSettings.LAKE_COAST_V4_DEFAULT,
            colour -> settings.lakeCoastV4Colour = colour,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColour(
            "lakeBridgesV4Colour",
            "Lake bridges",
            ViewerSettings.LAKE_BRIDGES_V4_DEFAULT,
            colour -> settings.lakeBridgesV4Colour = colour,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColour(
            "landableFrontageV4Colour",
            "Landable frontage",
            ViewerSettings.LANDABLE_FRONTAGE_DEFAULT,
            colour -> settings.landableFrontageV4Colour = colour,
            refreshes::repaintMap));
    }
}
