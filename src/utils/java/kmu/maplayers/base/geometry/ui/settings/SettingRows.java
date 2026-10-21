package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.ColourRows;
import kmu.desktop.ui.swing.ControlRows;
import kmu.desktop.ui.swing.SliderRows;
import kmu.desktop.ui.swing.ToggleTree;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.Arrays;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

/**
 * The row shapes every section builds, each bound to one refresh.
 *
 * <p>One instance per refresh rather than a refresh chosen per row, because the binding is the
 * point: a knob built from the rebuilding rows rebuilds, and one built from the repainting rows
 * repaints. The refresh runs once per change a reader makes and never while a row is being
 * built, so no knob's own {@code apply} has a refresh in it - an {@code apply} runs on build as
 * well, and a refresh written there would fire with every row the panel lays out.
 *
 * <p>A slider bound to a cheap refresh runs it on every movement, and one bound to an expensive
 * refresh runs it once the handle is let go - so dragging a colour's opacity repaints as it
 * moves, and dragging a reach rebuilds the sector once.
 */
final class SettingRows {

    private static final Runnable DO_NOTHING = () -> { };

    private final ViewerSettings settings;

    private final Runnable refresh;

    private final SliderRows.SliderWork sliderWork;

    private SettingRows(ViewerSettings settings, Runnable refresh, SliderRows.SliderWork sliderWork) {

        this.settings = settings;
        this.refresh = refresh;
        this.sliderWork = sliderWork;
    }

    /**
     * Rows whose refresh is cheap enough to run on every movement of a slider.
     *
     * @param settings what the rows write to
     * @param repaint  the cheap refresh
     * @return the rows
     */
    static SettingRows createRepaintingRows(ViewerSettings settings, Runnable repaint) {
        return new SettingRows(settings, repaint, new SliderRows.SliderWork(repaint, DO_NOTHING));
    }

    /**
     * Rows whose refresh runs once a slider settles.
     *
     * @param settings what the rows write to
     * @param refresh  the expensive refresh
     * @return the rows
     */
    static SettingRows createSettlingRows(ViewerSettings settings, Runnable refresh) {
        return new SettingRows(settings, refresh, new SliderRows.SliderWork(DO_NOTHING, refresh));
    }

    // A column for one section to hold, filled by whichever run of rows it is the section for.
    // The rows add themselves to whatever they are handed, so a section body is only the
    // container plus the layout the panel itself uses.
    static JPanel buildSectionBody(Consumer<JPanel> addRows) {

        var body = new JPanel();

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        addRows.accept(body);

        return body;
    }

    JPanel buildColour(ColourKnob knob) {
        return ColourRows.buildColour(knob.title(), knob.bindTo(settings), refresh);
    }

    JPanel buildColourPair(ColourPairKnob knob) {
        return ColourRows.buildColourPair(
            knob.title(), knob.bindFill(settings), knob.bindEdge(settings), refresh);
    }

    <T> JPanel buildRadio(RadioKnob<T> knob) {
        return ControlRows.buildRadio(knob.title(), knob.picks(), knob.bindTo(settings), refresh);
    }

    JPanel buildSlider(SliderKnob knob) {
        return SliderRows.buildSlider(buildSliderSpec(knob));
    }

    JPanel buildSliderPair(SliderKnob left, SliderKnob right) {
        return SliderRows.buildSliderPair(buildSliderSpec(left), buildSliderSpec(right));
    }

    // A section whose heading is a switch over the whole run, refreshed by this binding.
    JPanel buildSwitchedSection(String sectionName, SwitchKnob master, JPanel body) {

        return CollapsibleSection.buildSection(
            sectionName,
            master.title(),
            new CollapsibleSection.MasterSwitch(master.bindTo(settings), refresh),
            body);
    }

    JPanel buildToggle(SwitchKnob knob) {
        return ControlRows.buildToggle(knob.title(), knob.bindTo(settings), refresh);
    }

    JPanel buildToggleRow(SwitchKnob... knobs) {

        var toggles = Arrays
            .stream(knobs)
            .map(knob -> new ControlRows.Toggle(knob.title(), knob.bindTo(settings)))
            .toArray(ControlRows.Toggle[]::new);

        return ControlRows.buildToggleRow(refresh, toggles);
    }

    JPanel buildToggleTree(ToggleTree.Row... rows) {
        return ToggleTree.buildToggleTree(refresh, rows);
    }

    private SliderRows.SliderSpec buildSliderSpec(SliderKnob knob) {

        return new SliderRows.SliderSpec(
            knob.title(), knob.range(), knob.bindTo(settings), sliderWork);
    }
}
