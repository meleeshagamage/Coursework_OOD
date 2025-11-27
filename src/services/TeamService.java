package services;

import model.Team;
import model.Participant;

import java.util.*;
import java.util.stream.Collectors;

public class TeamService {

    public static void displayTeamResults(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            System.out.println("No teams to display.");
            return;
        }

        System.out.println("\n=== TEAM RESULTS ===");
        System.out.println("Total teams: " + teams.size());

        for (Team team : teams) {
            displayTeam(team);
        }

        displayStatistics(teams);
    }

    private static void displayTeam(Team team) {
        System.out.println("\nTeam: " + team.getTeamId());
        System.out.println("Average Skill: " + String.format("%.1f", team.getAverageSkill()));
        System.out.println("Size: " + team.getCurrentSize() + "/" + team.getTeamSize());

        // Count personality types
        Map<String, Long> personalities = team.getMembers().stream()
                .collect(Collectors.groupingBy(Participant::getPersonalityType, Collectors.counting()));

        System.out.println("Personalities: " + personalities);

        System.out.println("Members:");
        for (Participant member : team.getMembers()) {
            System.out.println("  - " + member.getName() + " (" + member.getPersonalityType() +
                    ", Skill: " + member.getSkillLevel() + ")");
        }
    }

    private static void displayStatistics(List<Team> teams) {
        System.out.println("\n=== STATISTICS ===");

        double avgSkill = teams.stream()
                .mapToDouble(Team::getAverageSkill)
                .average()
                .orElse(0);

        System.out.println("Overall Average Skill: " + String.format("%.1f", avgSkill));

        // Count total participants by personality
        Map<String, Long> totalPersonalities = teams.stream()
                .flatMap(team -> team.getMembers().stream())
                .collect(Collectors.groupingBy(Participant::getPersonalityType, Collectors.counting()));

        System.out.println("Total Personality Distribution: " + totalPersonalities);
    }
}