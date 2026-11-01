import dev.chatmoderation.core.ChatModerationServices;
import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.semantic.ProviderFailurePolicy;
import dev.chatmoderation.semantic.SemanticModerationProvider;
import dev.chatmoderation.semantic.SemanticModerationResult;

import java.util.List;

/** Intentionally compiled outside Gradle's production/test source sets. No API credentials. */
public final class PackagedConsumer {
    public static void main(String[] args) {
        var deterministic = ChatModerationServices.deterministic();
        require(deterministic.moderate("오늘 점심 먹자").action() == ModerationAction.ALLOW, "normal message");
        require(deterministic.moderate("연락처는 010-1234-5678입니다").action() == ModerationAction.MASK,
                "bundled rules");
        require(deterministic.moderate("씨발").action() == ModerationAction.BLOCK, "bundled keyword resource");
        SemanticModerationProvider fake = message -> SemanticModerationResult.block(
                List.of(ModerationReason.HARASSMENT), 0.99, "packaged-consumer-fake");
        var semantic = ChatModerationServices.withSemanticProvider(
                deterministic, fake, ProviderFailurePolicy.DETERMINISTIC_FALLBACK);
        require(semantic.moderate("너는 정말 한심한 사람이야").action() == ModerationAction.BLOCK,
                "public semantic extension");
        var origin = ChatModerationServices.class.getProtectionDomain().getCodeSource().getLocation();
        require(origin.getPath().endsWith(".jar"), "classes must load from the production JAR");
        System.out.println("Packaged consumer passed: public API, bundled resources, fake provider, JAR origin.");
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
