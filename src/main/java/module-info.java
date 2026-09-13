module dev.chatmoderation {
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    exports dev.chatmoderation.core;
    exports dev.chatmoderation.core.model;
    exports dev.chatmoderation.semantic;
    exports dev.chatmoderation.semantic.openai;
}
