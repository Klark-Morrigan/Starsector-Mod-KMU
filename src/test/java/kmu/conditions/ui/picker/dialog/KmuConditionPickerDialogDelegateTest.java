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
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerDialogDelegateTest {

    @Nested
    class ResolveActionFromUiEvent {

        @Test
        void resolvesActionPassedAsButtonId() {
            var action = buildAction();

            assertThat(KmuConditionPickerDialogDelegate.resolveActionFromUiEvent(action, null))
                    .contains(action);
        }

        @Test
        void resolvesActionPassedAsData() {
            var action = buildAction();

            assertThat(KmuConditionPickerDialogDelegate.resolveActionFromUiEvent("button-id", action))
                    .contains(action);
        }

        @Test
        void resolvesActionStoredOnButtonCustomData() {
            var action = buildAction();
            var button = buildButtonWithCustomData(action);

            assertThat(KmuConditionPickerDialogDelegate.resolveActionFromUiEvent(button, null))
                    .contains(action);
        }

        @Test
        void ignoresUnrelatedUiEvents() {
            assertThat(KmuConditionPickerDialogDelegate.resolveActionFromUiEvent("button-id", "payload"))
                    .isEmpty();
        }

        private ButtonAPI buildButtonWithCustomData(Object customData) {
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
                        return resolveDefaultValue(method.getReturnType());
                    });
        }

        private Object resolveDefaultValue(Class<?> returnType) {
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
    }

    @Nested
    class GetCustomPanelPlugin {

        @Test
        void panelPluginHandlesButtonPressedActions() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    new KmuConditionSpec("hot", "Hot", "graphics/icons/markets/hot.png", true)));
            var marketFake = new EditableMarketFake();
            var actionHandler = new KmuConditionPickerActionHandler(
                    service,
                    new KmuConditionPickerModelFactory(service),
                    marketFake);
            var feedbackSink = new RecordingFeedbackSink();
            var delegate = new KmuConditionPickerDialogDelegate(
                    actionHandler,
                    feedbackSink,
                    720f,
                    560f);
            var action = KmuConditionPickerAction.fromEntry(
                    actionHandler.getModel().getEntries().get(0));

            delegate.getCustomPanelPlugin().buttonPressed(action);

            assertThat(actionHandler.getModel().getEntries())
                    .extracting(KmuConditionPickerEntry::getState)
                    .containsExactly(KmuConditionPickerEntryState.PRESENT);
            assertThat(marketFake.conditionIds).containsExactly("hot");
            assertThat(feedbackSink.messages).containsExactly("Added condition: hot");
        }

        @Test
        void panelPluginIgnoresPresentConditionActionsWithoutFeedback() {
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    new KmuConditionSpec("hot", "Hot", "graphics/icons/markets/hot.png", true)));
            var marketFake = new EditableMarketFake("hot");
            var actionHandler = new KmuConditionPickerActionHandler(
                    service,
                    new KmuConditionPickerModelFactory(service),
                    marketFake);
            var feedbackSink = new RecordingFeedbackSink();
            var delegate = new KmuConditionPickerDialogDelegate(
                    actionHandler,
                    feedbackSink,
                    720f,
                    560f);
            var action = KmuConditionPickerAction.fromEntry(
                    actionHandler.getModel().getEntries().get(0));

            delegate.getCustomPanelPlugin().buttonPressed(action);

            assertThat(marketFake.conditionIds).containsExactly("hot");
            assertThat(feedbackSink.messages).isEmpty();
        }
    }

    @Nested
    class GetConfirmText {

        @BeforeEach
        void installStarsectorSettings() {
            StarsectorSettingsFake.installSettings();
        }

        @AfterEach
        void clearStarsectorSettings() {
            StarsectorSettingsFake.clearSettings();
        }

        @Test
        void confirmTextIsTheSharedCloseWording() {
            // The picker's one button dismisses a list nothing was staged in, so it keeps the shared
            // wording that any dialog with nothing to commit can read. The bar-arranging dialog wants
            // a word of its own precisely because it does have something to leave the player with.
            var service = new KmuConditionService(new ConditionRepositoryFake(
                    new KmuConditionSpec("hot", "Hot", "graphics/icons/markets/hot.png", true)));
            var delegate = new KmuConditionPickerDialogDelegate(
                    new KmuConditionPickerActionHandler(
                            service,
                            new KmuConditionPickerModelFactory(service),
                            new EditableMarketFake()),
                    new RecordingFeedbackSink(),
                    720f,
                    560f);

            assertThat(delegate.getConfirmText())
                    .isEqualTo("Close");
        }
    }

    private static KmuConditionPickerAction buildAction() {
        return KmuConditionPickerAction.fromEntry(new KmuConditionPickerEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionPickerEntryState.ABSENT,
                "Test tooltip"));
    }

    private static final class ConditionRepositoryFake implements KmuConditionRepository {
        private final List<KmuConditionSpec> specs;

        private ConditionRepositoryFake(KmuConditionSpec... specs) {
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

    private static final class EditableMarketFake implements KmuEditableMarket {
        private final Set<String> conditionIds = new LinkedHashSet<>();

        private EditableMarketFake(String... conditionIds) {
            for (var conditionId : conditionIds) {
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
