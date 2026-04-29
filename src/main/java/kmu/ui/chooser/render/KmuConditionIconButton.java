package kmu.ui.chooser.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import kmu.ui.chooser.action.KmuConditionChooserAction;
import kmu.ui.chooser.model.KmuConditionChooserEntry;
import kmu.ui.chooser.tooltip.KmuTooltipSection;
import kmu.ui.chooser.tooltip.KmuTooltipSectionStyle;
import kmu.ui.chooser.tooltip.KmuConditionTooltipRenderer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class KmuConditionIconButton {
    static final class Sizing {
        static final float ICON_MARGIN = 6f;
        static final float VANILLA_COLONY_CONDITION_ICON_HEIGHT = 40f;
        static final float FALLBACK_ICON_SIZE = VANILLA_COLONY_CONDITION_ICON_HEIGHT;
        static final float MIN_BUTTON_SIZE = 34f;

        private Sizing() {
        }

        static float squareButtonWidth() {
            return VANILLA_COLONY_CONDITION_ICON_HEIGHT + ICON_MARGIN * 2f;
        }
    }

    private static final class TooltipLayout {
        private static final float METADATA_PAD = 4f;
        private static final float TOOLTIP_WIDTH = 420f;

        private TooltipLayout() {
        }
    }

    private static final class Alpha {
        private static final float ABSENT_ICON = 0.55f;
        private static final float BUTTON_BACKDROP = 0.28f;
        private static final float BUTTON_BORDER = 0.18f;
        private static final float SUPPRESSED_BACKDROP = 0.34f;
        private static final float SUPPRESSED_BORDER = 0.50f;
        private static final float VISIBLE_PRESENT_BACKDROP = 0.28f;
        private static final float VISIBLE_PRESENT_BORDER = 0.42f;

        private Alpha() {
        }
    }

    private static final class Palette {
        private static final Color PRESENT_ICON = Color.WHITE;
        private static final Color ABSENT_ICON = new Color(130, 130, 130);
        private static final Color DEFAULT_BACKDROP = new Color(31, 94, 112, 175);
        private static final Color DEFAULT_BORDER = new Color(170, 222, 255, 255);
        private static final Color SUPPRESSED_BACKDROP = new Color(150, 50, 45);
        private static final Color SUPPRESSED_BORDER = new Color(255, 90, 80);
        private static final Color VISIBLE_PRESENT_BACKDROP = new Color(35, 80, 45);
        private static final Color VISIBLE_PRESENT_BORDER = new Color(90, 220, 95);

        private Palette() {
        }

        private static Color defaultBackdrop() {
            return starsectorColor(Misc::getDarkPlayerColor, DEFAULT_BACKDROP);
        }

        private static Color defaultBorder() {
            return starsectorColor(Misc::getBasePlayerColor, DEFAULT_BORDER);
        }

        private static Color starsectorColor(Supplier<Color> colorSupplier, Color fallback) {
            try {
                Color color = colorSupplier.get();
                return color != null ? color : fallback;
            } catch (Throwable exception) {
                return fallback;
            }
        }
    }

    private KmuConditionChooserEntry entry;
    private final Consumer<KmuConditionChooserAction> actionConsumer;

    public KmuConditionIconButton(
            KmuConditionChooserEntry entry,
            Consumer<KmuConditionChooserAction> actionConsumer) {
        this.entry = Objects.requireNonNull(entry, "entry");
        this.actionConsumer = Objects.requireNonNull(actionConsumer, "actionConsumer");
    }

    public void addTo(
            CustomPanelAPI gridPanel,
            TooltipMakerAPI tooltipOwner,
            float x,
            float y,
            ButtonMetrics metrics) {
        Objects.requireNonNull(gridPanel, "gridPanel");
        Objects.requireNonNull(tooltipOwner, "tooltipOwner");
        Objects.requireNonNull(metrics, "metrics");

        CustomPanelAPI buttonPanel = gridPanel.createCustomPanel(
                metrics.getButtonWidth(),
                metrics.getButtonHeight(),
                new IconButtonPanelPlugin(metrics));
        gridPanel.addComponent(buttonPanel).inTL(x, y);
        tooltipOwner.addTooltipTo(
                new EntryTooltipCreator(this::getEntry),
                buttonPanel,
                TooltipMakerAPI.TooltipLocation.RIGHT);
    }

    void updateEntry(KmuConditionChooserEntry entry) {
        this.entry = Objects.requireNonNull(entry, "entry");
    }

    KmuConditionChooserEntry getEntry() {
        return entry;
    }

    static ButtonMetrics measure(KmuConditionChooserEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Optional<String> icon = entry.getIcon();
        if (!icon.isPresent()) {
            return metricsForSource(Sizing.FALLBACK_ICON_SIZE, Sizing.FALLBACK_ICON_SIZE);
        }

        try {
            SpriteAPI sprite = Global.getSettings().getSprite(icon.get());
            if (sprite == null) {
                return metricsForSource(Sizing.FALLBACK_ICON_SIZE, Sizing.FALLBACK_ICON_SIZE);
            }
            return metricsForSource(sprite.getWidth(), sprite.getHeight());
        } catch (RuntimeException exception) {
            return metricsForSource(Sizing.FALLBACK_ICON_SIZE, Sizing.FALLBACK_ICON_SIZE);
        }
    }

    static boolean shouldGreyOut(KmuConditionChooserEntry entry) {
        return !Objects.requireNonNull(entry, "entry").isPresent();
    }

    static Color backdropColorFor(KmuConditionChooserEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.isSuppressed()) {
            return Palette.SUPPRESSED_BACKDROP;
        }
        if (isVisibleUnsuppressedPresent(entry)) {
            return Palette.VISIBLE_PRESENT_BACKDROP;
        }
        return Palette.defaultBackdrop();
    }

    static Color borderColorFor(KmuConditionChooserEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.isSuppressed()) {
            return Palette.SUPPRESSED_BORDER;
        }
        if (isVisibleUnsuppressedPresent(entry)) {
            return Palette.VISIBLE_PRESENT_BORDER;
        }
        return Palette.defaultBorder();
    }

    static float backdropAlphaFor(KmuConditionChooserEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.isSuppressed()) {
            return Alpha.SUPPRESSED_BACKDROP;
        }
        if (isVisibleUnsuppressedPresent(entry)) {
            return Alpha.VISIBLE_PRESENT_BACKDROP;
        }
        return Alpha.BUTTON_BACKDROP;
    }

    static float borderAlphaFor(KmuConditionChooserEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.isSuppressed()) {
            return Alpha.SUPPRESSED_BORDER;
        }
        if (isVisibleUnsuppressedPresent(entry)) {
            return Alpha.VISIBLE_PRESENT_BORDER;
        }
        return Alpha.BUTTON_BORDER;
    }

    static boolean isVisibleUnsuppressedPresent(KmuConditionChooserEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return entry.isPresent() && !entry.isSuppressed() && !entry.isHidden();
    }

    static ButtonMetrics metricsForSource(float sourceWidth, float sourceHeight) {
        IconBounds iconBounds = iconBounds(sourceWidth, sourceHeight);
        float buttonWidth = Math.max(Sizing.MIN_BUTTON_SIZE, iconBounds.getWidth() + Sizing.ICON_MARGIN * 2f);
        float buttonHeight = Math.max(Sizing.MIN_BUTTON_SIZE, iconBounds.getHeight() + Sizing.ICON_MARGIN * 2f);
        float iconOffsetX = (buttonWidth - iconBounds.getWidth()) / 2f;
        float iconOffsetY = (buttonHeight - iconBounds.getHeight()) / 2f;
        return new ButtonMetrics(
                buttonWidth,
                buttonHeight,
                iconBounds.getWidth(),
                iconBounds.getHeight(),
                iconOffsetX,
                iconOffsetY);
    }

    static IconBounds iconBounds(float sourceWidth, float sourceHeight) {
        if (sourceWidth <= 0f || sourceHeight <= 0f) {
            return new IconBounds(Sizing.FALLBACK_ICON_SIZE, Sizing.FALLBACK_ICON_SIZE);
        }

        float scale = Sizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT / sourceHeight;
        return new IconBounds(sourceWidth * scale, sourceHeight * scale);
    }

    static final class ButtonMetrics {
        private final float buttonWidth;
        private final float buttonHeight;
        private final float iconWidth;
        private final float iconHeight;
        private final float iconOffsetX;
        private final float iconOffsetY;

        ButtonMetrics(
                float buttonWidth,
                float buttonHeight,
                float iconWidth,
                float iconHeight,
                float iconOffsetX,
                float iconOffsetY) {
            this.buttonWidth = buttonWidth;
            this.buttonHeight = buttonHeight;
            this.iconWidth = iconWidth;
            this.iconHeight = iconHeight;
            this.iconOffsetX = iconOffsetX;
            this.iconOffsetY = iconOffsetY;
        }

        float getButtonWidth() {
            return buttonWidth;
        }

        float getButtonHeight() {
            return buttonHeight;
        }

        float getIconWidth() {
            return iconWidth;
        }

        float getIconHeight() {
            return iconHeight;
        }

        float getIconOffsetX() {
            return iconOffsetX;
        }

        float getIconOffsetY() {
            return iconOffsetY;
        }
    }

    static final class IconBounds {
        private final float width;
        private final float height;

        private IconBounds(float width, float height) {
            this.width = width;
            this.height = height;
        }

        float getWidth() {
            return width;
        }

        float getHeight() {
            return height;
        }
    }

    private final class IconButtonPanelPlugin extends BaseCustomUIPanelPlugin {
        private final ButtonMetrics metrics;
        private PositionAPI position;

        private IconButtonPanelPlugin(ButtonMetrics metrics) {
            this.metrics = metrics;
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

            float x = position.getX();
            float y = position.getY();
            float width = position.getWidth();
            float height = position.getHeight();
            Color backdropColor = backdropColorFor(entry);
            Color borderColor = borderColorFor(entry);
            float backdropAlpha = backdropAlphaFor(entry);
            float borderAlpha = borderAlphaFor(entry);

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
                    actionConsumer.accept(KmuConditionChooserAction.fromEntry(entry));
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

            Optional<String> icon = entry.getIcon();
            if (!icon.isPresent()) {
                return;
            }

            SpriteAPI sprite = sprite(icon.get());
            if (sprite == null) {
                return;
            }

            Color previousColor = sprite.getColor();
            float previousAlpha = sprite.getAlphaMult();
            float previousWidth = sprite.getWidth();
            float previousHeight = sprite.getHeight();
            float alpha = shouldGreyOut(entry) ? Alpha.ABSENT_ICON : 1f;

            sprite.setSize(metrics.getIconWidth(), metrics.getIconHeight());
            sprite.setColor(shouldGreyOut(entry) ? Palette.ABSENT_ICON : Palette.PRESENT_ICON);
            sprite.setAlphaMult(alpha * alphaMult);
            sprite.render(
                    position.getX() + metrics.getIconOffsetX(),
                    position.getY() + metrics.getIconOffsetY());
            sprite.setColor(previousColor);
            sprite.setAlphaMult(previousAlpha);
            sprite.setSize(previousWidth, previousHeight);
        }

        private SpriteAPI sprite(String iconPath) {
            try {
                return Global.getSettings().getSprite(iconPath);
            } catch (RuntimeException exception) {
                return null;
            }
        }
    }

    static final class EntryTooltipCreator implements TooltipMakerAPI.TooltipCreator {
        private final Supplier<KmuConditionChooserEntry> entrySupplier;

        EntryTooltipCreator(Supplier<KmuConditionChooserEntry> entrySupplier) {
            this.entrySupplier = Objects.requireNonNull(entrySupplier, "entrySupplier");
        }

        @Override
        public boolean isTooltipExpandable(Object tooltipParam) {
            KmuConditionChooserEntry entry = entrySupplier.get();
            Optional<KmuConditionTooltipRenderer> renderer = entry.getTooltipRenderer();
            return renderer.isPresent() && renderer.get().isTooltipExpandable();
        }

        @Override
        public float getTooltipWidth(Object tooltipParam) {
            KmuConditionChooserEntry entry = entrySupplier.get();
            Optional<KmuConditionTooltipRenderer> renderer = entry.getTooltipRenderer();
            if (renderer.isPresent()) {
                return renderer.get().getTooltipWidth();
            }
            return TooltipLayout.TOOLTIP_WIDTH;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            KmuConditionChooserEntry entry = entrySupplier.get();
            Optional<KmuConditionTooltipRenderer> renderer = entry.getTooltipRenderer();
            if (renderer.isPresent() && renderLiveTooltip(tooltip, expanded, renderer.get())) {
                addStatusSections(tooltip, entry);
                addMetadataFooter(tooltip, entry);
                return;
            }

            tooltip.addTitle(entry.getName());
            if (!entry.getTooltipText().trim().isEmpty()) {
                tooltip.addPara(entry.getTooltipText(), TooltipLayout.METADATA_PAD);
            }
            addStatusSections(tooltip, entry);
            addMetadataFooter(tooltip, entry);
        }

        private boolean renderLiveTooltip(
                TooltipMakerAPI tooltip,
                boolean expanded,
                KmuConditionTooltipRenderer renderer) {
            try {
                renderer.createTooltip(tooltip, expanded);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private void addStatusSections(TooltipMakerAPI tooltip, KmuConditionChooserEntry entry) {
            if (entry.isSuppressed()) {
                KmuTooltipSection.add(
                        tooltip,
                        KmuTooltipSectionStyle.WARNING,
                        "Suppressed",
                        "This condition is present on the market, but it's suppressed. "
                                + "KMU has not detected the exact reason yet. Report this case to the KMU mod "
                                + "developer.");
            }
            if (entry.isHidden()) {
                KmuTooltipSection.add(
                        tooltip,
                        KmuTooltipSectionStyle.WARNING,
                        "Hidden",
                        "This condition is present on the market, but it's hidden. "
                                + "KMU has not detected the exact reason yet. Report this case to "
                                + "the KMU mod developer.");
            }
        }

        private void addMetadataFooter(TooltipMakerAPI tooltip, KmuConditionChooserEntry entry) {
            List<String> metadataLines = new ArrayList<>();
            metadataLines.add(metadataText("source", entry.getSourceModName().orElse("Starsector")));
            metadataLines.add(metadataText("id", entry.getConditionId()));
            entry.getIcon().ifPresent(icon -> metadataLines.add(metadataText("icon", icon)));
            if (entry.isPresent()) {
                metadataLines.add(metadataText("hidden", String.valueOf(entry.isHidden())));
                metadataLines.add(metadataText("suppressed", String.valueOf(entry.isSuppressed())));
            }
            KmuTooltipSection.add(tooltip, KmuTooltipSectionStyle.MUTED, "Metadata", metadataLines);
        }

        private String metadataText(String label, String value) {
            return label + ": " + value;
        }
    }
}
