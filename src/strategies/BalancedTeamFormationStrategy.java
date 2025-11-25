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

        // Filter participants who have completed surveys
        List<Participant> surveyedParticipants = participants.stream()
                .filter(Participant::isSurveyCompleted)
                .collect(Collectors.toList());

        if (surveyedParticipants.size() < teamSize) {
            throw new TeamFormationException("Not enough participants with completed surveys. Have " +
                    surveyedParticipants.size() + ", need at least " + teamSize);
        }

        // Sort participants by skill level for better distribution
        surveyedParticipants.sort(Comparator.comparingInt(Participant::getSkillLevel).reversed());

        // Categorize participants by personality type
        Map<String, List<Participant>> categorized = categorizeParticipants(surveyedParticipants);

        List<Participant> leaders = categorized.get("Leader");
        List<Participant> thinkers = categorized.get("Thinker");
        List<Participant> balanced = categorized.get("Balanced");

        int teamCount = surveyedParticipants.size() / teamSize;
        List<Team> teams = createEmptyTeams(teamCount, teamSize);

        // Phase 1: Distribute Leaders (1 per team)
        distributeLeaders(teams, leaders);

        // Phase 2: Distribute Thinkers (1-2 per team)
        distributeThinkers(teams, thinkers, teamSize);

        // Phase 3: Fill with Balanced participants
        fillWithBalanced(teams, balanced, teamSize);

        // Phase 4: Balance skill levels across teams
        balanceSkillLevels(teams);

        // Display teams with validity classification
        displayTeamsWithValidity(teams);

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

        for (Participant p : participants) {
            String type = p.getPersonalityType();
            categorized.get(type).add(p);
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
        for (int i = 0; i < Math.min(teams.size(), leaders.size()); i++) {
            teams.get(i).addMember(leaders.get(i));
        }
    }

    private void distributeThinkers(List<Team> teams, List<Participant> thinkers, int teamSize) {
        int thinkerIndex = 0;
        for (Team team : teams) {
            int spotsAvailable = teamSize - team.getCurrentSize();
            int thinkersToAdd = Math.min(2, Math.min(spotsAvailable, thinkers.size() - thinkerIndex));

            for (int i = 0; i < thinkersToAdd; i++) {
                team.addMember(thinkers.get(thinkerIndex));
                thinkerIndex++;
            }
        }
    }

    private void fillWithBalanced(List<Team> teams, List<Participant> balanced, int teamSize) {
        int balancedIndex = 0;
        // First pass: fill teams that need members
        for (Team team : teams) {
            while (!team.isFull() && balancedIndex < balanced.size()) {
                team.addMember(balanced.get(balancedIndex));
                balancedIndex++;
            }
        }
    }

    private void balanceSkillLevels(List<Team> teams) {
        // Simple balancing: sort teams by current average skill and swap members if needed
        boolean improved;
        int maxIterations = 10; // Prevent infinite loops
        int iterations = 0;

        do {
            improved = false;
            teams.sort(Comparator.comparingDouble(Team::getAverageSkill));

            Team lowestTeam = teams.get(0);
            Team highestTeam = teams.get(teams.size() - 1);

            // Try to find a swap that improves balance
            if (canImproveBalance(lowestTeam, highestTeam)) {
                improved = true;
            }

            iterations++;
        } while (improved && iterations < maxIterations);
    }

    private boolean canImproveBalance(Team lowTeam, Team highTeam) {
        double currentDiff = highTeam.getAverageSkill() - lowTeam.getAverageSkill();

        // If the difference is already small, no need to swap
        if (currentDiff < 1.0) {
            return false;
        }

        for (Participant highMember : highTeam.getMembers()) {
            for (Participant lowMember : lowTeam.getMembers()) {
                // Check if swapping would improve balance without breaking personality rules
                if (isValidSwap(lowTeam, highTeam, lowMember, highMember)) {
                    double newLowAvg = calculateNewAverage(lowTeam, lowMember, highMember);
                    double newHighAvg = calculateNewAverage(highTeam, highMember, lowMember);
                    double newDiff = newHighAvg - newLowAvg;

                    if (newDiff < currentDiff) {
                        // Perform the swap
                        swapMembers(lowTeam, highTeam, lowMember, highMember);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isValidSwap(Team team1, Team team2, Participant p1, Participant p2) {
        // Check if swapping would break personality rules for either team
        return isValidPersonalityDistribution(team1, p1, p2) &&
                isValidPersonalityDistribution(team2, p2, p1);
    }

    private boolean isValidPersonalityDistribution(Team team, Participant remove, Participant add) {
        // Simulate the team after swap
        Map<String, Integer> currentCounts = countPersonalityTypes(team);
        Map<String, Integer> newCounts = new HashMap<>(currentCounts);

        // Remove old member
        newCounts.put(remove.getPersonalityType(), newCounts.get(remove.getPersonalityType()) - 1);
        // Add new member
        newCounts.put(add.getPersonalityType(), newCounts.getOrDefault(add.getPersonalityType(), 0) + 1);

        // Check personality rules
        return isValidPersonalityMix(newCounts, team.getTeamSize());
    }

    private Map<String, Integer> countPersonalityTypes(Team team) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("Leader", 0);
        counts.put("Thinker", 0);
        counts.put("Balanced", 0);

        for (Participant member : team.getMembers()) {
            counts.put(member.getPersonalityType(), counts.get(member.getPersonalityType()) + 1);
        }
        return counts;
    }

    private boolean isValidPersonalityMix(Map<String, Integer> personalityCounts, int teamSize) {
        int leaders = personalityCounts.get("Leader");
        int thinkers = personalityCounts.get("Thinker");
        int balanced = personalityCounts.get("Balanced");

        // Check personality mix rules
        boolean hasValidLeaders = leaders <= 1; // Max 1 leader
        boolean hasValidThinkers = thinkers >= 1 && thinkers <= 2; // 1-2 thinkers
        boolean hasValidTotal = (leaders + thinkers + balanced) == teamSize;

        return hasValidLeaders && hasValidThinkers && hasValidTotal;
    }

    private boolean isTeamValid(Team team) {
        Map<String, Integer> personalityCounts = countPersonalityTypes(team);
        return isValidPersonalityMix(personalityCounts, team.getTeamSize());
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

        // Recalculate averages after swap
        team1.calculateAverageSkill();
        team2.calculateAverageSkill();
    }

    private void displayTeamsWithValidity(List<Team> teams) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("TEAM FORMATION RESULTS");
        System.out.println("=".repeat(80));

        // Separate valid and invalid teams
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
        System.out.println("\nVALID TEAMS (Follow Personality Mix Rules)");
        System.out.println("-".repeat(60));
        if (validTeams.isEmpty()) {
            System.out.println("No valid teams formed.");
        } else {
            for (Team team : validTeams) {
                displayTeamDetails(team, "VALID");
            }
        }

        // Display invalid teams
        System.out.println("\nINVALID TEAMS (Break Personality Mix Rules)");
        System.out.println("-".repeat(60));
        if (invalidTeams.isEmpty()) {
            System.out.println("No invalid teams - All teams follow the rules!");
        } else {
            for (Team team : invalidTeams) {
                displayTeamDetails(team, "INVALID");
            }
        }

        // Show summary statistics
        displaySummaryStatistics(teams, validTeams, invalidTeams);
    }

    private void displayTeamDetails(Team team, String validityStatus) {
        Map<String, Integer> personalityCounts = countPersonalityTypes(team);

        System.out.println("\n" + validityStatus + " | " + team.getTeamId() +
                " | Avg Skill: " + String.format("%.1f", team.getAverageSkill()) +
                " | Size: " + team.getCurrentSize() + "/" + team.getTeamSize());
        System.out.println("Personality Mix: " +
                "Leaders: " + personalityCounts.get("Leader") + " | " +
                "Thinkers: " + personalityCounts.get("Thinker") + " | " +
                "Balanced: " + personalityCounts.get("Balanced"));

        // Show rule compliance issues for invalid teams
        if (validityStatus.equals("INVALID")) {
            List<String> complianceIssues = getComplianceIssues(team);
            if (!complianceIssues.isEmpty()) {
                System.out.println("Issues: " + String.join(", ", complianceIssues));
            }
        }

        System.out.println("Members:");
        for (Participant member : team.getMembers()) {
            System.out.println("  - " + member.getName() +
                    " | " + member.getPersonalityType() +
                    " | Skill: " + member.getSkillLevel() +
                    " | " + member.getPreferredRole() +
                    " | " + member.getPreferredGame());
        }
    }

    private List<String> getComplianceIssues(Team team) {
        List<String> issues = new ArrayList<>();
        Map<String, Integer> counts = countPersonalityTypes(team);

        int leaders = counts.get("Leader");
        int thinkers = counts.get("Thinker");

        if (leaders > 1) {
            issues.add("Too many Leaders (" + leaders + ") - Max 1 allowed");
        } else if (leaders < 1) {
            issues.add("Missing Leader");
        }

        if (thinkers < 1) {
            issues.add("Not enough Thinkers (" + thinkers + ") - Need 1-2");
        } else if (thinkers > 2) {
            issues.add("Too many Thinkers (" + thinkers + ") - Max 2 allowed");
        }

        return issues;
    }

    private void displaySummaryStatistics(List<Team> allTeams, List<Team> validTeams, List<Team> invalidTeams) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SUMMARY STATISTICS");
        System.out.println("=".repeat(80));

        System.out.println("Total Teams: " + allTeams.size());
        System.out.println("Valid Teams: " + validTeams.size() +
                " (" + String.format("%.1f", (validTeams.size() * 100.0 / allTeams.size())) + "%)");
        System.out.println("Invalid Teams: " + invalidTeams.size() +
                " (" + String.format("%.1f", (invalidTeams.size() * 100.0 / allTeams.size())) + "%)");

        // Skill balance statistics
        double avgSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).average().orElse(0);
        double minSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).min().orElse(0);
        double maxSkill = allTeams.stream().mapToDouble(Team::getAverageSkill).max().orElse(0);
        double skillRange = maxSkill - minSkill;

        System.out.println("\nSkill Balance Analysis:");
        System.out.println("Overall Average Skill: " + String.format("%.1f", avgSkill));
        System.out.println("Skill Range: " + String.format("%.1f", minSkill) + " - " + String.format("%.1f", maxSkill));
        System.out.println("Skill Variation: " + String.format("%.2f", skillRange));

        if (skillRange <= 2.0) {
            System.out.println("Skill Balance: EXCELLENT (Well balanced teams)");
        } else if (skillRange <= 4.0) {
            System.out.println("Skill Balance: GOOD (Reasonable balance)");
        } else {
            System.out.println("Skill Balance: POOR (High skill disparity)");
        }
    }
}