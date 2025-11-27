import model.Participant;
import model.Survey;
import model.Team;
import strategies.BalancedTeamFormationStrategy;
import services.FileHandler;

import exceptions.TeamFormationException;
import exceptions.FileHandlingException;

import java.util.*;
import java.io.File;

public class TeamMateSystem {
    private List<Participant> participants = new ArrayList<>();
    private List<Team> currentTeams = new ArrayList<>();
    private BalancedTeamFormationStrategy teamFormationStrategy = new BalancedTeamFormationStrategy();
    private Scanner scanner = new Scanner(System.in);

    public void run() {
        System.out.println("=== TeamMate: Intelligent Team Formation System ===");

        try {
            showMainMenu();
        } finally {
            shutdown();
        }
    }

    private void shutdown() {
        scanner.close();
        FileHandler.shutdown();
        System.out.println("Thank you for using TeamMate!");
    }

    private void showMainMenu() {
        while (true) {
            System.out.println("\n=== MAIN MENU ===");
            System.out.println("1. Load participants from CSV");
            System.out.println("2. Conduct new surveys");
            System.out.println("3. Form teams");
            System.out.println("4. View participants");
            System.out.println("5. Save teams to CSV");
            System.out.println("6. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1": loadParticipantsFromCSV(); break;
                case "2": conductNewSurveys(); break;
                case "3": formTeams(); break;
                case "4": viewCurrentParticipants(); break;
                case "5": saveFormedTeams(); break;
                case "6": return;
                default: System.out.println("Invalid option. Please choose 1-6.");
            }
        }
    }

    private void formTeams() {
        if (participants.isEmpty()) {
            System.out.println("No participants available. Please load participants first.");
            return;
        }

        long surveyedCount = participants.stream()
                .filter(Participant::isSurveyCompleted)
                .count();

        if (surveyedCount == 0) {
            System.out.println("No participants have completed surveys. Please conduct surveys first.");
            return;
        }

        System.out.print("Enter team size: ");
        int teamSize = getTeamSize();

        try {
            currentTeams = teamFormationStrategy.formTeams(participants, teamSize);
            System.out.println("Successfully formed " + currentTeams.size() + " teams!");
        } catch (TeamFormationException e) {
            System.out.println("Error forming teams: " + e.getMessage());
        }
    }

    private int getTeamSize() {
        while (true) {
            try {
                String input = scanner.nextLine().trim();
                int size = Integer.parseInt(input);

                if (size < 2) {
                    System.out.print("Team size must be at least 2. Please enter a valid size: ");
                    continue;
                }

                if (size > participants.size()) {
                    System.out.print("Team size cannot exceed number of participants (" +
                            participants.size() + "). Please enter a valid size: ");
                    continue;
                }

                return size;

            } catch (NumberFormatException e) {
                System.out.print("Invalid number. Please enter a valid team size: ");
            }
        }
    }

    private void conductNewSurveys() {
        if (participants.isEmpty()) {
            System.out.println("No participants available. Please load participants first.");
            return;
        }
        Survey.conductNewSurveys(participants, scanner);
    }

    private void saveFormedTeams() {
        if (currentTeams.isEmpty()) {
            System.out.println("No teams formed yet. Please form teams first.");
            return;
        }

        System.out.print("Enter filename (default: formed_teams.csv): ");
        String filePath = scanner.nextLine().trim();
        if (filePath.isEmpty()) filePath = "formed_teams.csv";
        if (!filePath.endsWith(".csv")) filePath += ".csv";

        try {
            FileHandler.saveTeamsToCSV(currentTeams, filePath);
            System.out.println("Teams successfully saved to: " + filePath);
        } catch (FileHandlingException e) {
            System.out.println("Error saving teams: " + e.getMessage());
        }
    }

    private void loadParticipantsFromCSV() {
        System.out.print("Enter CSV file path (default: participants_sample.csv): ");
        String filePath = scanner.nextLine().trim();
        if (filePath.isEmpty()) filePath = "participants_sample.csv";

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File not found: " + filePath);
            return;
        }

        try {
            List<Participant> loaded = FileHandler.loadParticipantsFromCSV(filePath);
            participants.clear();
            participants.addAll(loaded);
            currentTeams.clear();

            System.out.println("Successfully loaded " + loaded.size() + " participants.");
        } catch (FileHandlingException e) {
            System.out.println("Error loading participants: " + e.getMessage());
        }
    }

    private void viewCurrentParticipants() {
        if (participants.isEmpty()) {
            System.out.println("No participants loaded.");
            return;
        }

        System.out.println("\n=== CURRENT PARTICIPANTS ===");
        System.out.println("Total: " + participants.size() + " participants");

        long surveyed = participants.stream().filter(Participant::isSurveyCompleted).count();
        System.out.println("Surveyed: " + surveyed + " | Not surveyed: " + (participants.size() - surveyed));

        for (Participant p : participants) {
            String status = p.isSurveyCompleted() ? "[SURVEYED]" : "[NO SURVEY]";
            System.out.println(status + " " + p);
        }
    }

    public static void main(String[] args) {
        new TeamMateSystem().run();
    }
}