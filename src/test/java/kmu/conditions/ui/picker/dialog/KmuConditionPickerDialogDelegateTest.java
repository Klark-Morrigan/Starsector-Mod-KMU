package kmu.conditions.ui.picker.dialog;

import com.fs.starfarer.api.ui.ButtonAPI;
import kmu.conditions.domain.KmuConditionRepository;
import kmu.conditions.domain.KmuConditionService;
import kmu.conditions.domain.KmuConditionSpec;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.ui.picker.action.KmuConditionPickerAction;
import kmu.conditions.ui.picker.action.KmuConditionPickerActionHandler;
import kmu.conditions.ui.picker.action.KmuConditionPickerFeedback;
import kmu.conditions.ui.picker.action.KmuConditionPickerFeedbackSink;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerModelFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerDialogDelegateTest {
    @Test
    void resolvesActionPassedAsButtonId() {
        KmuConditionPickerAction action = action();

        assertThat(KmuConditionPickerDialogDelegate.resolveActionFromUiEvent(action, null))
                .contains(action);
    }

    @Test
    void resolvesActionPassedAsData() {
        KmuConditionPickerAction action = action();

        assertThat(KmuConditionPickerDialogDelegate.resolveActionFromUiEvent("button-id", action))
                .contains(action);
    }

    @Test
    void resolvesActionStoredOnButtonCustomData() {
        KmuConditionPickerAction action = action();
        ButtonAPI button = buttonWithCustomData(action);

        assertThat(KmuConditionPickerDialogDelegate.resolveActionFromUiEvent(button, null))
                .contains(action);
    }

    @Test
    void ignoresUnrelatedUiEvents() {
        assertThat(KmuConditionPickerDialogDelegate.resolveActionFromUiEvent("button-id", "payload"))
                .isEmpty();
    }

    @Test
    void panelPluginHandlesButtonPressedActions() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                new KmuConditionSpec("hot", "Hot", "graphics/icons/markets/hot.png", true)));
        FakeEditableMarket market = new FakeEditableMarket();
        KmuConditionPickerActionHandler actionHandler = new KmuConditionPickerActionHandler(
                service,
                new KmuConditionPickerModelFactory(service),
                market);
        RecordingFeedbackSink feedbackSink = new RecordingFeedbackSink();
        KmuConditionPickerDialogDelegate delegate = new KmuConditionPickerDialogDelegate(
                actionHandler,
                feedbackSink,
                720f,
                560f);
        KmuConditionPickerAction action = KmuConditionPickerAction.fromEntry(
                actionHandler.getModel().getEntries().get(0));

        delegate.getCustomPanelPlugin().buttonPressed(action);

        assertThat(actionHandler.getModel().getEntries())
                .extracting(KmuConditionPickerEntry::getState)
                .containsExactly(KmuConditionPickerEntryState.PRESENT);
        assertThat(market.conditionIds).containsExactly("hot");
        assertThat(feedbackSink.messages).containsExactly("Added condition: hot");
    }

    @Test
    void panelPluginIgnoresPresentConditionActionsWithoutFeedback() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                new KmuConditionSpec("hot", "Hot", "graphics/icons/markets/hot.png", true)));
        FakeEditableMarket market = new FakeEditableMarket("hot");
        KmuConditionPickerActionHandler actionHandler = new KmuConditionPickerActionHandler(
                service,
                new KmuConditionPickerModelFactory(service),
                market);
        RecordingFeedbackSink feedbackSink = new RecordingFeedbackSink();
        KmuConditionPickerDialogDelegate delegate = new KmuConditionPickerDialogDelegate(
                actionHandler,
                feedbackSink,
                720f,
                560f);
        KmuConditionPickerAction action = KmuConditionPickerAction.fromEntry(
                actionHandler.getModel().getEntries().get(0));

        delegate.getCustomPanelPlugin().buttonPressed(action);

        assertThat(market.conditionIds).containsExactly("hot");
        assertThat(feedbackSink.messages).isEmpty();
    }

    private static KmuConditionPickerAction action() {
        return KmuConditionPickerAction.fromEntry(new KmuConditionPickerEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionPickerEntryState.ABSENT,
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

        private FakeEditableMarket(String... conditionIds) {
            for (String conditionId : conditionIds) {
                this.conditionIds.add(conditionId);
            }
        }

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

    private static final class RecordingFeedbackSink implements KmuConditionPickerFeedbackSink {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void report(KmuConditionPickerFeedback feedback) {
            messages.add(feedback.getMessage());
        }
    }
}
