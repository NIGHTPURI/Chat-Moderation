package dev.chatmoderation.core.policy;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.core.rule.RuleMatch;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DefaultModerationPolicy implements ModerationPolicy {
    private final Map<ModerationReason, ModerationAction> actions;

    public DefaultModerationPolicy() {
        this(Map.of());
    }

    public DefaultModerationPolicy(Map<ModerationReason, ModerationAction> overrides) {
        Objects.requireNonNull(overrides, "overrides must not be null");

        EnumMap<ModerationReason, ModerationAction> configuredActions = defaultActions();
        overrides.forEach((reason, action) -> configuredActions.put(
                Objects.requireNonNull(reason, "reason must not be null"),
                Objects.requireNonNull(action, "action must not be null")
        ));
        this.actions = Map.copyOf(configuredActions);
    }

    @Override
    public ModerationResult decide(String originalMessage, List<RuleMatch> matches) {
        Objects.requireNonNull(originalMessage, "originalMessage must not be null");
        Objects.requireNonNull(matches, "matches must not be null");

        List<RuleMatch> checkedMatches = List.copyOf(matches);
        validateMaskRanges(originalMessage, checkedMatches);

        Set<ModerationReason> reasons = new LinkedHashSet<>();
        ModerationAction finalAction = ModerationAction.ALLOW;
        for (RuleMatch match : checkedMatches) {
            reasons.add(match.reason());
            finalAction = stronger(finalAction, actions.get(match.reason()));
        }

        List<ModerationReason> reasonList = List.copyOf(reasons);
        return switch (finalAction) {
            case BLOCK -> ModerationResult.block(reasonList);
            case MASK -> ModerationResult.mask(mask(originalMessage, checkedMatches), reasonList);
            case ALLOW -> new ModerationResult(
                    true,
                    ModerationAction.ALLOW,
                    reasonList,
                    originalMessage
            );
        };
    }

    private EnumMap<ModerationReason, ModerationAction> defaultActions() {
        EnumMap<ModerationReason, ModerationAction> defaults =
                new EnumMap<>(ModerationReason.class);
        defaults.put(ModerationReason.PROFANITY, ModerationAction.BLOCK);
        defaults.put(ModerationReason.PERSONAL_INFORMATION, ModerationAction.MASK);
        defaults.put(ModerationReason.URL, ModerationAction.BLOCK);
        defaults.put(ModerationReason.SPAM, ModerationAction.BLOCK);
        defaults.put(ModerationReason.HARASSMENT, ModerationAction.BLOCK);
        defaults.put(ModerationReason.OTHER, ModerationAction.BLOCK);
        return defaults;
    }

    private ModerationAction stronger(ModerationAction current, ModerationAction candidate) {
        if (current == ModerationAction.BLOCK || candidate == ModerationAction.BLOCK) {
            return ModerationAction.BLOCK;
        }
        if (current == ModerationAction.MASK || candidate == ModerationAction.MASK) {
            return ModerationAction.MASK;
        }
        return ModerationAction.ALLOW;
    }

    private void validateMaskRanges(String message, List<RuleMatch> matches) {
        for (RuleMatch match : matches) {
            if (actions.get(match.reason()) == ModerationAction.MASK
                    && (match.endIndex() > message.length()
                    || !message.substring(match.startIndex(), match.endIndex())
                    .equals(match.matchedText()))) {
                throw new IllegalArgumentException("match does not belong to originalMessage");
            }
        }
    }

    private String mask(String message, List<RuleMatch> matches) {
        char[] masked = message.toCharArray();
        for (RuleMatch match : matches) {
            if (actions.get(match.reason()) != ModerationAction.MASK) {
                continue;
            }
            for (int index = match.startIndex(); index < match.endIndex(); index++) {
                masked[index] = '*';
            }
        }
        return new String(masked);
    }
}
