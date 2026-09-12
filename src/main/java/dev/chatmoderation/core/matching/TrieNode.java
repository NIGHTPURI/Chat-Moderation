package dev.chatmoderation.core.matching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TrieNode {
    private final Map<Character, TrieNode> children = new HashMap<>();
    private final List<String> outputs = new ArrayList<>();
    private TrieNode failure;

    TrieNode child(char character) {
        return children.get(character);
    }

    TrieNode childOrCreate(char character) {
        return children.computeIfAbsent(character, ignored -> new TrieNode());
    }

    Iterable<Map.Entry<Character, TrieNode>> transitions() {
        return children.entrySet();
    }

    void addOutput(String keyword) {
        outputs.add(keyword);
    }

    void addOutputs(List<String> keywords) {
        outputs.addAll(keywords);
    }

    List<String> outputs() {
        return outputs;
    }

    TrieNode failure() {
        return failure;
    }

    void failure(TrieNode failure) {
        this.failure = failure;
    }
}
