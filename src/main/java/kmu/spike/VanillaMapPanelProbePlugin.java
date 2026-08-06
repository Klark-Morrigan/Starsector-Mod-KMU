package kmu.spike;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import org.apache.log4j.Logger;

import java.util.List;

/**
 * Records what the vanilla widget tree actually grants a mod-owned panel once that panel has been
 * attached to the map widget: whether its render pass is called, whether it is offered input
 * events, and whether the buttons inside it report their own presses.
 *
 * <p>Those three answers are the whole question. Map controls are currently drawn in GL and
 * hit-tested by hand because the map is taken to be a surface no mod panel can join; if a panel
 * placed there renders, sees input, and dispatches its buttons, then the controls can be composed
 * from vanilla widgets instead of drawn, and the hand-rolled hit testing goes away with them.
 *
 * <p>Each signal is logged the first time it arrives rather than on every frame, so the log reads
 * as a checklist of what was granted rather than as a stream.
 *
 * <p>TODO: throwaway. Delete this package once the question above is answered either way.
 */
final class VanillaMapPanelProbePlugin implements CustomUIPanelPlugin {

    private static final Logger LOG = Global.getLogger(VanillaMapPanelProbePlugin.class);

    // One flag per signal rather than one for all three: the passes are granted independently, so
    // a panel that renders but is never offered input has to be distinguishable from one that was
    // never called at all.
    private boolean hasLoggedInput;
    private boolean hasLoggedPosition;
    private boolean hasLoggedRender;

    @Override
    public void positionChanged(PositionAPI position) {
        if (hasLoggedPosition) {
            return;
        }
        hasLoggedPosition = true;

        // The laid-out box, which says whether the parent honoured the requested placement or
        // overrode it - a panel positioned off the map surface would look identical to one that
        // never drew.
        LOG.info("Vanilla map panel probe: positioned at "
            + "[" + position.getX() + "," + position.getY()
            + " " + position.getWidth() + "x" + position.getHeight() + "]");
    }

    @Override
    public void renderBelow(float alphaMult) {
        // Nothing of the probe's own is drawn: the point is whether the vanilla child widgets
        // paint, so anything drawn here would only make a failure harder to read.
    }

    @Override
    public void render(float alphaMult) {
        if (hasLoggedRender) {
            return;
        }
        hasLoggedRender = true;

        // Reaching here at all is the first answer: the map widget renders its adopted children,
        // so a panel put there is composited rather than silently skipped.
        LOG.info("Vanilla map panel probe: render pass reached, alphaMult=" + alphaMult);
    }

    @Override
    public void advance(float amount) {
        // The probe holds no animation, and the campaign is paused behind these screens anyway.
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (hasLoggedInput) {
            return;
        }
        hasLoggedInput = true;

        // Being offered events is the second answer, and is separate from the buttons working:
        // the panel can be handed input while its children still fail to claim any of it.
        LOG.info("Vanilla map panel probe: input pass reached with "
            + (events == null ? 0 : events.size()) + " event(s)");
    }

    @Override
    public void buttonPressed(Object buttonId) {
        // The third and decisive answer, logged every time rather than once: a press reported here
        // means the vanilla button consumed a click over the map surface and routed it back
        // without any hit testing of the mod's own.
        LOG.info("Vanilla map panel probe: button pressed, id=" + buttonId);
    }
}
