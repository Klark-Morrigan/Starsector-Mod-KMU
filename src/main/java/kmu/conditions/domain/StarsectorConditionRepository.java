package kmu.conditions.domain;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;

import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static kmu.util.KmuValues.normaliseText;

/**
 * The market conditions the running game knows about, as the mod's own value rather than as the
 * engine's specs.
 *
 * <p>Everything the picker shows comes through here, so the engine's spec type stops at this
 * boundary: a screen holding {@code MarketConditionSpecAPI} could only be exercised inside a
 * running game, and would be free to read a field this never meant to publish.
 *
 * <p>A spec that cannot be read is left out rather than allowed to take the listing down with it.
 * The specs are contributed by every enabled mod, and one of them raising on a field it never
 * filled in is a fault in that mod, not a reason the player cannot open the condition picker. Each
 * dropped spec is logged, since a condition missing from the list with nothing said about it is
 * indistinguishable from one the game never had.
 */
public final class StarsectorConditionRepository implements KmuConditionRepository {

    static final String VANILLA_SOURCE_NAME = "Starsector";

    // What a dropped spec is called in the log when it will not even say its own id.
    private static final String UNNAMED_SPEC_ID = "<unnamed>";

    private static final Logger LOG = Global.getLogger(StarsectorConditionRepository.class);

    private final SettingsAPI settings;

    public StarsectorConditionRepository() {
        this(Global.getSettings());
    }

    public StarsectorConditionRepository(SettingsAPI settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public List<KmuConditionSpec> getAllConditionSpecs() {
        var specs = settings.getAllMarketConditionSpecs();
        if (specs == null) {
            return Collections.emptyList();
        }
        return specs.stream()
            .filter(Objects::nonNull)
            .map(this::buildSpecOrNull)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public Optional<KmuConditionSpec> findConditionSpec(String conditionId) {
        var normalisedId = normaliseText(conditionId);
        if (normalisedId == null) {
            return Optional.empty();
        }

        var spec = settings.getMarketConditionSpec(normalisedId);
        if (spec == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(buildSpecOrNull(spec));
    }

    // One engine spec as the mod's own value, or null where the spec cannot be read at all.
    //
    // Broad on purpose: every field below is another mod's data, and any of them can raise. What
    // the caller loses is the same whichever does - one condition - so the listing goes on without
    // it rather than the picker failing to open over somebody else's spec. The id is read on its
    // own for the log line, because the throw is frequently in the fields after it and a dropped
    // condition nobody can name is a report the player cannot act on.
    private KmuConditionSpec buildSpecOrNull(MarketConditionSpecAPI spec) {
        try {
            return new KmuConditionSpec(
                spec.getId(),
                spec.getName(),
                spec.getIcon(),
                spec.getDesc(),
                readSourceModName(spec),
                spec.isPlanetary());

        } catch (RuntimeException specUnreadable) {
            LOG.warn(
                "Could not read the market condition spec '" + readSpecIdOrUnknown(spec)
                    + "'; it will not be listed.",
                specUnreadable);
            return null;
        }
    }

    // What the spec calls itself, for a message about a spec that has already failed to be read -
    // so this read is allowed to fail in turn rather than replacing one lost condition with a
    // lost listing.
    private String readSpecIdOrUnknown(MarketConditionSpecAPI spec) {
        try {
            var specId = normaliseText(spec.getId());
            return specId == null ? UNNAMED_SPEC_ID : specId;

        } catch (RuntimeException idUnreadable) {
            return UNNAMED_SPEC_ID;
        }
    }

    private String readSourceModName(MarketConditionSpecAPI spec) {
        var sourceMod = spec.getSourceMod();
        if (sourceMod == null) {
            return VANILLA_SOURCE_NAME;
        }
        var sourceModName = normaliseText(sourceMod.getName());
        return sourceModName == null ? VANILLA_SOURCE_NAME : sourceModName;
    }
}
