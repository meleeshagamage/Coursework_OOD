package strategies;

import interfaces.TeamFormationStrategy;
import model.Participant;
import model.Team;
import exceptions.TeamFormationException;

import java.util.*;
import java.util.stream.Collectors;

public class BalancedTeamFormationStrategy implements TeamFormationStrategy {

    @Override
    public List<Team> formTeams(List<Participant> participants, int teamSize) throws TeamFormationException {
        System.out.println("Creating balanced teams of size " + teamSize + "...");

        validateInput(participants, teamSize);
        List<Participant> availableParticipants = new ArrayList<>(participants);

        System.out.println("Total participants available: " + availableParticipants.size());

        // Sort by skill level for better distribution
        availableParticipants.sort(Comparator.comparingInt(Participant::getSkillLevel).reversed());

        // Categorize participants
        Map<String, List<Participant>> categorized = categorizeParticipants(availableParticipants);
        List<Participant> leaders = categorized.get("Leader");
        List<Participant> thinkers = categorized.get("Thinker");
        List<Participant> balanced = categorized.get("Balanced");

        System.out.println("\nPersonality Distribution:");
        System.out.println("Leaders (90-100): " + leaders.size());
        System.out.println("Balanced (70-89): " + balanced.size());
        System.out.println("Thinkers (50-69): " + thinkers.size());

        int teamCount = availableParticipants.size() / teamSize;
        System.out.println("Forming " + teamCount + " teams using " + (teamCount * teamSize) + " participants");

        List<Team> teams = createEmptyTeams(teamCount, teamSize);

        // Distribution phases
        distributeLeaders(teams, leaders);
        distributeThinkers(teams, thinkers, teamSize);
        fillWithBalanced(teams, balanced, teamSize);
        ensureFullTeams(teams, availableParticipants, teamSize);

        // Balancing
        balanceTeams(teams);

        displayTeamsWithValidity(teams);
        displayParticipantUsage(teams, availableParticipants);

        return teams;
    }

    private void validateInput(List<Participant> participants, int teamSize) throws TeamFormationException {
        if (participants == null || participants.isEmpty()) {
            throw new TeamFormationException("No participants provided");
        }
        if (teamSize < 2) {
            throw new TeamFormationException("Team size must be at least 2");
        }
        if (participants.size() < teamSize) {
            throw new TeamFormationException("Not enough participants to form even one team. Have " +
                    participants.size() + ", need at least " + teamSize);
        }
    }

    private Map<String, List<Participant>> categorizeParticipants(List<Participant> participants) {
        Map<String, List<Participant>> categorized = new HashMap<>();
        categorized.put("Leader", new ArrayList<>());
        categorized.put("Thinker", new ArrayList<>());
        categorized.put("Balanced", new ArrayList<>());
        categorized.put("Unknown", new ArrayList<>());

        for (Participant p : participants) {
            String type = p.getPersonalityType();
            if (type == null) type = "Unknown";

            if (categorized.containsKey(type)) {
                categorized.get(type).add(p);
            } else {
                categorized.get("Balanced").add(p);
            }
        }

        categorized.values().forEach(Collections::shuffle);
        return categorized;
    }

    private List<Team> createEmptyTeams(int teamCount, int teamSize) {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            teams.add(new Team("Team-" + (i + 1), teamSize));
        }
        return teams;
    }

    private void distributeLeaders(List<Team> teams, List<Participant> leaders) {
        int leaderIndex = 0;
        for (Team team : teams) {
            if (leaderIndex < leaders.size() && !team.isFull()) {
                team.addMember(leaders.get(leaderIndex++));
            }
        }
    }

    private void distributeThinkers(List<Team> teams, List<Participant> thinkers, int teamSize) {
        int thinkerIndex = 0;
        for (Team team : teams) {
            int spotsAvailable = teamSize - team.getCurrentSize();
            int thinkersToAdd = Math.min(2, Math.min(spotsAvailable, thinkers.size() - thinkerIndex));

            for (int i = 0; i < thinkersToAdd; i++) {
                if (thinkerIndex < thinkers.size()) {
                    team.addMember(thinkers.get(thinkerIndex++));
                }
            }
        }
    }

    private void fillWithBalanced(List<Team> teams, List<Participant> balanced, int teamSize) {
        int balancedIndex = 0;
        for (Team team : teams) {
            while (!team.isFull() && balancedIndex < balanced.size()) {
                team.addMember(balanced.get(balancedIndex++));
            }
        }
    }

    private void ensureFullTeams(List<Team> teams, List<Participant> allParticipants, int teamSize) {
        List<Team> incompleteTeams = teams.stream()
                .filter(team -> !team.isFull())
                .collect(Collectors.toList());

        if (incompleteTeams.isEmpty()) return;

        Set<Participant> assignedParticipants = teams.stream()
                .flatMap(team -> team.getMembers().stream())
                .collect(Collectors.toSet());

        List<Participant> unassignedParticipants = allParticipants.stream()
                .filter(p -> !assignedParticipants.contains(p))
                .collect(Collectors.toList());

        int participantIndex = 0;
        for (Team team : incompleteTeams) {
            while (!team.isFull() && participantIndex < unassignedParticipants.size()) {
                team.addMember(unassignedParticipants.get(participantIndex++));
            }
        }
    }

    private void balanceTeams(List<Team> teams) {
        balanceSkillLevels(teams);
        balanceGameVariety(teams);
        balanceRoleDiversity(teams);
    }

    private void balanceSkillLevels(List<Team> teams) {
        for (int i = 0; i < 10; i++) { // Max 10 iterations
            teams.sort(Comparator.comparingDouble(Team::getAverageSkill));
            Team lowestTeam = teams.get(0);
            Team highestTeam = teams.get(teams.size() - 1);

            if (!tryImproveSkillBalance(lowestTeam, highestTeam)) {
                break; // No more improvements possible
            }
        }
    }

    private boolean tryImproveSkillBalance(Team lowTeam, Team highTeam) {
        double currentDiff = highTeam.getAverageSkill() - lowTeam.getAverageSkill();
        if (currentDiff < 1.0) return false;

        for (Participant highMember : highTeam.getMembers()) {
            for (Participant lowMember : lowTeam.getMembers()) {
                if (isValidSwap(lowTeam, highTeam, lowMember, highMember)) {
                    double newLowAvg = calculateNewAverage(lowTeam, lowMember, highMember);
                    double newHighAvg = calculateNewAverage(highTeam, highMember, lowMember);

                    if ((newHighAvg - newLowAvg) < currentDiff) {
                        swapMembers(lowTeam, highTeam, lowMember, highMember);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void balanceGameVariety(List<Team> teams) {
        for (Team team : teams) {
            Map<String, Long> gameCounts = team.getMembers().stream()
                    .collect(Collectors.groupingBy(Participant::getPreferredGame, Collectors.counting()));

            for (Map.Entry<String, Long> entry : gameCounts.entrySet()) {
                if (entry.getValue() > 2) {
                    tryFixGameVariety(team, entry.getKey(), teams);
                }
            }
        }
    }

    private void tryFixGameVariety(Team problemTeam, String overGame, List<Team> allTeams) {
        for (Participant excessPlayer : problemTeam.getMembers()) {
            if (!excessPlayer.getPreferredGame().equals(overGame)) continue;

            for (Team otherTeam : allTeams) {
                if (otherTeam == problemTeam) continue;

                for (Participant otherPlayer : otherTeam.getMembers()) {
                    if (!otherPlayer.getPreferredGame().equals(overGame) &&
                            isValidSwap(problemTeam, otherTeam, excessPlayer, otherPlayer)) {
                        swapMembers(problemTeam, otherTeam, excessPlayer, otherPlayer);
                        return;
                    }
                }
            }
        }
    }

    private void balanceRoleDiversity(List<Team> teams) {
        for (Team team : teams) {
            Set<String> uniqueRoles = team.getMembers().stream()
                    .map(Participant::getPreferredRole)
                    .collect(Collectors.toSet());

            if (uniqueRoles.size() < 3) {
                tryFixRoleDiversity(team, teams);
            }
        }
    }

    private void tryFixRoleDiversity(Team problemTeam, List<Team> allTeams) {
        Set<String> currentRoles = problemTeam.getMembers().stream()
                .map(Participant::getPreferredRole)
                .collect(Collectors.toSet());

        for (Participant currentPlayer : problemTeam.getMembers()) {
            for (Team otherTeam : allTeams) {
                if (otherTeam == problemTeam) continue;

                for (Participant otherPlayer : otherTeam.getMembers()) {
                    if (!currentRoles.contains(otherPlayer.getPreferredRole()) &&
                            isValidSwap(problemTeam, otherTeam, currentPlayer, otherPlayer)) {
                        swapMembers(problemTeam, otherTeam, currentPlayer, otherPlayer);
                        return;
                    }
                }
            }
        }
    }

    private boolean isValidSwap(Team team1, Team team2, Participant p1, Participant p2) {
        return isValidPersonalityDistribution(team1, p1, p2) &&
                isValidPersonalityDistribution(team2, p2, p1) &&
                isValidGameDistribution(team1, p1, p2) &&
                isValidGameDistribution(team2, p2, p1);
    }

    private boolean isValidPersonalityDistribution(Team team, Participant remove, Participant add) {
        Map<String, Integer> currentCounts = countPersonalityTypes(team);
        Map<String, Integer> newCounts = new HashMap<>(currentCounts);

        String removeType = remove.getPersonalityType() != null ? remove.getPersonalityType() : "Unknown";
        String addType = add.getPersonalityType() != null ? add.getPersonalityType() : "Unknown";

        newCounts.put(removeType, newCounts.getOrDefault(removeType, 0) - 1);
        newCounts.put(addType, newCounts.getOrDefault(addType, 0) + 1);

        return isValidPersonalityMix(newCounts, team.getTeamSize());
    }

    private boolean isValidGameDistribution(Team team, Participant remove, Participant add) {
        if (remove.getPreferredGame().equals(add.getPreferredGame())) {
            return true;
        }

        Map<String, Long> gameCounts = team.getMembers().stream()
                .collect(Collectors.groupingBy(Participant::getPreferredGame, Collectors.counting()));

        long newCount = gameCounts.getOrDefault(add.getPreferredGame(), 0L) + 1;
        return newCount <= 2;
    }

    private Map<String, Integer> countPersonalityTypes(Team team) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("Leader", 0);
        counts.put("Thinker", 0);
        counts.put("Balanced", 0);
        counts.put("Unknown", 0);

        for (Participant member : team.getMembers()) {
            String type = member.getPersonalityType();
            if (type == null) type = "Unknown";
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        return counts;
    }

    private boolean isValidPersonalityMix(Map<String, Integer> personalityCounts, int teamSize) {
        int leaders = personalityCounts.getOrDefault("Leader", 0);
        int thinkers = personalityCounts.getOrDefault("Thinker", 0);
        int total = personalityCounts.values().stream().mapToInt(Integer::intValue).sum();

        return leaders <= 1 && thinkers <= 2 && total == teamSize;
    }

    private boolean isTeamValid(Team team) {
        Map<String, Integer> personalityCounts = countPersonalityTypes(team);

        if (!isValidPersonalityMix(personalityCounts, team.getTeamSize())) {
            return false;
        }

        Map<String, Long> gameCounts = team.getMembers().stream()
                .collect(Collectors.groupingBy(Participant::getPreferredGame, Collectors.counting()));
        boolean validGames = gameCounts.values().stream().allMatch(count -> count <= 2);

        Set<String> uniqueRoles = team.getMembers().stream()
                .map(Participant::getPreferredRole)
                .collect(Collectors.toSet());
        boolean validRoles = uniqueRoles.size() >= 3;

        return validGames && validRoles;
    }

    private double calculateNewAverage(Team team, Participant remove, Participant add) {
        double total = team.getAverageSkill() * team.getCurrentSize();
        total = total - remove.getSkillLevel() + add.getSkillLevel();
        return total / team.getCurrentSize();
    }

    private void swapMembers(Team team1, Team team2, Participant p1, Participant p2) {
        team1.getMembers().remove(p1);
        team2.getMembers().remove(p2);
        team1.addMember(p2);
        team2.addMember(p1);
    }

    private void displayTeamsWithValidity(List<Team> teams) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEAM FORMATION RESULTS");
        System.out.println("=".repeat(80));

        List<Team> validTeams = teams.stream().filter(this::isTeamValid).collect(Collectors.toList());
        List<Team> invalidTeams = teams.stream().filter(team -> !isTeamValid(team)).collect(Collectors.toList());

        System.out.println("\nVALID TEAMS (Follow All Rules)");
        System.out.println("-".repeat(60));
        if (validTeams.isEmpty()) {
            System.out.println("No valid teams formed.");
        } else {
            validTeams.forEach(team -> displayTeamDetails(team, "VALID"));
        }

        System.out.println("\nINVALID TEAMS (Break Some Rules)");
        System.out.println("-".repeat(60));
        if (invalidTeams.isEmpty()) {
            System.out.println("No invalid teams - All teams follow the rules!");
        } else {
            invalidTeams.forEach(team -> displayTeamDetails(team, "INVALID"));
        }

        displaySummaryStatistics(teams, validTeams, invalidTeams);
    }

    private void displayTeamDetails(Team team, String validityStatus) {
        Map<String, Integer> personalityCounts = countPersonalityTypes(team);
        Set<String> uniqueRoles = team.getMembers().stream()
                .map(Participant::getPreferredRole)
                .collect(Collectors.toSet());
        Map<String, Long> gameCounts = team.getMembers().stream()
                .collect(Collectors.groupingBy(Participant::getPreferredGame, Collectors.counting()));

        System.out.println("\n" + validityStatus + " | " + team.getTeamId() +
                " | Avg Skill: " + String.format("%.1f", team.getAverageSkill()) +
                " | Size: " + team.getCurrentSize() + "/" + team.getTeamSize());
        System.out.println("Personality: Leaders=" + personalityCounts.get("Leader") +
                ", Thinkers=" + personalityCounts.get("Thinker") +
                ", Balanced=" + personalityCounts.get("Balanced"));
        System.out.println("Roles: " + uniqueRoles.size() + " unique - " + uniqueRoles);
        System.out.println("Games: " + gameCounts);

        if (validityStatus.equals("INVALID")) {
            List<String> issues = getComplianceIssues(team);
            if (!issues.isEmpty()) {
                System.out.println("Issues: " + String.join(", ", issues));
            }
        }

        System.out.println("Members:");
        for (Participant member : team.getMembers()) {
            String personality = member.getPersonalityType() != null ? member.getPersonalityType() : "Unknown";
            System.out.println("  - " + member.getName() + " | " + personality +
                    " | Skill: " + member.getSkillLevel() + " | " + member.getPreferredRole() +
                    " | " + member.getPreferredGame());
        }
    }

    private List<String> getComplianceIssues(Team team) {
        List<String> issues = new ArrayList<>();
        Map<String, Integer> personalityCounts = countPersonalityTypes(team);
        Set<String> uniqueRoles = team.getMembers().stream()
                .map(Participant::getPreferredRole)
                .collect(Collectors.toSet());
        Map<String, Long> gameCounts = team.getMembers().stream()
                .collect(Collectors.groupingBy(Participant::getPreferredGame, Collectors.counting()));

        if (personalityCounts.get("Leader") > 1) issues.add("Too many Leaders");
        if (personalityCounts.get("Thinker") > 2) issues.add("Too many Thinkers");

        gameCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 2)
                .forEach(entry -> issues.add("Too many " + entry.getKey() + " players (" + entry.getValue() + ")"));

        if (uniqueRoles.size() < 3) issues.add("Only " + uniqueRoles.size() + " unique roles");

        return issues;
    }

    private void displaySummaryStatistics(List<Team> allTeams, List<Team> validTeams, List<Team> invalidTeams) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SUMMARY STATISTICS");
        System.out.println("=".repeat(80));

        System.out.println("Total Teams: " + allTeams.size());
        System.out.println("Valid Teams: " + validTeams.size() + " (" +
                String.format("%.1f", (validTeams.size() * 100.0 / allTeams.size())) + "%)");
        System.out.println("Invalid Teams: " + invalidTeams.size() + " (" +
                String.format("%.1f", (invalidTeams.size() * 100.0 / allTeams.size())) + "%)");

        double avgSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).average().orElse(0);
        double minSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).min().orElse(0);
        double maxSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).max().orElse(0);

        System.out.println("\nSkill Balance:");
        System.out.println("Overall Average: " + String.format("%.1f", avgSkill));
        System.out.println("Range: " + String.format("%.1f", minSkill) + " - " + String.format("%.1f", maxSkill));
        System.out.println("Variation: " + String.format("%.2f", (maxSkill - minSkill)));
    }

    private void displayParticipantUsage(List<Team> teams, List<Participant> availableParticipants) {
        int totalUsed = teams.stream().mapToInt(Team::getCurrentSize).sum();
        System.out.println("\n=== PARTICIPANT USAGE SUMMARY ===");
        System.out.println("Participants used: " + totalUsed + " out of " + availableParticipants.size());
        System.out.println("Participants not assigned: " + (availableParticipants.size() - totalUsed));
    }
}