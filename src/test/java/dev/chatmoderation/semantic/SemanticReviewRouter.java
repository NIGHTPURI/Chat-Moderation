package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationReason;
import dev.chatmoderation.core.model.ModerationResult;

import java.util.List;
import java.util.Objects;

public final class SemanticReviewRouter {
    private static final List<String> REVIEW_TRIGGERS = List.of(
            "보지", "자지", "애널", "병신년", "야동리", "야동초", "후장식",
            "쓸모없", "한심", "무능", "머리에 든", "인간도 아니", "사라져",
            "꺼져", "닥쳐", "부모", "엄마", "아빠", "가족",
            "몸 사진", "벗은 사진", "야한 사진", "같이 자자", "하룻밤",
            "뜨겁게 놀", "호텔로", "성적인 만남", "은밀한 사진",
            "수익 보장", "무료 쿠폰", "선착순", "가입하면", "대출",
            "코인", "추천인", "구매하세요", "홍보 메시지",
            "급식충", "틀딱", "맘충", "한남", "한녀", "관종", "노답"
    );
    private static final List<String> AMBIGUOUS_BLOCK_TRIGGERS = List.of(
            "병신년", "야동리", "야동초", "후장식"
    );
    private static final List<String> CLEAR_BLOCK_MARKERS = List.of(
            "시발", "씨발", "지랄", "염병", "옘병", "병신", "존나",
            "개년아", "이 개년", "이개년", "개년같", "개새끼", "좆", "느금마",
            "섹스", "야동", "포르노", "딸딸이", "딸치", "오나니", "후장",
            "ㅅㅂ", "ㅆㅂ", "ㅈㄴ", "씨아발"
    );

    public SemanticRoute route(String message, ModerationResult deterministicResult) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(deterministicResult, "deterministicResult must not be null");

        String canonicalMessage = message.strip();
        if (deterministicResult.action() == ModerationAction.BLOCK) {
            if (isAmbiguousOnlyBlock(canonicalMessage, deterministicResult)) {
                return new SemanticRoute(
                        SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW,
                        deterministicResult,
                        canonicalMessage
                );
            }
            return new SemanticRoute(
                    SemanticRoutingDecision.CLEAR_BLOCK,
                    deterministicResult,
                    null
            );
        }

        if (containsAny(canonicalMessage, REVIEW_TRIGGERS)) {
            String semanticMessage = deterministicResult.action() == ModerationAction.MASK
                    ? deterministicResult.outputMessage()
                    : canonicalMessage;
            return new SemanticRoute(
                    SemanticRoutingDecision.NEEDS_SEMANTIC_REVIEW,
                    deterministicResult,
                    semanticMessage
            );
        }

        SemanticRoutingDecision decision = deterministicResult.action() == ModerationAction.MASK
                ? SemanticRoutingDecision.CLEAR_MASK
                : SemanticRoutingDecision.CLEAR_ALLOW;
        return new SemanticRoute(decision, deterministicResult, null);
    }

    private boolean containsAny(String message, List<String> candidates) {
        return candidates.stream().anyMatch(message::contains);
    }

    private boolean isAmbiguousOnlyBlock(
            String message,
            ModerationResult deterministicResult
    ) {
        if (deterministicResult.reasons().size() != 1
                || !containsAny(message, AMBIGUOUS_BLOCK_TRIGGERS)) {
            return false;
        }
        ModerationReason reason = deterministicResult.reasons().getFirst();
        if (reason != ModerationReason.PROFANITY
                && reason != ModerationReason.SEXUAL_CONTENT) {
            return false;
        }

        String remainder = message;
        for (String ambiguous : AMBIGUOUS_BLOCK_TRIGGERS) {
            remainder = remainder.replace(ambiguous, "");
        }
        return !containsAny(remainder, CLEAR_BLOCK_MARKERS);
    }
}
