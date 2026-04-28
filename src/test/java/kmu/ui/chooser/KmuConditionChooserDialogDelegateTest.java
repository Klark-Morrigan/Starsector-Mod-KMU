package kmu.ui.chooser;

import com.fs.starfarer.api.ui.ButtonAPI;
import kmu.conditions.KmuConditionRepository;
import kmu.conditions.KmuConditionService;
import kmu.conditions.KmuConditionSpec;
import kmu.conditions.KmuEditableMarket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionChooserDialogDelegateTest {
    @Test
    void resolvesActionPassedAsButtonId() {
        KmuConditionChooserAction action = action();

        assertThat(KmuConditionChooserDialogDelegate.resolveActionFromUiEvent(action, null))
                .contains(action);
    }

    @Test
    void resolvesActionPassedAsData() {
        KmuConditionChooserAction action = action();

        assertThat(KmuConditionChooserDialogDelegate.resolveActionFromUiEvent("button-id", action))
                .contains(action);
    }

    @Test
    void resolvesActionStoredOnButtonCustomData() {
        KmuConditionChooserAction action = action();
        ButtonAPI button = buttonWithCustomData(action);

        assertThat(KmuConditionChooserDialogDelegate.resolveActionFromUiEvent(button, null))
                .contains(action);
    }

    @Test
    void ignoresUnrelatedUiEvents() {
        assertThat(KmuConditionChooserDialogDelegate.resolveActionFromUiEvent("button-id", "payload"))
                .isEmpty();
    }

    @Test
    void panelPluginHandlesButtonPressedActions() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                new KmuConditionSpec("hot", "Hot", "graphics/icons/markets/hot.png", true)));
        FakeEditableMarket market = new FakeEditableMarket();
        KmuConditionChooserActionHandler actionHandler = new KmuConditionChooserActionHandler(
                service,
                new KmuConditionChooserModelFactory(service),
                market);
        KmuConditionChooserDialogDelegate delegate = new KmuConditionChooserDialogDelegate(actionHandler);
        KmuConditionChooserAction action = KmuConditionChooserAction.fromEntry(
                actionHandler.getModel().getEntries().get(0));

        delegate.getCustomPanelPlugin().buttonPressed(action);

        assertThat(actionHandler.getModel().getEntries())
                .extracting(KmuConditionChooserEntry::getState)
                .containsExactly(KmuConditionChooserEntryState.PRESENT);
        assertThat(market.conditionIds).containsExactly("hot");
    }

    private static KmuConditionChooserAction action() {
        return KmuConditionChooserAction.fromEntry(new KmuConditionChooserEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionChooserEntryState.ABSENT,
                "Test tooltip"));
    }

    private static ButtonAPI buttonWithCustomData(Object customData) {
        return (ButtonAPI) Proxy.newProxyInstance(
                ButtonAPI.class.getClassLoader(),
                new Class<?>[] {ButtonAPI.class},
                (proxy, method, args) -> {
                    if ("getCustomData".equals(method.getName())) {
                        return customData;
                    }
                    if ("toString".equals(method.getName())) {
                        return "ButtonAPI test double";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static final class FakeConditionRepository implements KmuConditionRepository {
        private final List<KmuConditionSpec> specs;

        private FakeConditionRepository(KmuConditionSpec... specs) {
            this.specs = Arrays.asList(specs);
        }

        @Override
        public List<KmuConditionSpec> getAllConditionSpecs() {
            return specs;
        }

        @Override
        public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
            return specs.stream()
                    .filter(spec -> spec.getId().equals(conditionId))
                    .findFirst();
        }
    }

    private static final class FakeEditableMarket implements KmuEditableMarket {
        private final Set<String> conditionIds = new LinkedHashSet<>();

        @Override
        public Set<String> getConditionIds() {
            return conditionIds;
        }

        @Override
        public boolean hasCondition(String conditionId) {
            return conditionIds.contains(conditionId);
        }

        @Override
        public void addCondition(String conditionId) {
            conditionIds.add(conditionId);
        }

        @Override
        public void markConditionSurveyed(String conditionId) {
        }

        @Override
        public void reapplyConditions() {
        }
    }
}
