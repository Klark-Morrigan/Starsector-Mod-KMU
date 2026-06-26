package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import kmu.conditions.ui.picker.action.KmuConditionPickerAction;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.tooltip.KmuConditionEntryTooltipCreator;
import kmlib.starsector.ui.color.StarsectorUiColor;

import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class KmuConditionIconButton {
    private static final float ABSENT_ICON_ALPHA = 0.55f;

    private KmuConditionPickerEntry entry;
    private final Consumer<KmuConditionPickerAction> actionConsumer;

    public KmuConditionIconButton(
            KmuConditionPickerEntry entry,
            Consumer<KmuConditionPickerAction> actionConsumer) {
        this.entry = Objects.requireNonNull(entry, "entry");
        this.actionConsumer = Objects.requireNonNull(actionConsumer, "actionConsumer");
    }

    public void addTo(
            CustomPanelAPI gridPanel,
            TooltipMakerAPI tooltipOwner,
            float x,
            float y,
            KmuConditionIconButtonLayout metrics) {
        Objects.requireNonNull(gridPanel, "gridPanel");
        Objects.requireNonNull(tooltipOwner, "tooltipOwner");
        Objects.requireNonNull(metrics, "metrics");

        var buttonPanel = gridPanel.createCustomPanel(
                metrics.getButtonWidth(),
                metrics.getButtonHeight(),
                new IconButtonPanelPlugin(metrics));
        gridPanel.addComponent(buttonPanel).inTL(x, y);
        tooltipOwner.addTooltipTo(
                new KmuConditionEntryTooltipCreator(this::getEntry),
                buttonPanel,
                TooltipMakerAPI.TooltipLocation.RIGHT);
    }

    void updateEntry(KmuConditionPickerEntry entry) {
        this.entry = Objects.requireNonNull(entry, "entry");
    }

    KmuConditionPickerEntry getEntry() {
        return entry;
    }

    static KmuConditionIconButtonLayout computeKmuConditionIconButtonLayout(KmuConditionPickerEntry entry) {
        Objects.requireNonNull(entry, "entry");
        var fallback = KmuConditionIconButtonSizing.FALLBACK_ICON_SIZE;
        var icon = entry.getIcon();
        if (!icon.isPresent()) {
            return KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(fallback, fallback);
        }

        try {
            var sprite = Global.getSettings().getSprite(icon.get());
            if (sprite == null) {
                return KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(fallback, fallback);
            }
            return KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(sprite.getWidth(), sprite.getHeight());
        } catch (RuntimeException exception) {
            return KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(fallback, fallback);
        }
    }

    private final class IconButtonPanelPlugin extends BaseCustomUIPanelPlugin {
        private final KmuConditionIconButtonLayout layout;
        private PositionAPI position;

        private IconButtonPanelPlugin(KmuConditionIconButtonLayout layout) {
            this.layout = layout;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            this.position = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (position == null) {
                return;
            }

            var x = position.getX();
            var y = position.getY();
            var width = position.getWidth();
            var height = position.getHeight();
            var style = KmuConditionIconButtonStyle.forEntry(entry);
            var backdropColor = style.getBackdropColor();
            var borderColor = style.getBorderColor();
            var backdropAlpha = style.getBackdropAlpha();
            var borderAlpha = style.getBorderAlpha();

            Misc.renderQuadAlpha(x, y, width, height, backdropColor, backdropAlpha * alphaMult);
            Misc.renderQuadAlpha(x, y, width, 1f, borderColor, borderAlpha * alphaMult);
            Misc.renderQuadAlpha(x, y + height - 1f, width, 1f, borderColor, borderAlpha * alphaMult);
            Misc.renderQuadAlpha(x, y, 1f, height, borderColor, borderAlpha * alphaMult);
            Misc.renderQuadAlpha(x + width - 1f, y, 1f, height, borderColor, borderAlpha * alphaMult);
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (position == null || events == null) {
                return;
            }

            for (InputEventAPI event : events) {
                if (event == null || event.isConsumed()) {
                    continue;
                }
                if (event.isLMBUpEvent() && position.containsEvent(event)) {
                    actionConsumer.accept(KmuConditionPickerAction.fromEntry(entry));
                    event.consume();
                    return;
                }
            }
        }

        @Override
        public void render(float alphaMult) {
            if (position == null) {
                return;
            }

            var icon = entry.getIcon();
            if (!icon.isPresent()) {
                return;
            }

            var sprite = getSprite(icon.get());
            if (sprite == null) {
                return;
            }

            var previousColor = sprite.getColor();
            var previousAlpha = sprite.getAlphaMult();
            var previousWidth = sprite.getWidth();
            var previousHeight = sprite.getHeight();
            var greyOut = !entry.isPresent();
            float alpha = greyOut ? ABSENT_ICON_ALPHA : 1f;

            sprite.setSize(layout.getIconWidth(), layout.getIconHeight());
            sprite.setColor(greyOut
                    ? StarsectorUiColor.DIM_GRAY.resolve()
                    : StarsectorUiColor.VANILLA_TEXT.resolve());
            sprite.setAlphaMult(alpha * alphaMult);
            sprite.render(
                    position.getX() + layout.getIconOffsetX(),
                    position.getY() + layout.getIconOffsetY());
            sprite.setColor(previousColor);
            sprite.setAlphaMult(previousAlpha);
            sprite.setSize(previousWidth, previousHeight);
        }

        private SpriteAPI getSprite(String iconPath) {
            try {
                return Global.getSettings().getSprite(iconPath);
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }
}
