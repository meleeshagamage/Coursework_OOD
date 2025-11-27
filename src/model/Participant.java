package model;

import java.util.HashMap;
import java.util.Map;

public class Participant {
    private String id;
    private String name;
    private String email;
    private String preferredGame;
    private int skillLevel;
    private String preferredRole;
    private int personalityScore;
    private String personalityType;
    private Map<String, Integer> surveyResponses;
    private boolean surveyCompleted;

    public Participant(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.surveyResponses = new HashMap<>();
        this.surveyCompleted = false;
    }

    public Participant(String id, String name, String email, String preferredGame,
                       int skillLevel, String preferredRole, int personalityScore) {
        this(id, name, email);
        this.preferredGame = preferredGame;
        this.skillLevel = skillLevel;
        this.preferredRole = preferredRole;
        this.personalityScore = personalityScore;
        this.personalityType = classifyPersonality(personalityScore);
        this.surveyCompleted = true;
    }

    private String classifyPersonality(int score) {
        if (score >= 90 && score <= 100) {
            return "Leader";
        } else if (score >= 70 && score <= 89) {
            return "Balanced";
        } else if (score >= 50 && score <= 69) {
            return "Thinker";
        } else {
            return "Unknown";
        }
    }

    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPreferredGame() { return preferredGame; }
    public int getSkillLevel() { return skillLevel; }
    public String getPreferredRole() { return preferredRole; }
    public int getPersonalityScore() { return personalityScore; }
    public String getPersonalityType() { return personalityType; }
    public boolean isSurveyCompleted() { return surveyCompleted; }
    public Map<String, Integer> getSurveyResponses() { return new HashMap<>(surveyResponses); }

    public void setPreferredGame(String game) { this.preferredGame = game; }
    public void setSkillLevel(int level) { this.skillLevel = level; }
    public void setPreferredRole(String role) { this.preferredRole = role; }
    public void setPersonalityScore(int score) {
        this.personalityScore = score;
        this.personalityType = classifyPersonality(score);
    }
    public void setSurveyCompleted(boolean completed) { this.surveyCompleted = completed; }
    public void setSurveyResponses(Map<String, Integer> responses) {
        this.surveyResponses = new HashMap<>(responses);
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - %s - Skill: %d - Role: %s - %s (%d)",
                name, id, preferredGame, skillLevel, preferredRole, personalityType, personalityScore);
    }
}