package com.example.stardust_springboot.conversation.memory;

import org.springframework.stereotype.Component;

/** Conservative provider-independent token counter for hard prompt budgeting. */
@Component
public class TokenCounter {
    public int count(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int ascii = 0;
        int nonAscii = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (codePoint <= 0x7f) {
                ascii++;
            } else {
                nonAscii++;
            }
            offset += Character.charCount(codePoint);
        }
        return Math.max(1, (ascii + 3) / 4 + nonAscii);
    }

    public int countMessage(String role, String content) {
        return 4 + count(role) + count(content);
    }
}
