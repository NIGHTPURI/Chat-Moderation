package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationReason;

import java.util.List;
import java.util.Objects;

public final class LocalHeuristicSemanticProvider implements SemanticModerationProvider {
    private static final String PROVIDER_NAME = "local-heuristic-v1";
    private static final List<String> BENIGN_CONTEXT = List.of(
            "보지 못", "보지 않", "자지 못", "자지 않", "애널리틱스",
            "병신년", "야동리", "야동초", "후장식"
    );
    private static final List<String> SEXUAL_CONTEXT = List.of(
            "몸 사진", "벗은 사진", "야한 사진", "같이 자자", "하룻밤",
            "뜨겁게 놀", "호텔로", "성적인 만남", "은밀한 사진",
            "성기를", "보여 달", "만져", "노출 사진"
    );
    private static final List<String> INSULT_CONTEXT = List.of(
            "쓸모없", "한심", "무능", "머리에 든", "인간도 아니", "사라져",
            "꺼져", "닥쳐", "부모도", "엄마도", "아빠도", "가족도",
            "급식충", "틀딱", "맘충", "한남", "한녀", "관종", "노답"
    );
    private static final List<String> ADVERTISEMENT_CONTEXT = List.of(
            "수익 보장", "무료 쿠폰", "선착순", "가입하면", "대출",
            "코인", "추천인", "구매하세요", "홍보 메시지"
    );

    @Override
    public SemanticModerationResult moderate(String message) {
        Objects.requireNonNull(message, "message must not be null");

        if (containsAny(message, BENIGN_CONTEXT)) {
            return success(SemanticModerationResult.Decision.ALLOW, List.of(), 0.92);
        }
        if (containsAny(message, SEXUAL_CONTEXT)) {
            return success(
                    SemanticModerationResult.Decision.BLOCK,
                    List.of(ModerationReason.SEXUAL_CONTENT),
                    0.86
            );
        }
        if (containsAny(message, INSULT_CONTEXT)) {
            return success(
                    SemanticModerationResult.Decision.BLOCK,
                    List.of(ModerationReason.PROFANITY),
                    0.84
            );
        }
        if (containsAny(message, ADVERTISEMENT_CONTEXT)) {
            return success(
                    SemanticModerationResult.Decision.BLOCK,
                    List.of(ModerationReason.SPAM),
                    0.82
            );
        }
        return success(SemanticModerationResult.Decision.ALLOW, List.of(), 0.55);
    }

    private SemanticModerationResult success(
            SemanticModerationResult.Decision decision,
            List<ModerationReason> reasons,
            double confidence
    ) {
        return new SemanticModerationResult(
                decision,
                reasons,
                confidence,
                SemanticModerationResult.Status.SUCCESS,
                PROVIDER_NAME
        );
    }

    private boolean containsAny(String message, List<String> candidates) {
        return candidates.stream().anyMatch(message::contains);
    }
}
