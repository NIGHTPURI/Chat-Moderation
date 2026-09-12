package dev.chatmoderation.validation;

record ExpectedOutput(Kind kind, String value) {
    enum Kind {
        EXACT,
        NULL,
        NOT_ASSERTED
    }

    static ExpectedOutput exact(String value) {
        return new ExpectedOutput(Kind.EXACT, value);
    }

    static ExpectedOutput nullOutput() {
        return new ExpectedOutput(Kind.NULL, null);
    }

    static ExpectedOutput notAsserted() {
        return new ExpectedOutput(Kind.NOT_ASSERTED, null);
    }
}
