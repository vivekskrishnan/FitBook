package com.vivekkrishnan.fitbook.ai.agent1;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Serves the chat page (customer-only, see SecurityConfig). The page talks to POST /ai/chat.
@Controller
public class ChatPageController {

    @GetMapping("/ai/chat")
    public String chatPage() {
        return "chat";
    }
}
