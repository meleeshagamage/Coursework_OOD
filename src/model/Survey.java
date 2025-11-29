package model;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.List;

public class Survey {
    private String participantId;
    private Map<String, Integer> responses;
    private String preferredGame;
    private int skillLevel;
    private String preferredRole;
    private int totalScore;
    private String personalityType;

    public Survey(String participantId) {
        this.participantId = participantId;
        this.responses = new HashMap<>();
    }

    // Service methods for managing multiple surveys
    public static void conductNewSurveys(List<Participant> participants, Scanner scanner) {
        System.out.print("Enter number of participants to survey: ");

        try {
            int count = Integer.parseInt(scanner.nextLine());
            if (count <= 0) {
                System.out.println("Invalid number. Must be greater than 0.");
                return;
            }

            for (int i = 0; i < count; i++) {
                System.out.println("\n--- Participant " + (i + 1) + " ---");

                String id = getInput(scanner, "Enter participant ID: ");
                if (id.isEmpty()) {
                    System.out.println("Participant ID cannot be empty. Skipping...");
                    continue;
                }

                if (participantExists(participants, id)) {
                    System.out.println("Participant ID already exists. Skipping...");
                    continue;
                }

                String name = getInput(scanner, "Enter participant name: ");
                String email = getInput(scanner, "Enter participant email: ");

                if (name.isEmpty() || email.isEmpty()) {
                    System.out.println("Name or email is empty. Skipping...");
                    continue;
                }

                Participant participant = new Participant(id, name, email);
                boolean surveyCompleted = conductSurveyForParticipant(participant, scanner);

                if (surveyCompleted) {
                    participants.add(participant);
                    System.out.println("Participant added and survey completed!");
                } else {
                    System.out.println("Survey was not completed. Participant not added.");
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid number format. Please enter a valid integer.");
        }
    }

    private static String getInput(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static boolean participantExists(List<Participant> participants, String id) {
        return participants.stream().anyMatch(p -> p.getId().equals(id));
    }

    public static boolean conductSurveyForParticipant(Participant participant, Scanner scanner) {
        System.out.println("\n=== Personality Survey for " + participant.getName() + " ===");

        Survey survey = new Survey(participant.getId());
        boolean completed = survey.conductSurvey(scanner);

        if (completed) {
            updateParticipantFromSurvey(participant, survey);
            return true;
        }
        return false;
    }

    private static void updateParticipantFromSurvey(Participant participant, Survey survey) {

        participant.setPreferredGame(survey.getPreferredGame());
        participant.setSkillLevel(survey.getSkillLevel());
        participant.setPreferredRole(survey.getPreferredRole());
        participant.setPersonalityScore(survey.getTotalScore());
        participant.setSurveyCompleted(true);
    }

    // Individual survey methods
    public boolean conductSurvey(Scanner scanner) {
        System.out.println("Please rate each statement from 1 (Strongly Disagree) to 5 (Strongly Agree)");

        String[] questions = {
                "I enjoy taking the lead and guiding others during group activities.",
                "I prefer analyzing situations and coming up with strategic solutions.",
                "I work well with others and enjoy collaborative teamwork.",
                "I am calm under pressure and can help maintain team morale.",
                "I like making quick decisions and adapting in dynamic situations."
        };

        // Check if user wants to cancel
        System.out.print("Press Enter to continue or 'cancel' to stop: ");
        String input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("cancel")) {
            System.out.println("Survey cancelled.");
            return false;
        }

        for (int i = 0; i < questions.length; i++) {
            int response;
            while (true) {
                System.out.printf("\nQ%d: %s%n", i + 1, questions[i]);
                System.out.print("Your rating (1-5, or 'cancel' to stop): ");
                String responseInput = scanner.nextLine().trim();

                if (responseInput.equalsIgnoreCase("cancel")) {
                    System.out.println("Survey cancelled.");
                    return false;
                }

                try {
                    response = Integer.parseInt(responseInput);
                    if (response >= 1 && response <= 5) {
                        responses.put("Q" + (i + 1), response);
                        break;
                    } else {
                        System.out.println("Please enter a number between 1 and 5.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a valid number between 1 and 5.");
                }
            }
        }

        if (!collectAdditionalInfo(scanner)) {
            System.out.println("Survey cancelled during additional info collection.");
            return false;
        }

        calculateResults();
        displayResults();
        return true;
    }

    private boolean collectAdditionalInfo(Scanner scanner) {
        // Use simple boolean check instead of exception
        this.preferredGame = selectGame(scanner);
        if (preferredGame == null) return false;

        this.skillLevel = selectSkillLevel(scanner);
        if (skillLevel == -1) return false;

        this.preferredRole = selectRole(scanner);
        return preferredRole != null;
    }

    private String selectGame(Scanner scanner) {
        String[] games = {"Valorant", "DOTA 2", "CS:GO", "FIFA", "Basketball", "Chess", "Badminton"};
        System.out.println("\n=== Game Preference ===");
        for (int i = 0; i < games.length; i++) {
            System.out.printf("%d. %s%n", i + 1, games[i]);
        }

        while (true) {
            System.out.print("Select your preferred game (1-" + games.length + ", or 'cancel' to stop): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("cancel")) {
                return null;
            }

            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= games.length) {
                    return games[choice - 1];
                } else {
                    System.out.println("Please enter a number between 1 and " + games.length + ".");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private int selectSkillLevel(Scanner scanner) {
        while (true) {
            System.out.print("Enter your skill level (1-10, where 10 is expert, or 'cancel' to stop): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("cancel")) {
                return -1;
            }

            try {
                int skillLevel = Integer.parseInt(input);
                if (skillLevel >= 1 && skillLevel <= 10) {
                    return skillLevel;
                } else {
                    System.out.println("Please enter a number between 1 and 10.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number between 1 and 10.");
            }
        }
    }

    private String selectRole(Scanner scanner) {
        String[] roles = {"Strategist", "Attacker", "Defender", "Supporter", "Coordinator"};
        Map<String, String> roleDescriptions = new HashMap<>();
        roleDescriptions.put("Strategist", "Focuses on tactics and planning. Keeps the bigger picture in mind.");
        roleDescriptions.put("Attacker", "Frontline player. Good reflexes, offensive tactics, quick execution.");
        roleDescriptions.put("Defender", "Protects and supports team stability. Good under pressure.");
        roleDescriptions.put("Supporter", "Jack-of-all-trades. Adapts roles, ensures smooth coordination.");
        roleDescriptions.put("Coordinator", "Communication lead. Keeps team informed and organized.");

        System.out.println("\n=== Preferred Role ===");
        for (int i = 0; i < roles.length; i++) {
            System.out.printf("%d. %s: %s%n", i + 1, roles[i], roleDescriptions.get(roles[i]));
        }

        while (true) {
            System.out.print("Select your preferred role (1-" + roles.length + ", or 'cancel' to stop): ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("cancel")) {
                return null;
            }

            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= roles.length) {
                    return roles[choice - 1];
                } else {
                    System.out.println("Please enter a number between 1 and " + roles.length + ".");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    public void calculateResults() {
        this.totalScore = responses.values().stream().mapToInt(Integer::intValue).sum() * 4;
        this.personalityType = determinePersonalityType(this.totalScore);
    }

    public String determinePersonalityType(int totalScore) {
        if (totalScore >= 90 && totalScore <= 100) {
            return "Leader";
        } else if (totalScore >= 70 && totalScore <= 89) {
            return "Balanced";
        } else if (totalScore >= 50 && totalScore <= 69) {
            return "Thinker";

        } else {
            return "Unknown";
        }
    }

    private String getPersonalityDescription(String personalityType) {
        switch (personalityType) {
            case "Leader": return "Confident, decision-maker, naturally takes charge";
            case "Balanced": return "Adaptive, communicative, team-oriented";
            case "Thinker": return "Observant, analytical, prefers planning before action";

            default: return "Personality type not determined";
        }
    }

    private void displayResults() {
        System.out.println("\n=== Survey Results ===");
        System.out.println("Personality Score: " + totalScore);
        System.out.println("Personality Type: " + personalityType);
        System.out.println("Description: " + getPersonalityDescription(personalityType));
        System.out.println("Preferred Game: " + preferredGame);
        System.out.println("Skill Level: " + skillLevel);
        System.out.println("Preferred Role: " + preferredRole);
        System.out.println("=" .repeat(40));
    }

    // Getters
    public String getParticipantId() { return participantId; }
    public Map<String, Integer> getResponses() { return new HashMap<>(responses); }
    public String getPreferredGame() { return preferredGame; }
    public int getSkillLevel() { return skillLevel; }
    public String getPreferredRole() { return preferredRole; }
    public int getTotalScore() { return totalScore; }
    public String getPersonalityType() { return personalityType; }

    // Setters
    public void setParticipantId(String participantId) { this.participantId = participantId; }
    public void setResponses(Map<String, Integer> responses) { this.responses = new HashMap<>(responses); }
    public void setPreferredGame(String preferredGame) { this.preferredGame = preferredGame; }
    public void setSkillLevel(int skillLevel) { this.skillLevel = skillLevel; }
    public void setPreferredRole(String preferredRole) { this.preferredRole = preferredRole; }
    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
        this.personalityType = determinePersonalityType(totalScore);
    }
}