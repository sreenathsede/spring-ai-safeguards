package com.dev.safeguards;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

import java.util.regex.Pattern;

public class PiiMaskingAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingAdvisor.class);

    private static final Pattern CARD  = Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b");
    private static final Pattern SSN   = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b");

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String original = request.prompt().getUserMessage().getText();
        String masked = mask(original);

        if (!masked.equals(original)) {
            log.info("PII masked before outbound call");
        }

        ChatClientRequest updated = request.mutate()
                .prompt(request.prompt().augmentUserMessage(masked))
                .build();
        return chain.nextCall(updated);
    }

    private String mask(String text) {
        text = CARD.matcher(text).replaceAll("[CARD REDACTED]");
        text = SSN.matcher(text).replaceAll("[SSN REDACTED]");
        text = EMAIL.matcher(text).replaceAll("[EMAIL REDACTED]");
        return text;
    }

    @Override
    public String getName() {
        return "PiiMaskingAdvisor";
    }

    @Override
    public int getOrder() {
        return 100; // higher precedence (lower number) than the logger, so the log shows MASKED text
    }
}
