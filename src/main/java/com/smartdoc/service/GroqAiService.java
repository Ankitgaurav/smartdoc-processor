package com.smartdoc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Calls Groq AI API to extract structured data from document text.
 *
 * Uses RestTemplate (synchronous HTTP client — no extra dependency needed).
 * Groq API format is identical to OpenAI chat completions API.
 */
@Service
@Slf4j
public class GroqAiService {

    @Value("${app.groq.api-key}")
    private String apiKey;

    @Value("${app.groq.api-url}")
    private String apiUrl;

    @Value("${app.groq.model}")
    private String model;

    @Value("${app.groq.max-tokens}")
    private int maxTokens;

    // Created directly — no injection needed for RestTemplate
    private final RestTemplate restTemplate = new RestTemplate();

    public String extractStructuredData(String documentText, String documentName) {
        log.info("Calling Groq AI for document: {}", documentName);

        String truncatedText = documentText.length() > 3000
                ? documentText.substring(0, 3000) + "...[truncated]"
                : documentText;

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "You are a document data extraction assistant. Always respond with valid JSON only. No explanations."),
                        Map.of("role", "user",
                                "content", buildPrompt(truncatedText, documentName))
                )
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> responseEntity = restTemplate.postForEntity(apiUrl, entity, Map.class);

            String content = extractContentFromResponse(responseEntity.getBody());
            log.info("Groq extraction successful for: {}", documentName);
            return content;

        } catch (Exception e) {
            log.error("Groq API call failed for {}: {}", documentName, e.getMessage());
            return String.format(
                    "{\"error\":\"AI extraction failed\",\"reason\":\"%s\",\"filename\":\"%s\"}",
                    e.getMessage().replace("\"", "'"), documentName
            );
        }
    }

    private String buildPrompt(String text, String filename) {
        return String.format("""
            Analyze this document and extract key information as JSON.

            Document filename: %s
            Document content:
            ---
            %s
            ---

            Return a JSON object with these fields (use null if not found):
            {
              "documentType": "invoice/resume/contract/report/other",
              "title": "document title or subject",
              "date": "date found in document",
              "keyEntities": ["list of names, companies, or organizations"],
              "summary": "2-3 sentence summary of the document",
              "extractedFields": {
                "any key-value pairs relevant to this document type"
              }
            }

            Return ONLY the JSON. No markdown, no explanation.
            """, filename, text);
    }

    @SuppressWarnings("unchecked")
    private String extractContentFromResponse(Map<String, Object> response) {
        if (response == null) throw new RuntimeException("Null response from Groq API");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new RuntimeException("No choices in Groq response");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");

        // Groq sometimes wraps response in markdown code blocks — strip them
        // Input:  ```json\n{ ... }\n```
        // Output: { ... }
        if (content != null) {
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("^```[a-zA-Z]*\\n?", ""); // remove opening ```json
                content = content.replaceAll("```$", "").trim();        // remove closing ```
            }
        }
        return content;
    }
}