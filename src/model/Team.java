package model;

import java.util.ArrayList;
import java.util.List;

public class Team {
    private String teamId;
    private List<Participant> members;
    private double averageSkill;
    private int teamSize;

    public Team(String teamId, int teamSize) {
        this.teamId = teamId;
        this.members = new ArrayList<>();
        this.teamSize = teamSize;
        this.averageSkill = 0.0;
    }

    public boolean addMember(Participant participant) {
        if (members.size() < teamSize) {
            members.add(participant);
            calculateAverageSkill();
            return true;
        }
        return false;
    }

    // Changed from private to public
    public void calculateAverageSkill() {
        if (members.isEmpty()) {
            averageSkill = 0.0;
            return;
        }
        double total = 0.0;
        for (Participant member : members) {
            total += member.getSkillLevel();
        }
        averageSkill = total / members.size();
    }

    public boolean isFull() {
        return members.size() >= teamSize;
    }

    public List<String> getGames() {
        List<String> games = new ArrayList<>();
        for (Participant member : members) {
            games.add(member.getPreferredGame());
        }
        return games;
    }

    public List<String> getRoles() {
        List<String> roles = new ArrayList<>();
        for (Participant member : members) {
            roles.add(member.getPreferredRole());
        }
        return roles;
    }

    public List<String> getPersonalityTypes() {
        List<String> types = new ArrayList<>();
        for (Participant member : members) {
            types.add(member.getPersonalityType());
        }
        return types;
    }

    // Getters
    public String getTeamId() { return teamId; }
    public List<Participant> getMembers() { return new ArrayList<>(members); }
    public double getAverageSkill() { return averageSkill; }
    public int getCurrentSize() { return members.size(); }
    public int getTeamSize() { return teamSize; }

    @Override
    public String toString() {
        return String.format("Team %s: %d members, Avg Skill: %.2f",
                teamId, members.size(), averageSkill);
    }
}