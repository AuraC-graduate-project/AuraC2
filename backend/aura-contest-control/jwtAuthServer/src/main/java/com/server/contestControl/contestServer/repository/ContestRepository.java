package com.server.contestControl.contestServer.repository;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    boolean existsByStatusIn(List<ContestStatus> statuses);
    Optional<Contest> findByStatus(ContestStatus status);
    boolean existsByStatus(ContestStatus statuses);

    List<Contest> findAllByStatus(ContestStatus status);

    Optional<Contest> findTopByStatusOrderByStartTimeDesc(ContestStatus status);

}