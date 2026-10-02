package com.dev.safeguards;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

/**
 * Output-side guardrail. Lets the model answer, then scans the response for
 * secrets before it reaches the caller. An input filter has to anticipate every
 * way a user might ask; this only has to check whether the answer contains the
 * one string that must never leave.
 */
public class SecretLeakAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SecretLeakAdvisor.class);
    private final List<String> secrets;

    public SecretLeakAdvisor(List<String> secrets) {
        this.secrets = secrets;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);

        String reply = response.chatResponse().getResult().getOutput().getText();
        boolean leaked = reply != null && secrets.stream()
                .anyMatch(s -> reply.toLowerCase().contains(s.toLowerCase()));

        if (!leaked) {
            return response;
        }

        log.warn("Secret found in model response, replacing it before it reaches the caller");
        ChatResponse safe = new ChatResponse(
                List.of(new Generation(new AssistantMessage("I'm not able to share that."))));
        return ChatClientResponse.builder()
                .chatResponse(safe)
                .context(response.context())
                .build();
    }

    @Override
    public String getName() {
        return "SecretLeakAdvisor";
    }

    @Override
    public int getOrder() {
        // Between the input filter (0) and the logger (200): the logger sits
        // closer to the model, so the console still shows the raw leak while
        // this advisor redacts it on the way back to the caller.
        return 100;
    }
}
