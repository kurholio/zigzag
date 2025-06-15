package com.zigzag.predictor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zigzag.data.ZZPoint;
import com.zigzag.data.ZZTradePrediction;


public class OpenAIPredictor {
	  private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
	  private static final String OPENAI_AK = "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";
	  private static final ObjectMapper mapper = new ObjectMapper();

	
	public static ZZTradePrediction getPredictionFromGPT(List<ZZPoint> enrichedPoints) throws IOException, InterruptedException {
        String inputJson = mapper.writeValueAsString(enrichedPoints);

        String prompt = "Based on the provided enriched ZZPoint data, "
        		+ "suggest the next BUY and SELL price targets along with confidence levels (0-1). Also repeat this original prompt "
        		+ " and return JSON like this: {\"buyPrice\":..., \"sellPrice\":..., \"buyConfidence\":..., \"sellConfidence\":..., \"rationale\":..., \"prompt\"...}";

        Map<String, Object> request = Map.of(
                "model", "gpt-4.1",
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a financial trading assistant."),
                        Map.of("role", "user", "content", prompt + "\n\n" + inputJson)
                ),
                "temperature", 0.3
        );

        String requestBody = mapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + OPENAI_AK)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        String jsonResponse = response.body();
        String rawContent = extractContent(jsonResponse);
        String jsonContent = extractJsonBlock(rawContent);


        return mapper.readValue(jsonContent, ZZTradePrediction.class);
    }

	private static String extractContent(String fullResponse) throws IOException {
	    Map<String, Object> tree = mapper.readValue(fullResponse, new TypeReference<>() {});
	    List<Map<String, Object>> choices = (List<Map<String, Object>>) tree.get("choices");
	    if (choices.isEmpty()) throw new RuntimeException("No choices returned");
	    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
	    return message.get("content").toString().trim();
	}

	private static String extractJsonBlock(String raw) {
	    int start = raw.indexOf('{');
	    int end = raw.lastIndexOf('}');
	    if (start == -1 || end == -1 || end <= start) {
	        throw new RuntimeException("Failed to find valid JSON object in response");
	    }
	    return raw.substring(start, end + 1);
	}
}
