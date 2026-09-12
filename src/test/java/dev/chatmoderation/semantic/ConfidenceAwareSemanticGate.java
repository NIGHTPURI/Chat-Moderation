package dev.chatmoderation.semantic;

import dev.chatmoderation.core.model.ModerationAction;
import dev.chatmoderation.core.model.ModerationResult;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Phase 3.15 experiment. Selects semantic candidates but never makes a semantic decision. */
public final class ConfidenceAwareSemanticGate {
    private static final List<String> CONTEXT_MARKERS = List.of(
            "신고", "제보", "피해", "상담", "증거", "기록", "인용", "따옴표",
            "대사", "가사", "소설", "기사", "보도", "발언", "들었다", "들었",
            "받았다", "받았", "요구받", "권유받", "불렸", "라고 했", "라고 말",
            "표현", "예문", "사례", "교육", "수업", "강의", "교재", "연구",
            "논문", "보고서", "분석", "설명", "정의", "분류", "역사", "연혁",
            "사전", "문법", "정책", "필터", "탐지", "오탐", "번역"
    );
    private static final List<String> AMBIGUOUS_OR_NAMED = List.of(
            "병신년", "병신일", "야동리", "야동동", "야동초", "야동역", "야동천",
            "보지산", "보지암", "보지리", "자지포", "자지동", "후장식", "후장동",
            "후장리", "성기능", "성기 조직", "성기 구조", "항문", "sex 변수",
            "sex 필드", "sex column", "penetration test", "스팸 필터"
    );
    private static final List<String> TARGET_MARKERS = List.of(
            "너", "네가", "네 ", "니가", "니 ", "당신", "그쪽", "너희", "니들",
            "상대", "쟤", "걔", "저 인간", "이 인간"
    );
    private static final List<String> INSULT_OR_REJECTION = List.of(
            "쓸모", "한심", "무능", "멍청", "바보", "수준", "가치가 없", "가치 없",
            "답이 없", "노답", "민폐", "생각이 없", "생각도 없", "머리가 없",
            "머리도 없", "판단력", "사람 구실", "인간 구실", "기본도 모르",
            "형편없", "최악", "역겹", "징그럽", "추하", "모자라", "저급",
            "무식", "예의 없", "예의가 없", "꼴", "주변을 힘들", "지치게",
            "사라져", "꺼져", "닥쳐", "말 걸지 마", "대화할 필요", "상종"
    );
    private static final List<String> FAMILY = List.of(
            "부모", "엄마", "아빠", "어머니", "아버지", "가족", "집안", "가정교육",
            "형제", "자매", "애미", "애비"
    );
    private static final List<String> SEXUAL = List.of(
            "야한", "노출", "몸 사진", "몸사진", "벗은 사진", "사진 보내", "옷을 벗",
            "벗어", "같이 자", "자고 싶", "하룻밤", "호텔", "숙소", "모텔",
            "은밀", "성적", "몸매", "가슴", "신체", "만지고", "만져", "키스",
            "영상 통화", "영상통화", "성관계", "잠자리", "강제로 가까이", "옷 안쪽"
    );
    private static final List<String> SEXUAL_DIRECTION = List.of(
            "보내", "보여", "공유", "와 줘", "와줘", "오라고", "가자", "만나자",
            "하자", "자자", "와서", "해 줘", "해줘", "원해", "싶어", "싶다", "궁금", "평가",
            "상상", "대상", "거절", "싫다고", "강제로", "몰래", "계속 요구"
    );
    private static final List<String> COMMERCIAL = List.of(
            "가입", "등록", "신청", "결제", "구매", "추천 코드", "추천코드", "추천인",
            "수익", "고수익", "투자", "코인", "주식", "종목", "대출", "쿠폰",
            "포인트", "무료 상품", "한정 혜택", "특가", "공동구매", "오픈채팅",
            "비밀방", "채널 입장", "링크 클릭"
    );
    private static final List<String> CALL_TO_ACTION = List.of(
            "하세요", "해라", "눌러", "클릭", "입력", "참여", "입장", "문의",
            "보장", "지급", "드립니다", "드려요", "받으", "알려드", "도와드",
            "가능합니다", "마감", "선착순"
    );
    private static final List<String> DEROGATORY_SLANG = List.of(
            "급식충", "틀딱", "맘충", "한남", "한녀", "김치녀", "된장녀", "잼민이",
            "찐따", "루저", "관종", "꼰대", "폐급", "빌런", "프로불편러"
    );
    private static final Pattern PRODUCTIVE_SLUR = Pattern.compile(
            ".*(?:너|니|당신|쟤|걔).{0,12}[가-힣]{1,8}(?:충|빠|까)(?:\\s|$|[.,!?]).*"
    );

    public ConfidenceAwareSemanticRoute route(
            String message,
            ModerationResult deterministicResult
    ) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(deterministicResult, "deterministicResult must not be null");
        String canonical = message.strip();
        String normalized = canonical.toLowerCase(Locale.ROOT);

        if (deterministicResult.action() == ModerationAction.MASK) {
            return local(ConfidenceGateState.CERTAIN_MASK, deterministicResult);
        }
        if (deterministicResult.action() == ModerationAction.BLOCK) {
            return contextSensitiveDeterministicBlock(normalized)
                    ? review(canonical, deterministicResult)
                    : local(ConfidenceGateState.CERTAIN_BLOCK, deterministicResult);
        }
        return semanticRiskAllow(normalized)
                ? review(canonical, deterministicResult)
                : local(ConfidenceGateState.CERTAIN_ALLOW, deterministicResult);
    }

    private boolean contextSensitiveDeterministicBlock(String message) {
        return containsAny(message, AMBIGUOUS_OR_NAMED)
                || containsAny(message, CONTEXT_MARKERS);
    }

    private boolean semanticRiskAllow(String message) {
        boolean target = containsAny(message, TARGET_MARKERS);
        boolean insult = containsAny(message, INSULT_OR_REJECTION);
        boolean family = containsAny(message, FAMILY);
        boolean sexual = containsAny(message, SEXUAL);
        boolean commercial = containsAny(message, COMMERCIAL);
        boolean slang = containsAny(message, DEROGATORY_SLANG)
                || PRODUCTIVE_SLUR.matcher(message + " ").matches();
        return insult || family && (target || insult)
                || sexual && (target || containsAny(message, SEXUAL_DIRECTION))
                || commercial && containsAny(message, CALL_TO_ACTION)
                || slang;
    }

    private boolean containsAny(String message, List<String> signals) {
        return signals.stream().anyMatch(message::contains);
    }

    private ConfidenceAwareSemanticRoute local(
            ConfidenceGateState state,
            ModerationResult result
    ) {
        return new ConfidenceAwareSemanticRoute(state, result, null);
    }

    private ConfidenceAwareSemanticRoute review(String message, ModerationResult result) {
        return new ConfidenceAwareSemanticRoute(
                ConfidenceGateState.NEEDS_SEMANTIC_REVIEW,
                result,
                message
        );
    }
}
