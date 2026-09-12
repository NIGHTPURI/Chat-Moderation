package dev.chatmoderation.core.matching;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class AhoCorasickKeywordMatcher implements KeywordMatcher {
    private final TrieNode root = new TrieNode();

    public AhoCorasickKeywordMatcher(Collection<String> keywords) {
        Objects.requireNonNull(keywords, "keywords must not be null");

        Set<String> uniqueKeywords = new LinkedHashSet<>();
        for (String keyword : keywords) {
            Objects.requireNonNull(keyword, "keyword must not be null");
            if (keyword.isEmpty()) {
                throw new IllegalArgumentException("keyword must not be empty");
            }
            uniqueKeywords.add(keyword);
        }

        uniqueKeywords.forEach(this::insert);
        buildFailureLinks();
    }

    @Override
    public List<KeywordMatch> findAll(String text) {
        Objects.requireNonNull(text, "text must not be null");

        List<KeywordMatch> matches = new ArrayList<>();
        TrieNode current = root;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            while (current != root && current.child(character) == null) {
                current = current.failure();
            }

            TrieNode next = current.child(character);
            current = next != null ? next : root;

            for (String keyword : current.outputs()) {
                int endIndex = index + 1;
                matches.add(new KeywordMatch(keyword, endIndex - keyword.length(), endIndex));
            }
        }

        return List.copyOf(matches);
    }

    private void insert(String keyword) {
        TrieNode current = root;
        for (int index = 0; index < keyword.length(); index++) {
            current = current.childOrCreate(keyword.charAt(index));
        }
        current.addOutput(keyword);
    }

    private void buildFailureLinks() {
        root.failure(root);
        Queue<TrieNode> queue = new ArrayDeque<>();

        for (Map.Entry<Character, TrieNode> transition : root.transitions()) {
            TrieNode child = transition.getValue();
            child.failure(root);
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            TrieNode parent = queue.remove();

            for (Map.Entry<Character, TrieNode> transition : parent.transitions()) {
                char character = transition.getKey();
                TrieNode child = transition.getValue();
                TrieNode fallback = parent.failure();

                while (fallback != root && fallback.child(character) == null) {
                    fallback = fallback.failure();
                }

                TrieNode fallbackChild = fallback.child(character);
                if (fallbackChild != null && fallbackChild != child) {
                    child.failure(fallbackChild);
                } else {
                    child.failure(root);
                }

                child.addOutputs(child.failure().outputs());
                queue.add(child);
            }
        }
    }
}
