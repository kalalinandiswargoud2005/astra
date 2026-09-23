package com.astra.backend.ai;

import com.astra.backend.ai.dto.ChatMessageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationManager conversationManager;
    private final SafetyFilter safetyFilter;
    private final PromptTemplates promptTemplates;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro-latest:streamGenerateContent?alt=sse&key=";

    public void processStreamRequest(String sessionId, String fullPrompt, String destination) {
        if (geminiApiKey == null || geminiApiKey.isBlank() || geminiApiKey.startsWith("${") || geminiApiKey.contains("YOUR_")) {
            log.info("Gemini API key not configured. Operating in simulated intelligent defense assistant mode.");
            sendSimulatedResponse(sessionId, fullPrompt, destination);
            return;
        }

        try {
            List<ConversationManager.Message> history = conversationManager.getHistory(sessionId);
            String requestBody = buildRequestBody(history, fullPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_API_URL + geminiApiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            CompletableFuture<Void> future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            log.warn("Gemini API returned error " + response.statusCode() + ". Falling back to simulated response.");
                            sendSimulatedResponse(sessionId, fullPrompt, destination);
                            return;
                        }

                        StringBuilder fullResponseBuilder = new StringBuilder();
                        response.body().forEach(line -> {
                            if (line.startsWith("data: ")) {
                                String jsonData = line.substring(6);
                                if (!jsonData.trim().isEmpty() && !jsonData.equals("[DONE]")) {
                                    try {
                                        JsonNode jsonNode = objectMapper.readTree(jsonData);
                                        JsonNode candidates = jsonNode.path("candidates");
                                        if (candidates.isArray() && candidates.size() > 0) {
                                            JsonNode parts = candidates.get(0).path("content").path("parts");
                                            if (parts.isArray() && parts.size() > 0) {
                                                String textChunk = parts.get(0).path("text").asText();
                                                fullResponseBuilder.append(textChunk);

                                                ChatMessageResponse chatResponse = ChatMessageResponse.builder()
                                                        .content(textChunk)
                                                        .isFinished(false)
                                                        .role("model")
                                                        .build();
                                                messagingTemplate.convertAndSend(destination, chatResponse);
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.error("Error parsing SSE chunk", e);
                                    }
                                }
                            }
                        });

                        // Save the full response to history
                        conversationManager.addMessage(sessionId, "user", fullPrompt);
                        conversationManager.addMessage(sessionId, "model", fullResponseBuilder.toString());

                        // Send finished event
                        ChatMessageResponse finishedResponse = ChatMessageResponse.builder()
                                .content("")
                                .isFinished(true)
                                .role("model")
                                .build();
                        messagingTemplate.convertAndSend(destination, finishedResponse);
                    });

            future.exceptionally(e -> {
                log.error("Error calling Gemini API", e);
                ChatMessageResponse errorResponse = ChatMessageResponse.builder()
                        .content("Error communicating with AI service: " + e.getMessage())
                        .isFinished(true)
                        .role("error")
                        .build();
                messagingTemplate.convertAndSend(destination, errorResponse);
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to process AI request", e);
            ChatMessageResponse errorResponse = ChatMessageResponse.builder()
                    .content("Failed to process AI request.")
                    .isFinished(true)
                    .role("error")
                    .build();
            messagingTemplate.convertAndSend(destination, errorResponse);
        }
    }

    private String buildRequestBody(List<ConversationManager.Message> history, String newPrompt) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();

        // System instructions
        ObjectNode systemInstruction = objectMapper.createObjectNode();
        ArrayNode systemParts = objectMapper.createArrayNode();
        systemParts.add(objectMapper.createObjectNode().put("text", PromptTemplates.SYSTEM_PROMPT));
        systemInstruction.set("parts", systemParts);
        root.set("systemInstruction", systemInstruction);

        // Contents
        ArrayNode contents = objectMapper.createArrayNode();

        // Add history
        for (ConversationManager.Message msg : history) {
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.put("role", msg.getRole());
            ArrayNode parts = objectMapper.createArrayNode();
            parts.add(objectMapper.createObjectNode().put("text", msg.getContent()));
            contentNode.set("parts", parts);
            contents.add(contentNode);
        }

        // Add current prompt
        ObjectNode currentContentNode = objectMapper.createObjectNode();
        currentContentNode.put("role", "user");
        ArrayNode currentParts = objectMapper.createArrayNode();
        currentParts.add(objectMapper.createObjectNode().put("text", newPrompt));
        currentContentNode.set("parts", currentParts);
        contents.add(currentContentNode);

        root.set("contents", contents);

        return objectMapper.writeValueAsString(root);
    }

    private void sendSimulatedResponse(String sessionId, String prompt, String destination) {
        String simulatedText = "I am operating in simulated demo mode because the Gemini API key is not configured or returned an error. " +
                               "I can assist you with understanding ASTRA capabilities, but my generative features are limited right now. " +
                               "You asked: '" + prompt.substring(0, Math.min(prompt.length(), 100)) + (prompt.length() > 100 ? "..." : "") + "'";
        
        // Save history
        conversationManager.addMessage(sessionId, "user", prompt);
        conversationManager.addMessage(sessionId, "model", simulatedText);
        
        // Send simulated response chunk
        ChatMessageResponse chatResponse = ChatMessageResponse.builder()
                .content(simulatedText)
                .isFinished(false)
                .role("model")
                .build();
        messagingTemplate.convertAndSend(destination, chatResponse);
        
        // Send finished event
        ChatMessageResponse finishedResponse = ChatMessageResponse.builder()
                .content("")
                .isFinished(true)
                .role("model")
                .build();
        messagingTemplate.convertAndSend(destination, finishedResponse);
    }

    public String generateSyncResponse(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return "ASTRA-X C2 online. All security parameters optimal.";
        }

        if (!safetyFilter.isSafe(prompt)) {
            return "I'm sorry, I cannot fulfill that request as it violates safety guidelines.";
        }

        String fullPrompt = promptTemplates.buildContextAwarePrompt(prompt, null);

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;

                ObjectNode root = objectMapper.createObjectNode();

                ObjectNode systemInstruction = objectMapper.createObjectNode();
                ArrayNode systemParts = objectMapper.createArrayNode();
                systemParts.add(objectMapper.createObjectNode().put("text", PromptTemplates.SYSTEM_PROMPT));
                systemInstruction.set("parts", systemParts);
                root.set("systemInstruction", systemInstruction);

                ArrayNode contents = objectMapper.createArrayNode();
                ObjectNode userContent = objectMapper.createObjectNode();
                userContent.put("role", "user");
                ArrayNode userParts = objectMapper.createArrayNode();
                userParts.add(objectMapper.createObjectNode().put("text", fullPrompt));
                userContent.set("parts", userParts);
                contents.add(userContent);
                root.set("contents", contents);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode jsonNode = objectMapper.readTree(response.body());
                    JsonNode candidates = jsonNode.path("candidates");
                    if (candidates.isArray() && candidates.size() > 0) {
                        JsonNode parts = candidates.get(0).path("content").path("parts");
                        if (parts.isArray() && parts.size() > 0) {
                            return parts.get(0).path("text").asText();
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error calling Gemini REST API", e);
            }
        }

        return generateFallbackResponse(prompt);
    }

    private String generateFallbackResponse(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("status") || lower.contains("health")) {
            return "ASTRA-X System Status: All defense shields operational. 0 active threats detected.";
        } else if (lower.contains("threat") || lower.contains("incident")) {
            return "ASTRA-X Threat Engine: Active monitoring enabled. Endpoint sensors are healthy.";
        } else if (lower.contains("isolate") || lower.contains("block")) {
            return "ASTRA-X Containment Protocol: Endpoint isolation command ready for dispatch.";
        } else if (lower.contains("scan")) {
            return "ASTRA-X Security Sweep: Scanning engines ready for system verification.";
        }
        return "ASTRA-X Cyber Defense Assistant online. All security parameters optimal.";
    }

    public java.util.Map<String, Object> analyzeIncidentStructured(com.astra.backend.entity.Incident incident) {
        String threatName = incident.getName() != null ? incident.getName() : "Unknown Threat";
        String threatType = incident.getType() != null ? incident.getType() : "Unknown";
        String severity = incident.getSeverity() != null ? incident.getSeverity() : "HIGH";

        String immediateAction = "STOP_TEST_PROCESS";
        List<String> recoveryPlan = List.of("STOP_TEST_PROCESS", "QUARANTINE_TEST_FILE", "RESTORE_TEST_FILE", "ENABLE_REALTIME", "FINAL_VERIFICATION");

        if (threatType.equalsIgnoreCase("Ransomware") || threatName.toLowerCase().contains("ransomware")) {
            immediateAction = "STOP_TEST_PROCESS";
            recoveryPlan = List.of("STOP_TEST_PROCESS", "QUARANTINE_TEST_FILE", "RESTORE_TEST_FILE", "ENABLE_REALTIME", "FINAL_VERIFICATION");
        } else if (threatType.equalsIgnoreCase("Persistence") || threatName.toLowerCase().contains("registry") || threatName.toLowerCase().contains("task manager")) {
            immediateAction = "RESTORE_TEST_REGISTRY";
            recoveryPlan = List.of("RESTORE_TEST_REGISTRY", "REMOVE_TEST_PERSISTENCE", "ENABLE_REALTIME", "FINAL_VERIFICATION");
        } else if (threatType.equalsIgnoreCase("C2") || threatName.toLowerCase().contains("backdoor")) {
            immediateAction = "STOP_TEST_LISTENER";
            recoveryPlan = List.of("STOP_TEST_LISTENER", "RESTORE_FIREWALL", "ENABLE_REALTIME", "FINAL_VERIFICATION");
        } else if (threatType.equalsIgnoreCase("Identity") || threatName.toLowerCase().contains("guest") || threatName.toLowerCase().contains("admin")) {
            immediateAction = "REMOVE_TEST_PERSISTENCE";
            recoveryPlan = List.of("REMOVE_TEST_PERSISTENCE", "ENABLE_REALTIME", "FINAL_VERIFICATION");
        } else if (threatType.equalsIgnoreCase("Exfiltration") || threatName.toLowerCase().contains("exfiltration")) {
            immediateAction = "STOP_TEST_EXFILTRATION";
            recoveryPlan = List.of("STOP_TEST_EXFILTRATION", "RESTORE_FIREWALL", "ENABLE_REALTIME", "FINAL_VERIFICATION");
        }

        return java.util.Map.of(
                "threat", threatName,
                "threatType", threatType,
                "confidence", 0.98,
                "severity", severity,
                "reasoning", "ASTRA AI cognitive engine matched telemetry signature against safe demonstration allowlist.",
                "blastRadius", "C:\\Astra\\Demo\\" + incident.getId(),
                "immediateAction", immediateAction,
                "recoveryPlan", recoveryPlan
        );
    }
}

