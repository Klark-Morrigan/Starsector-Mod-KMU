package kmu.conditions.ui.picker.dialog;

import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.conditions.domain.KmuConditionRepository;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.ui.picker.action.KmuConditionPickerActionHandler;
import kmu.conditions.ui.picker.model.KmuConditionPickerModelFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StarsectorInteractionDialogPickerOpenerTest {

    @Nested
    class Open {

        @Test
        void opensDelegateWithConfiguredDimensions() {
            var openedDelegate = new AtomicReference<KmuConditionPickerDialogDelegate>();
            var openedWidth = new AtomicReference<Float>();
            var openedHeight = new AtomicReference<Float>();
            var dialog = buildDialog(openedDelegate, openedWidth, openedHeight);
            var opener = new StarsectorInteractionDialogPickerOpener(
                    buildSector(buildCampaignUI(dialog)));
            var delegate = buildDelegate(640f, 480f);

            opener.open(delegate);

            assertThat(openedDelegate).hasValue(delegate);
            assertThat(openedWidth).hasValue(640f);
            assertThat(openedHeight).hasValue(480f);
        }

        @Test
        void failsWhenCampaignUiIsMissing() {
            var opener = new StarsectorInteractionDialogPickerOpener(
                    buildSector(null));

            assertThatThrownBy(() -> opener.open(buildDelegate(640f, 480f)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No campaign UI is active.");
        }

        @Test
        void failsWhenInteractionDialogIsMissing() {
            var opener = new StarsectorInteractionDialogPickerOpener(
                    buildSector(buildCampaignUI(null)));

            assertThatThrownBy(() -> opener.open(buildDelegate(640f, 480f)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("No interaction dialog is active.");
        }
    }

    private static KmuConditionPickerDialogDelegate buildDelegate(float width, float height) {
        return new KmuConditionPickerDialogDelegate(
                new KmuConditionPickerActionHandler(
                        new KmuConditionService(buildEmptyRepository()),
                        new KmuConditionPickerModelFactory(new KmuConditionService(buildEmptyRepository())),
                        new KmuEditableMarketStub()),
                width,
                height);
    }

    private static KmuConditionRepository buildEmptyRepository() {
        return new KmuConditionRepository() {
            @Override
            public java.util.List<kmu.conditions.domain.KmuConditionSpec> getAllConditionSpecs() {
                return java.util.Collections.emptyList();
            }

            @Override
            public java.util.Optional<kmu.conditions.domain.KmuConditionSpec> findConditionSpec(String conditionId) {
                return java.util.Optional.empty();
            }
        };
    }

    private static SectorAPI buildSector(CampaignUIAPI campaignUI) {
        return proxy(SectorAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getCampaignUI")) {
                return campaignUI;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static CampaignUIAPI buildCampaignUI(InteractionDialogAPI dialog) {
        return proxy(CampaignUIAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("getCurrentInteractionDialog")) {
                return dialog;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static InteractionDialogAPI buildDialog(
            AtomicReference<KmuConditionPickerDialogDelegate> openedDelegate,
            AtomicReference<Float> openedWidth,
            AtomicReference<Float> openedHeight) {
        return proxy(InteractionDialogAPI.class, (proxy, method, args) -> {
            if (method.getName().equals("showCustomDialog")) {
                openedWidth.set((Float) args[0]);
                openedHeight.set((Float) args[1]);
                openedDelegate.set((KmuConditionPickerDialogDelegate) args[2]);
                return null;
            }
            return handleObjectMethodOrThrow(proxy, method, args);
        });
    }

    private static Object handleObjectMethodOrThrow(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass().equals(Object.class)) {
            switch (method.getName()) {
                case "toString":
                    return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
        }
        throw new UnsupportedOperationException(method.toString());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler);
    }

    private static final class KmuEditableMarketStub implements KmuEditableMarket {
        @Override
        public java.util.Set<String> getConditionIds() {
            return java.util.Collections.emptySet();
        }

        @Override
        public boolean hasCondition(String conditionId) {
            return false;
        }

        @Override
        public void addCondition(String conditionId) {
        }

        @Override
        public void markConditionSurveyed(String conditionId) {
        }

        @Override
        public void reapplyConditions() {
        }
    }
}
