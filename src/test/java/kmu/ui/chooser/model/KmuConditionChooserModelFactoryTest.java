package kmu.ui.chooser.model;

import kmu.conditions.KmuConditionRepository;
import kmu.conditions.KmuConditionService;
import kmu.conditions.KmuConditionSpec;
import kmu.conditions.KmuEditableMarket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmuConditionChooserModelFactoryTest {
    @Test
    void buildsEntriesForPlanetarySpecsInServiceOrder() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true),
                spec("population_3", "Population 3", false),
                spec("farmland_rich", "Farmland: Rich", true)));
        KmuConditionChooserModelFactory factory = new KmuConditionChooserModelFactory(service);

        KmuConditionChooserModel model = factory.create(new FakeEditableMarket("hot"));

        assertThat(model.getEntries())
                .extracting(KmuConditionChooserEntry::getConditionId)
                .containsExactly("hot", "farmland_rich");
        assertThat(model.getEntries())
                .extracting(KmuConditionChooserEntry::getState)
                .containsExactly(KmuConditionChooserEntryState.PRESENT, KmuConditionChooserEntryState.ABSENT);
        assertThat(model.getEntryCount()).isEqualTo(2);
        assertThat(model.getPresentCount()).isEqualTo(1);
        assertThat(model.getAbsentCount()).isEqualTo(1);
    }

    @Test
    void includesSpecDataAndSpecDescriptionTooltipText() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                new KmuConditionSpec("cold", "Cold", null, "Cold condition description.", "Vanilla", true)));
        KmuConditionChooserModelFactory factory = new KmuConditionChooserModelFactory(service);

        KmuConditionChooserEntry entry = factory.create(new FakeEditableMarket())
                .getEntries()
                .get(0);

        assertThat(entry.getConditionId()).isEqualTo("cold");
        assertThat(entry.getName()).isEqualTo("Cold");
        assertThat(entry.getIcon()).isEmpty();
        assertThat(entry.descriptionLine()).isEqualTo("cold - Absent");
        assertThat(entry.getTooltipText()).isEqualTo("Cold condition description.");
        assertThat(entry.getSourceModName()).contains("Vanilla");
    }

    @Test
    void returnsImmutableEntryList() {
        KmuConditionService service = new KmuConditionService(new FakeConditionRepository(
                spec("hot", "Hot", true)));
        KmuConditionChooserModelFactory factory = new KmuConditionChooserModelFactory(service);

        KmuConditionChooserModel model = factory.create(new FakeEditableMarket());

        assertThatThrownBy(() -> model.getEntries().add(new KmuConditionChooserEntry(
                "cold",
                "Cold",
                null,
                KmuConditionChooserEntryState.ABSENT,
                "tooltip")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static KmuConditionSpec spec(String id, String name, boolean planetary) {
        return new KmuConditionSpec(id, name, "graphics/icons/" + id + ".png", planetary);
    }

    private static final class FakeConditionRepository implements KmuConditionRepository {
        private final LinkedHashMap<String, KmuConditionSpec> specsById = new LinkedHashMap<>();

        private FakeConditionRepository(KmuConditionSpec... specs) {
            for (KmuConditionSpec spec : specs) {
                specsById.put(spec.getId(), spec);
            }
        }

        @Override
        public List<KmuConditionSpec> getAllConditionSpecs() {
            return new ArrayList<>(specsById.values());
        }

        @Override
        public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
            return Optional.ofNullable(specsById.get(conditionId));
        }
    }

    private static final class FakeEditableMarket implements KmuEditableMarket {
        private final LinkedHashSet<String> conditionIds = new LinkedHashSet<>();

        private FakeEditableMarket(String... conditionIds) {
            for (String conditionId : conditionIds) {
                this.conditionIds.add(conditionId);
            }
        }

        @Override
        public Set<String> getConditionIds() {
            return new LinkedHashSet<>(conditionIds);
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
