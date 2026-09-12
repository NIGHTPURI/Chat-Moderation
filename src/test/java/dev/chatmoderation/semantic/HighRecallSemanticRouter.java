package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class HighRecallSemanticRouter {
    private static final List<String> REPORT_MARKERS = List.of(
            "신고", "제보", "피해", "상담 기록", "요구받", "들었습니다",
            "들었어요", "받았습니다", "권유받", "불렸습니다"
    );
    private static final List<String> QUOTE_OR_EDUCATION_MARKERS = List.of(
            "인용", "소설 속", "대사", "따옴표", "표현의 예시", "언어 수업",
            "교육 자료", "정책 강의", "문법 교재", "지도 검색", "안내 책자",
            "연혁 자료", "공학 보고서", "의학 세미나", "학술 논문", "자료형",
            "분석합니다", "설명합니다", "연구합니다", "정리합니다", "소개합니다"
    );
    private static final List<String> SECOND_PERSON = List.of(
            "너는", "넌", "너 ", "너를", "너한테", "네가", "네 ", "너희", "당신"
    );
    private static final List<String> NEGATIVE_EVALUATION = List.of(
            "쓸모없", "한심", "무능", "생각할 머리", "머리가 부족", "가치가 없",
            "생각이라는 게 없", "민폐", "수준이 어느 정도", "수준이 뻔", "수준이 보",
            "같은 수준", "답이 없", "모자란",
            "사람 구실", "능력도 없", "아무도 맡기", "주변이 왜 힘든", "부끄러울",
            "예의가 없", "기대하지 않", "포기했", "망치는", "판단력이", "형편없",
            "기본도 모르", "쓸모도 없", "지치게", "최악", "사라져", "꺼져", "닥쳐"
    );
    private static final List<String> ATTACK_FRAME = List.of(
            "그런 판단", "그 행동", "방금 한 말", "처리하는 꼴", "하는 꼴",
            "보면 볼수록", "말투", "태도", "사람 같", "인간", "존재"
    );
    private static final List<String> FAMILY = List.of(
            "부모", "엄마", "아빠", "가족", "집안", "형제"
    );
    private static final List<String> SEXUAL_CONTEXT = List.of(
            "야한", "옷을 벗", "벗은 모습", "몸 사진", "몸을 찍", "같이 자",
            "자러 가", "은밀한", "성적인", "성적 요구", "호텔", "만지고",
            "숙소", "몸매", "옷 안쪽", "강제로", "성적 대상", "신체를", "노출 사진"
    );
    private static final List<String> REQUEST_OR_TARGETING = List.of(
            "보내 줘", "보내", "보여 줘", "보여", "공유해", "가자", "만나자",
            "하고 싶", "싶어", "싶어진", "궁금해", "평가하고", "가까이 가", "싫다고 해도",
            "대답을 피할수록", "채팅하는 내내"
    );
    private static final List<String> COMMERCIAL = List.of(
            "등록", "신청", "입장", "추천 코드", "추천인", "고수익", "수익 보장",
            "무료 상품", "쿠폰", "대출", "급등 종목", "특가", "구매", "결제",
            "한정 혜택", "현금 포인트"
    );
    private static final List<String> COMMERCIAL_ACTION = List.of(
            "보장", "지급", "도와드", "알려드", "가능", "드립니다", "입력", "클릭",
            "받으려면", "하세요"
    );
    private static final List<String> DEROGATORY_SLANG = List.of(
            "급식충", "틀딱", "맘충", "한남", "한녀", "노답", "관종", "꼰대",
            "잼민이", "김치녀", "루저", "찐따"
    );

    public HighRecallSemanticRoute route(String message, ModerationResult deterministicResult) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(deterministicResult, "deterministicResult must not be null");
        if (deterministicResult.action() != ModerationAction.ALLOW) {
            return localFinal();
        }

        String canonical = message.strip();
        String normalized = canonical.toLowerCase(Locale.ROOT);
        if (clearlyQuotedOrEducational(normalized)
                || clearlyReportedWithoutDirectAttack(normalized)) {
            return localFinal();
        }

        boolean secondPerson = containsAny(normalized, SECOND_PERSON);
        boolean negative = containsAny(normalized, NEGATIVE_EVALUATION);
        boolean familyAttack = containsAny(normalized, FAMILY)
                && (secondPerson || negative);
        boolean sexualCandidate = containsAny(normalized, SEXUAL_CONTEXT)
                && (secondPerson || containsAny(normalized, REQUEST_OR_TARGETING));
        boolean advertisement = containsAny(normalized, COMMERCIAL)
                && containsAny(normalized, COMMERCIAL_ACTION);
        boolean slangAttack = containsAny(normalized, DEROGATORY_SLANG);

        boolean structuralInsult = containsAny(normalized, ATTACK_FRAME) && negative;
        boolean productiveSlang = secondPerson && normalized.matches(".*[가-힣]{1,6}충.*");

        return secondPerson && negative || structuralInsult || negative || familyAttack
                || sexualCandidate || advertisement || slangAttack || productiveSlang
                ? semantic(canonical)
                : localFinal();
    }

    private boolean clearlyQuotedOrEducational(String message) {
        return containsAny(message, QUOTE_OR_EDUCATION_MARKERS);
    }

    private boolean clearlyReportedWithoutDirectAttack(String message) {
        if (!containsAny(message, REPORT_MARKERS)) {
            return false;
        }
        return !containsAny(message, SECOND_PERSON);
    }

    private boolean containsAny(String message, List<String> signals) {
        return signals.stream().anyMatch(message::contains);
    }

    private HighRecallSemanticRoute localFinal() {
        return new HighRecallSemanticRoute(HighRecallRoutingDecision.LOCAL_FINAL, null);
    }

    private HighRecallSemanticRoute semantic(String message) {
        return new HighRecallSemanticRoute(
                HighRecallRoutingDecision.NEEDS_SEMANTIC_REVIEW,
                message
        );
    }
}
