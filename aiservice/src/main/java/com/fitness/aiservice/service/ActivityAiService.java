package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ActivityAiService {
    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity){
        String prompt = createPromptForActivity(activity);
        String aiResponse = geminiService.getAnswer(prompt);
        Recommendation recommendation = processAiResponse(activity,aiResponse);
        return recommendation;
    }
    public Recommendation processAiResponse(Activity activity,String aiResponse){
        try{
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(aiResponse);

            JsonNode textNode = jsonNode.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text");

            String jsonContent = textNode.asText()
                    .replaceAll("```json\\n","")
                    .replaceAll("\\n```","")
                    .trim();

//            System.out.println(jsonContent);

            JsonNode parsedJson = objectMapper.readTree(jsonContent);
            JsonNode analysis = parsedJson.path("analysis");

            StringBuilder fullAnalysis = new StringBuilder();
            addAnalysisSection(fullAnalysis,analysis,"overall","Overall:");
            addAnalysisSection(fullAnalysis,analysis,"pace","Pace:");
            addAnalysisSection(fullAnalysis,analysis,"heartRate","Heart Rate:");
            addAnalysisSection(fullAnalysis,analysis,"caloriesBurned","Calories Burned:");

            List<String> improvements = extractImprovements(parsedJson.path("improvements"));
            List<String> suggestions = extractSuggestions(parsedJson.path("suggestions"));
            List<String> safety = extractSafety(parsedJson.path("safety"));

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getType())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safety)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return createDefaultRecommendation(activity);
        }

    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .userId(activity.getUserId())
                .activityId(activity.getId())
                .activityType(activity.getType())
                .recommendation("Unable to generate detailed analysis")
                .improvements(Collections.singletonList("Continue with your current routine"))
                .suggestions(Collections.singletonList("Consider consulting a fitness professional"))
                .safety(Arrays.asList(
                        "Always warmup before exercise",
                        "Stay Hydrated",
                        "Listen to your body"
                ))
                .build();
    }

    private List<String> extractSafety(JsonNode safetyNode) {
        List<String> safety = new ArrayList<>();
        if(safetyNode.isArray()){
            safetyNode.forEach(safe -> {
                safety.add(safe.asText());
            });
        }
        return safety.isEmpty() ?
                Collections.singletonList("Follow General Safety Guidelines"):
                safety;
    }

    private List<String> extractSuggestions(JsonNode suggestionsJson) {
        List<String> suggestions = new ArrayList<>();
        if(suggestionsJson.isArray()){
            suggestionsJson.forEach(suggestion -> {
                String workout = suggestion.path("workout").asText();
                String description = suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s",workout,description));
            });
        }
        return suggestions.isEmpty() ?
                Collections.singletonList("No Specific Suggestions Provided") :
                suggestions;
    }

    private List<String> extractImprovements(JsonNode improvementsJson) {
        List<String> improvements = new ArrayList<>();
        if(improvementsJson.isArray()){
            improvementsJson.forEach(improvement ->{
                String area =  improvement.path("area").asText();
                String recommendation = improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s",area,recommendation));
            });
        }
        return improvements.isEmpty() ?
                Collections.singletonList("No Specific improvements Provided") :
                improvements;

    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysis, String key, String prefix) {
        if(analysis.path(key).isMissingNode()){
            return;
        }
        fullAnalysis.append(prefix)
                .append(analysis.path(key).asText())
                .append("\n\n");
    }

    private String createPromptForActivity(Activity activity) {
        return String.format(""" 
                  Analyze this fitness activity and provide detailed recommendations in the following format
                  {
                      "analysis" : {
                          "overall": "Overall analysis here",
                          "pace": "Pace analysis here",
                          "heartRate": "Heart rate analysis here",
                          "CaloriesBurned": "Calories Burned here"
                      },
                      "improvements": [
                          {
                              "area": "Area name",
                              "recommendation": "Detailed Recommendation"
                          }
                      ],
                      "suggestions" : [
                          {
                              "workout": "Workout name",
                              "description": "Detailed workout description"
                          }
                      ],
                      "safety": [
                          "Safety point 1",
                          "Safety point 2"
                      ]
                  }
                
                  Analyze this activity:
                  Activity Type: %s
                  Duration: %d minutes
                  calories Burned: %d
                  Additional Metrics: %s
                
                  provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines
                  Ensure the response follows the EXACT JSON format shown above.
                """,
                    activity.getType(),
                    activity.getDuration(),
                    activity.getCaloriesBurned(),
                    activity.getAdditionalMetrics()
        );
    }
}
