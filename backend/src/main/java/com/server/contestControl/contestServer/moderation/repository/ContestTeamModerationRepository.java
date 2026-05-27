package com.server.contestControl.contestServer.moderation.repository;

import com.server.contestControl.contestServer.moderation.entity.ContestTeamModeration;
import com.server.contestControl.contestServer.moderation.enums.ContestTeamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ContestTeamModerationRepository extends JpaRepository<ContestTeamModeration, Long> {

    Optional<ContestTeamModeration> findByContest_IdAndTeam_Id(Long contestId, Long teamId);

    List<ContestTeamModeration> findByContest_Id(Long contestId);

    @Query("""
            select m.team.id
            from ContestTeamModeration m
            where m.contest.id = :contestId
              and (
                    m.status = :disqualified
                    or m.hiddenFromScoreboard = true
              )
            """)
    Set<Long> findScoreboardSuppressedTeamIds(
            @Param("contestId") Long contestId,
            @Param("disqualified") ContestTeamStatus disqualified
    );

    @Query("""
            select m
            from ContestTeamModeration m
            join fetch m.team
            left join fetch m.updatedByAdmin
            where m.contest.id = :contestId
              and m.team.id in :teamIds
            """)
    List<ContestTeamModeration> findByContestIdAndTeamIdIn(
            @Param("contestId") Long contestId,
            @Param("teamIds") Collection<Long> teamIds
    );
}
