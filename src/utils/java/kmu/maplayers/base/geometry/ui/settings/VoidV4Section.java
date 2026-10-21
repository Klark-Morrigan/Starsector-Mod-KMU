package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.ToggleTree;
import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.ArrayList;
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
final class VoidV4Section
        extends PanelSection {

    // v4's own, beside v3 rather than inside it. The two constructions answer the same question
    // differently, so neither is a branch of the other - and a reader comparing them wants each
    // to fold away whole, which a shared tree cannot offer.
    private static final String SECTION_NAME = "voidV4";

    private static final SwitchKnob MASTER = new SwitchKnob(
        CollapsibleSection.SectionKeys.forSection(SECTION_NAME).switchKey(),
        "Void pockets - v4",
        false,
        (settings, isOn) -> settings.showVoidV4 = isOn);

    // v4's pieces of void. Remembered under the key saved windows already hold it by, which is
    // why it does not read like the field it writes.
    private static final SwitchKnob VOID_PIECES = new SwitchKnob(
        "showBareVoid",
        "Pieces",
        true,
        (settings, isOn) -> settings.showVoidPiecesV4 = isOn);

    // The lake tier, in the order its branch lists it: a tier's lines, then its fills, then its
    // names, each a switch under the tier's own roll-up.
    private static final SwitchKnob LAKE_COAST = new SwitchKnob(
        "showLakeCoastV4",
        "Coast",
        false,
        (settings, isOn) -> settings.showLakeCoastV4 = isOn);

    private static final SwitchKnob LAKE_BRIDGES = new SwitchKnob(
        "showLakeBridgesV4",
        "Bridges",
        false,
        (settings, isOn) -> settings.showLakeBridgesV4 = isOn);

    // A rule of the bridges rather than a layer, so it sits under them and in no roll-up: a
    // roll-up turning it on with the layers would claim to show something there is nothing
    // separate to see.
    private static final SwitchKnob THIN_LAKE_BRIDGES = new SwitchKnob(
        "shouldThinLakeBridgesV4",
        "Thin shared-anchor bridges",
        true,
        (settings, isOn) -> settings.shouldThinLakeBridgesV4 = isOn);

    private static final SwitchKnob LAKE_WATER = new SwitchKnob(
        "showLakeWaterV4",
        "Water",
        false,
        (settings, isOn) -> settings.showLakeWaterV4 = isOn);

    private static final SwitchKnob LAKE_MARGIN = new SwitchKnob(
        "showLakeMarginV4",
        "Margin",
        false,
        (settings, isOn) -> settings.showLakeMarginV4 = isOn);

    private static final SwitchKnob LAKE_NAMES = new SwitchKnob(
        "showLakeNamesV4",
        "Names",
        false,
        (settings, isOn) -> settings.showLakeNamesV4 = isOn);

    // Every layer of the lake tier, in the order its branch lists them: what both the branch's
    // roll-up and the root's cover. Named once, so a key added to the branch cannot be left out
    // of the root, where it would silently go unswitched.
    private static final List<SwitchKnob> LAKE_LAYERS =
        List.of(LAKE_COAST, LAKE_BRIDGES, LAKE_WATER, LAKE_MARGIN, LAKE_NAMES);

    private static final SwitchKnob LANDABLE_FRONTAGE = new SwitchKnob(
        "showLandableFrontageV4",
        "Landable frontage",
        false,
        (settings, isOn) -> settings.showLandableFrontageV4 = isOn);

    // NOWHERE to open on, the default the settings document: the true partition is what is
    // being judged, and a window opening on the inset would show it already dressed.
    private static final RadioKnob<EdgeInsetRule> VOID_INSET_RULE = new RadioKnob<>(
        "voidInsetRule",
        "Void insets",
        EdgeInsetPicks.INSET_RULE_PICKS,
        EdgeInsetRule.NOWHERE,
        (settings, rule) -> settings.voidInsetRule = rule);

    // Held under the key a saved window already has, for the reason the pieces' switch is.
    private static final ColourKnob VOID_PIECES_COLOUR = new ColourKnob(
        "bareVoidColour",
        "Pieces",
        ViewerSettings.VOID_PIECES_V4_DEFAULT,
        (settings, colour) -> settings.voidPiecesV4Colour = colour);

    private static final ColourKnob LAKE_COAST_COLOUR = new ColourKnob(
        "lakeCoastV4Colour",
        "Lake coast reaches",
        ViewerSettings.LAKE_COAST_V4_DEFAULT,
        (settings, colour) -> settings.lakeCoastV4Colour = colour);

    private static final ColourKnob LAKE_BRIDGES_COLOUR = new ColourKnob(
        "lakeBridgesV4Colour",
        "Lake bridges",
        ViewerSettings.LAKE_BRIDGES_V4_DEFAULT,
        (settings, colour) -> settings.lakeBridgesV4Colour = colour);

    private static final ColourKnob LAKE_WATER_COLOUR = new ColourKnob(
        "lakeWaterV4Colour",
        "Lake water",
        ViewerSettings.LAKE_WATER_V4_DEFAULT,
        (settings, colour) -> settings.lakeWaterV4Colour = colour);

    private static final ColourKnob LAKE_MARGIN_COLOUR = new ColourKnob(
        "lakeMarginV4Colour",
        "Lake margin",
        ViewerSettings.LAKE_MARGIN_V4_DEFAULT,
        (settings, colour) -> settings.lakeMarginV4Colour = colour);

    private static final ColourKnob LANDABLE_FRONTAGE_COLOUR = new ColourKnob(
        "landableFrontageV4Colour",
        "Landable frontage",
        ViewerSettings.LANDABLE_FRONTAGE_DEFAULT,
        (settings, colour) -> settings.landableFrontageV4Colour = colour);

    VoidV4Section(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    /**
     * @return every setting this section offers
     */
    static List<ViewerKnob> listKnobs() {

        return List.of(
            MASTER,
            VOID_PIECES,
            LAKE_COAST,
            LAKE_BRIDGES,
            THIN_LAKE_BRIDGES,
            LAKE_WATER,
            LAKE_MARGIN,
            LAKE_NAMES,
            LANDABLE_FRONTAGE,
            VOID_INSET_RULE,
            VOID_PIECES_COLOUR,
            LAKE_COAST_COLOUR,
            LAKE_BRIDGES_COLOUR,
            LAKE_WATER_COLOUR,
            LAKE_MARGIN_COLOUR,
            LANDABLE_FRONTAGE_COLOUR);
    }

    @Override
    JPanel buildSection() {

        return voidV4Rows.buildSwitchedSection(
            SECTION_NAME, MASTER, SettingRows.buildSectionBody(this::addRows));
    }

    // What a roll-up covers, by the keys its switches are remembered under.
    private static String[] listKeys(List<SwitchKnob> knobs) {
        return knobs.stream().map(SwitchKnob::key).toArray(String[]::new);
    }

    // What the root's roll-up covers: every layer of the tree, in the tree's order. Not the
    // rules, which change how a layer is laid rather than whether it is shown.
    private static String[] listEveryLayerKey() {

        var layers = new ArrayList<SwitchKnob>();

        layers.add(VOID_PIECES);
        layers.addAll(LAKE_LAYERS);
        layers.add(LANDABLE_FRONTAGE);

        return listKeys(layers);
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

        controls.add(voidV4Rows.buildToggleTree(
            ToggleTree.Row.ofRollUp(
                TreeDepths.ROOT, "allVoidV4Layers", "Every v4 layer", listEveryLayerKey()),
            ToggleTree.Row.ofSwitch(TreeDepths.BRANCH, VOID_PIECES.bindSwitch(settings)),
            ToggleTree.Row.ofRollUp(
                TreeDepths.BRANCH, "lakesV4", "Lakes", listKeys(LAKE_LAYERS)),
            ToggleTree.Row.ofSwitch(TreeDepths.LEAF, LAKE_COAST.bindSwitch(settings)),
            ToggleTree.Row.ofSwitch(TreeDepths.LEAF, LAKE_BRIDGES.bindSwitch(settings)),
            ToggleTree.Row.ofSwitch(TreeDepths.RULE, THIN_LAKE_BRIDGES.bindSwitch(settings)),

            // The fills after the lines, paired as v3's fill and names are: the two halves of
            // one lake share a line, and the names sit under them.
            ToggleTree.Row.ofSwitchPair(
                TreeDepths.LEAF, LAKE_WATER.bindSwitch(settings), LAKE_MARGIN.bindSwitch(settings)),
            ToggleTree.Row.ofSwitch(TreeDepths.LEAF, LAKE_NAMES.bindSwitch(settings)),
            ToggleTree.Row.ofSwitch(TreeDepths.BRANCH, LANDABLE_FRONTAGE.bindSwitch(settings))));

        // Under the pieces it acts on, rather than with the colours: it is the same layer drawn
        // another way, and a reader comparing true against inset reaches for it while reading
        // that layer.
        controls.add(voidV4Rows.buildRadio(VOID_INSET_RULE));

        controls.add(repaintRows.buildColour(VOID_PIECES_COLOUR));
        controls.add(repaintRows.buildColour(LAKE_COAST_COLOUR));
        controls.add(repaintRows.buildColour(LAKE_BRIDGES_COLOUR));
        controls.add(repaintRows.buildColour(LAKE_WATER_COLOUR));
        controls.add(repaintRows.buildColour(LAKE_MARGIN_COLOUR));
        controls.add(repaintRows.buildColour(LANDABLE_FRONTAGE_COLOUR));
    }
}
