package strategies;

import interfaces.TeamFormationStrategy;
import model.Participant;
import model.Team;
import exceptions.TeamFormationException;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BalancedTeamFormationStrategy implements TeamFormationStrategy {
    private final ExecutorService teamExecutor;
    private final int availableProcessors;

    public BalancedTeamFormationStrategy() {
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
        this.teamExecutor = Executors.newFixedThreadPool(availableProcessors);
    }

    @Override
    public List<Team> formTeams(List<Participant> participants, int teamSize) throws TeamFormationException {
        System.out.println("Starting concurrent team formation with " + availableProcessors + " threads...");

        validateInput(participants, teamSize);
        List<Participant> availableParticipants = new ArrayList<>(participants);

        System.out.println("Total participants available: " + availableParticipants.size());

        // Sort by skill level for better distribution
        availableParticipants.sort(Comparator.comparingInt(Participant::getSkillLevel).reversed());

        // Categorize participants CONCURRENTLY
        Map<String, List<Participant>> categorized = concurrentlyCategorizeParticipants(availableParticipants);
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

        // CONCURRENT TEAM FORMATION
        formTeamsConcurrently(teams, leaders, thinkers, balanced, teamSize);

        // CONCURRENT BALANCING
        balanceTeamsConcurrently(teams);

        // VALIDATION DISPLAY - KEEP YOUR ORIGINAL METHOD
        displayTeamsWithValidity(teams);
        displayParticipantUsage(teams, availableParticipants);

        teamExecutor.shutdown();
        return teams;
    }

    private Map<String, List<Participant>> concurrentlyCategorizeParticipants(List<Participant> participants) {
        int batchSize = Math.max(1, participants.size() / availableProcessors);
        List<Callable<Map<String, List<Participant>>>> tasks = new ArrayList<>();

        for (int i = 0; i < participants.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(participants.size(), i + batchSize);
            final List<Participant> batch = participants.subList(start, end);

            tasks.add(() -> {
                Map<String, List<Participant>> batchResult = new HashMap<>();
                batchResult.put("Leader", new ArrayList<>());
                batchResult.put("Thinker", new ArrayList<>());
                batchResult.put("Balanced", new ArrayList<>());
                batchResult.put("Unknown", new ArrayList<>());

                for (Participant p : batch) {
                    String type = p.getPersonalityType();
                    if (type == null) type = "Unknown";
                    batchResult.computeIfAbsent(type, k -> new ArrayList<>()).add(p);
                }
                return batchResult;
            });
        }

        try {
            List<Future<Map<String, List<Participant>>>> futures = teamExecutor.invokeAll(tasks, 30, TimeUnit.SECONDS);

            Map<String, List<Participant>> finalResult = new HashMap<>();
            finalResult.put("Leader", new ArrayList<>());
            finalResult.put("Thinker", new ArrayList<>());
            finalResult.put("Balanced", new ArrayList<>());
            finalResult.put("Unknown", new ArrayList<>());

            for (Future<Map<String, List<Participant>>> future : futures) {
                Map<String, List<Participant>> batchResult = future.get();
                for (Map.Entry<String, List<Participant>> entry : batchResult.entrySet()) {
                    finalResult.get(entry.getKey()).addAll(entry.getValue());
                }
            }

            finalResult.values().forEach(Collections::shuffle);
            return finalResult;

        } catch (Exception e) {
            System.out.println("Concurrent categorization failed, using sequential fallback");
            return categorizeParticipants(participants);
        }
    }

    private void formTeamsConcurrently(List<Team> teams, List<Participant> leaders,
                                       List<Participant> thinkers, List<Participant> balanced, int teamSize) {

        List<Participant> safeLeaders = new CopyOnWriteArrayList<>(leaders);
        List<Participant> safeThinkers = new CopyOnWriteArrayList<>(thinkers);
        List<Participant> safeBalanced = new CopyOnWriteArrayList<>(balanced);

        AtomicInteger currentTeamIndex = new AtomicInteger(0);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < availableProcessors; i++) {
            tasks.add(() -> {
                int teamIdx;
                while ((teamIdx = currentTeamIndex.getAndIncrement()) < teams.size()) {
                    Team team = teams.get(teamIdx);
                    fillSingleTeam(team, safeLeaders, safeThinkers, safeBalanced, teamSize);
                }
                return null;
            });
        }

        try {
            teamExecutor.invokeAll(tasks, 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Team formation interrupted");
        }
    }

    private void fillSingleTeam(Team team, List<Participant> leaders,
                                List<Participant> thinkers, List<Participant> balanced, int teamSize) {

        // Add one leader if available
        if (!leaders.isEmpty() && !team.isFull()) {
            team.addMember(leaders.remove(0));
        }

        // Add up to 2 thinkers
        int thinkersAdded = 0;
        while (thinkersAdded < 2 && !thinkers.isEmpty() && !team.isFull()) {
            team.addMember(thinkers.remove(0));
            thinkersAdded++;
        }

        // Fill with balanced participants
        while (!team.isFull() && !balanced.isEmpty()) {
            team.addMember(balanced.remove(0));
        }
    }

    private void balanceTeamsConcurrently(List<Team> teams) {
        if (teams.size() <= 1) return;

        System.out.println("\nBalancing teams concurrently...");

        // Use parallel stream for simple concurrency
        teams.parallelStream().forEach(team -> {
            // Local optimization for each team
            for (Team otherTeam : teams) {
                if (team != otherTeam) {
                    attemptSkillSwap(team, otherTeam);
                }
            }
        });

        System.out.println("Team balancing completed");
    }

    private void attemptSkillSwap(Team team1, Team team2) {
        for (Participant p1 : team1.getMembers()) {
            for (Participant p2 : team2.getMembers()) {
                if (isValidSwap(team1, team2, p1, p2)) {
                    double newDiff = calculateNewSkillDifference(team1, team2, p1, p2);
                    double currentDiff = Math.abs(team1.getAverageSkill() - team2.getAverageSkill());

                    if (newDiff < currentDiff) {
                        swapMembers(team1, team2, p1, p2);
                        return;
                    }
                }
            }
        }
    }

    // KEEP ALL YOUR ORIGINAL VALIDATION AND DISPLAY METHODS
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

    private boolean isValidSwap(Team team1, Team team2, Participant p1, Participant p2) {
        return isValidPersonalityDistribution(team1, p1, p2) &&
                isValidPersonalityDistribution(team2, p2, p1);
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

    private double calculateNewSkillDifference(Team team1, Team team2, Participant p1, Participant p2) {
        double newAvg1 = (team1.getAverageSkill() * team1.getCurrentSize() - p1.getSkillLevel() + p2.getSkillLevel())
                / team1.getCurrentSize();
        double newAvg2 = (team2.getAverageSkill() * team2.getCurrentSize() - p2.getSkillLevel() + p1.getSkillLevel())
                / team2.getCurrentSize();
        return Math.abs(newAvg1 - newAvg2);
    }

    private void swapMembers(Team team1, Team team2, Participant p1, Participant p2) {
        team1.getMembers().remove(p1);
        team2.getMembers().remove(p2);
        team1.addMember(p2);
        team2.addMember(p1);
        team1.calculateAverageSkill();
        team2.calculateAverageSkill();
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

    public void shutdown() {
        teamExecutor.shutdown();
        try {
            if (!teamExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                teamExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            teamExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}