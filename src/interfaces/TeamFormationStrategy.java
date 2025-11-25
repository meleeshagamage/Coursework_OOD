package interfaces;

import model.Participant;
import model.Team;
import exceptions.TeamFormationException;
import java.util.List;

public interface TeamFormationStrategy {
    List<Team> formTeams(List<Participant> participants, int teamSize) throws TeamFormationException;
}