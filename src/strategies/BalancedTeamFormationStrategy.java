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

        // Validate input
        validateInput(participants, teamSize);

        // USE ALL PARTICIPANTS (remove survey filter)
        List<Participant> availableParticipants = new ArrayList<>(participants);

        System.out.println("Total participants available: " + availableParticipants.size());

        // Sort participants by skill level for better distribution
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

        // Calculate maximum possible teams
        int teamCount = availableParticipants.size() / teamSize;
        int totalParticipantsNeeded = teamCount * teamSize;

        System.out.println("Forming " + teamCount + " teams using " + totalParticipantsNeeded + " participants");

        List<Team> teams = createEmptyTeams(teamCount, teamSize);

        // Phase 1: Distribute Leaders (1 per team)
        distributeLeaders(teams, leaders);

        // Phase 2: Distribute Thinkers (1-2 per team)
        distributeThinkers(teams, thinkers, teamSize);

        // Phase 3: Fill with Balanced participants
        fillWithBalanced(teams, balanced, teamSize);

        // Phase 4: Ensure all teams are full and follow rules
        ensureFullTeams(teams, availableParticipants, teamSize);

        // Phase 5: Balance teams based on all criteria
        balanceTeams(teams);

        // Display results
        displayTeamsWithValidity(teams);

        // Verify all participants are used
        int totalUsed = teams.stream().mapToInt(Team::getCurrentSize).sum();
        System.out.println("\n=== PARTICIPANT USAGE SUMMARY ===");
        System.out.println("Participants used: " + totalUsed + " out of " + availableParticipants.size());
        System.out.println("Participants not assigned: " + (availableParticipants.size() - totalUsed));

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
        categorized.put("Unknown", new ArrayList<>()); // Add Unknown category

        for (Participant p : participants) {
            String type = p.getPersonalityType();
            if (type == null) {
                type = "Unknown"; // Handle null personality types
            }
            if (categorized.containsKey(type)) {
                categorized.get(type).add(p);
            } else {
                // If unknown type, treat as Balanced
                categorized.get("Balanced").add(p);
            }
        }

        // Shuffle for random distribution within categories
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
                team.addMember(leaders.get(leaderIndex));
                leaderIndex++;
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
                    team.addMember(thinkers.get(thinkerIndex));
                    thinkerIndex++;
                }
            }
        }
    }

    private void fillWithBalanced(List<Team> teams, List<Participant> balanced, int teamSize) {
        int balancedIndex = 0;
        for (Team team : teams) {
            while (!team.isFull() && balancedIndex < balanced.size()) {
                team.addMember(balanced.get(balancedIndex));
                balancedIndex++;
            }
        }
    }

    private void ensureFullTeams(List<Team> teams, List<Participant> allParticipants, int teamSize) {
        // Find teams that are not full
        List<Team> incompleteTeams = teams.stream()
                .filter(team -> !team.isFull())
                .collect(Collectors.toList());

        if (incompleteTeams.isEmpty()) {
            return;
        }

        // Get participants not yet assigned to any team
        Set<Participant> assignedParticipants = teams.stream()
                .flatMap(team -> team.getMembers().stream())
                .collect(Collectors.toSet());

        List<Participant> unassignedParticipants = allParticipants.stream()
                .filter(p -> !assignedParticipants.contains(p))
                .collect(Collectors.toList());

        // Assign remaining participants to incomplete teams
        int participantIndex = 0;
        for (Team team : incompleteTeams) {
            while (!team.isFull() && participantIndex < unassignedParticipants.size()) {
                team.addMember(unassignedParticipants.get(participantIndex));
                participantIndex++;
            }
        }
    }

    private void balanceTeams(List<Team> teams) {
        // Balance skill levels
        balanceSkillLevels(teams);

        // Balance game variety
        balanceGameVariety(teams);

        // Balance role diversity
        balanceRoleDiversity(teams);
    }

    private void balanceSkillLevels(List<Team> teams) {
        boolean improved;
        int maxIterations = 10;
        int iterations = 0;

        do {
            improved = false;
            teams.sort(Comparator.comparingDouble(Team::getAverageSkill));

            Team lowestTeam = teams.get(0);
            Team highestTeam = teams.get(teams.size() - 1);

            if (canImproveSkillBalance(lowestTeam, highestTeam)) {
                improved = true;
            }

            iterations++;
        } while (improved && iterations < maxIterations);
    }

    private boolean canImproveSkillBalance(Team lowTeam, Team highTeam) {
        double currentDiff = highTeam.getAverageSkill() - lowTeam.getAverageSkill();

        if (currentDiff < 1.0) {
            return false;
        }

        for (Participant highMember : highTeam.getMembers()) {
            for (Participant lowMember : lowTeam.getMembers()) {
                if (isValidSwap(lowTeam, highTeam, lowMember, highMember)) {
                    double newLowAvg = calculateNewAverage(lowTeam, lowMember, highMember);
                    double newHighAvg = calculateNewAverage(highTeam, highMember, lowMember);
                    double newDiff = newHighAvg - newLowAvg;

                    if (newDiff < currentDiff) {
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

            // If any game has more than 2 players, try to swap
            for (Map.Entry<String, Long> entry : gameCounts.entrySet()) {
                if (entry.getValue() > 2) {
                    tryFixGameVariety(team, entry.getKey(), teams);
                }
            }
        }
    }

    private void tryFixGameVariety(Team problemTeam, String overrepresentedGame, List<Team> allTeams) {
        List<Participant> excessPlayers = problemTeam.getMembers().stream()
                .filter(p -> p.getPreferredGame().equals(overrepresentedGame))
                .collect(Collectors.toList());

        for (Participant excessPlayer : excessPlayers) {
            for (Team otherTeam : allTeams) {
                if (otherTeam == problemTeam) continue;

                Map<String, Long> otherGameCounts = otherTeam.getMembers().stream()
                        .collect(Collectors.groupingBy(Participant::getPreferredGame, Collectors.counting()));

                // Find a player from other team with different game
                for (Participant otherPlayer : otherTeam.getMembers()) {
                    if (!otherPlayer.getPreferredGame().equals(overrepresentedGame) &&
                            otherGameCounts.getOrDefault(otherPlayer.getPreferredGame(), 0L) > 1) {

                        if (isValidSwap(problemTeam, otherTeam, excessPlayer, otherPlayer)) {
                            swapMembers(problemTeam, otherTeam, excessPlayer, otherPlayer);
                            return;
                        }
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

            // If less than 3 unique roles, try to improve
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

        // Safely handle null personality types
        String removeType = remove.getPersonalityType() != null ? remove.getPersonalityType() : "Unknown";
        String addType = add.getPersonalityType() != null ? add.getPersonalityType() : "Unknown";

        // Safely decrement and increment counts
        newCounts.put(removeType, newCounts.getOrDefault(removeType, 0) - 1);
        newCounts.put(addType, newCounts.getOrDefault(addType, 0) + 1);

        return isValidPersonalityMix(newCounts, team.getTeamSize());
    }

    private boolean isValidGameDistribution(Team team, Participant remove, Participant add) {
        if (remove.getPreferredGame().equals(add.getPreferredGame())) {
            return true; // No change in game distribution
        }

        Map<String, Long> currentGameCounts = team.getMembers().stream()
                .collect(Collectors.groupingBy(Participant::getPreferredGame, Collectors.counting()));

        // Check if adding this game would exceed max of 2
        long newCount = currentGameCounts.getOrDefault(add.getPreferredGame(), 0L) + 1;
        return newCount <= 2;
    }

    // FIXED METHOD: Handle null personality types safely
    private Map<String, Integer> countPersonalityTypes(Team team) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("Leader", 0);
        counts.put("Thinker", 0);
        counts.put("Balanced", 0);
        counts.put("Unknown", 0); // Add Unknown category

        for (Participant member : team.getMembers()) {
            String type = member.getPersonalityType();
            if (type == null) {
                type = "Unknown"; // Handle null types
            }
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        return counts;
    }

    private boolean isValidPersonalityMix(Map<String, Integer> personalityCounts, int teamSize) {
        int leaders = personalityCounts.getOrDefault("Leader", 0);
        int thinkers = personalityCounts.getOrDefault("Thinker", 0);
        int balanced = personalityCounts.getOrDefault("Balanced", 0);
        int unknown = personalityCounts.getOrDefault("Unknown", 0);

        boolean hasValidLeaders = leaders <= 1;
        boolean hasValidThinkers = thinkers <= 2; // Can have 0-2 thinkers
        boolean hasValidTotal = (leaders + thinkers + balanced + unknown) == teamSize;

        return hasValidLeaders && hasValidThinkers && hasValidTotal;
    }

    private boolean isTeamValid(Team team) {
        Map<String, Integer> personalityCounts = countPersonalityTypes(team);

        // Check personality rules
        if (!isValidPersonalityMix(personalityCounts, team.getTeamSize())) {
            return false;
        }

        // Check game variety (max 2 per game)
        Map<String, Long> gameCounts = team.getMembers().stream()
                .collect(Collectors.groupingBy(Participant::getPreferredGame, Collectors.counting()));
        boolean validGames = gameCounts.values().stream().allMatch(count -> count <= 2);

        // Check role diversity (at least 3 different roles)
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

        List<Team> validTeams = new ArrayList<>();
        List<Team> invalidTeams = new ArrayList<>();

        for (Team team : teams) {
            if (isTeamValid(team)) {
                validTeams.add(team);
            } else {
                invalidTeams.add(team);
            }
        }

        // Display valid teams
        System.out.println("\nVALID TEAMS (Follow All Rules)");
        System.out.println("-".repeat(60));
        if (validTeams.isEmpty()) {
            System.out.println("No valid teams formed.");
        } else {
            for (Team team : validTeams) {
                displayTeamDetails(team, "VALID");
            }
        }

        // Display invalid teams
        System.out.println("\nINVALID TEAMS (Break Some Rules)");
        System.out.println("-".repeat(60));
        if (invalidTeams.isEmpty()) {
            System.out.println("No invalid teams - All teams follow the rules!");
        } else {
            for (Team team : invalidTeams) {
                displayTeamDetails(team, "INVALID");
            }
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
                ", Balanced=" + personalityCounts.get("Balanced") +
                ", Unknown=" + personalityCounts.get("Unknown"));
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

        // Personality issues
        if (personalityCounts.get("Leader") > 1) {
            issues.add("Too many Leaders");
        }
        if (personalityCounts.get("Thinker") > 2) {
            issues.add("Too many Thinkers");
        }

        // Game variety issues
        for (Map.Entry<String, Long> entry : gameCounts.entrySet()) {
            if (entry.getValue() > 2) {
                issues.add("Too many " + entry.getKey() + " players (" + entry.getValue() + ")");
            }
        }

        // Role diversity issues
        if (uniqueRoles.size() < 3) {
            issues.add("Only " + uniqueRoles.size() + " unique roles");
        }

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

        // Skill balance
        double avgSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).average().orElse(0);
        double minSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).min().orElse(0);
        double maxSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).max().orElse(0);
        double skillRange = maxSkill - minSkill;

        System.out.println("\nSkill Balance:");
        System.out.println("Overall Average: " + String.format("%.1f", avgSkill));
        System.out.println("Range: " + String.format("%.1f", minSkill) + " - " + String.format("%.1f", maxSkill));
        System.out.println("Variation: " + String.format("%.2f", skillRange));
    }
}