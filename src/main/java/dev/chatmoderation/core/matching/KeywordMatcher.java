package dev.chatmoderation.core.matching;

import java.util.List;

public interface KeywordMatcher {
    List<KeywordMatch> findAll(String text);
}
