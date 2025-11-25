package Services;

import model.Participant;
import model.Team;
import exceptions.FileHandlingException;
import exceptions.InvalidDataException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FileHandler {
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    public static List<Participant> loadParticipantsFromCSV(String filePath) throws FileHandlingException {
        Callable<List<Participant>> task = () -> {
            List<Participant> participants = new ArrayList<>();

            try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                String line;
                br.readLine(); // Skip header

                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    try {
                        Participant p = parseParticipant(line);
                        participants.add(p);
                    } catch (InvalidDataException e) {
                        System.out.println("Skipping invalid data: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                throw new FileHandlingException("Error reading file: " + e.getMessage());
            }

            if (participants.isEmpty()) {
                throw new FileHandlingException("No valid participants found");
            }

            return participants;
        };

        try {
            return executor.submit(task).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new FileHandlingException("Loading failed: " + e.getMessage());
        }
    }

    public static void saveTeamsToCSV(List<Team> teams, String filePath) throws FileHandlingException {
        if (teams == null || teams.isEmpty()) {
            throw new FileHandlingException("No teams to save");
        }

        Callable<Boolean> task = () -> {
            try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
                writer.println("TeamID,MemberID,MemberName,PreferredGame,SkillLevel,PreferredRole,PersonalityType,PersonalityScore");

                for (Team team : teams) {
                    for (Participant member : team.getMembers()) {
                        writer.printf("%s,%s,%s,%s,%d,%s,%s,%d%n",
                                team.getTeamId(), member.getId(), member.getName(),
                                member.getPreferredGame(), member.getSkillLevel(),
                                member.getPreferredRole(), member.getPersonalityType(),
                                member.getPersonalityScore());
                    }
                }
                return true;
            } catch (IOException e) {
                throw new FileHandlingException("Error writing file: " + e.getMessage());
            }
        };

        try {
            executor.submit(task).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new FileHandlingException("Saving failed: " + e.getMessage());
        }
    }

    private static Participant parseParticipant(String line) throws InvalidDataException {
        String[] parts = line.split(",");
        if (parts.length < 8) {
            throw new InvalidDataException("Insufficient data fields");
        }

        try {
            String id = parts[0].trim();
            String name = parts[1].trim();
            String email = parts[2].trim();
            String game = parts[3].trim();
            int skillLevel = Integer.parseInt(parts[4].trim());
            String role = parts[5].trim();
            int personalityScore = Integer.parseInt(parts[6].trim());

            if (id.isEmpty() || name.isEmpty() || email.isEmpty()) {
                throw new InvalidDataException("Missing required fields");
            }

            if (skillLevel < 1 || skillLevel > 10) {
                throw new InvalidDataException("Invalid skill level: " + skillLevel);
            }

            if (personalityScore < 0 || personalityScore > 100) {
                throw new InvalidDataException("Invalid personality score: " + personalityScore);
            }

            return new Participant(id, name, email, game, skillLevel, role, personalityScore);

        } catch (NumberFormatException e) {
            throw new InvalidDataException("Invalid number format");
        }
    }

    public static void shutdown() {
        executor.shutdown();
    }
}