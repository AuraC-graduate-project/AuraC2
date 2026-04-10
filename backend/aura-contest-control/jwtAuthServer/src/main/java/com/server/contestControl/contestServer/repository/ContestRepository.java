package com.server.contestControl.contestServer.repository;

import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.enums.ContestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    boolean existsByStatusIn(List<ContestStatus> statuses);
    Optional<Contest> findByStatus(ContestStatus status);
    boolean existsByStatus(ContestStatus statuses);

    List<Contest> findAllByStatus(ContestStatus status);

    Optional<Contest> findTopByStatusOrderByStartTimeDesc(ContestStatus status);

    /**
     * Find contests eligible for automatic status sync:
     * - Not status-locked (statusLocked = false or null)
     * - In a non-terminal, non-paused state (UPCOMING or RUNNING)
     */
    @Query("SELECT c FROM Contest c WHERE (c.statusLocked = false OR c.statusLocked IS NULL) AND c.status IN :statuses")
    List<Contest> findSyncCandidates(@Param("statuses") List<ContestStatus> statuses);

    /**
     * Fetch a contest and immediately acquire an exclusive row-level lock.
     * Any concurrent transaction attempting the same call will block until
     * this transaction commits or rolls back.
     *
     * Must be called within an active transaction (REQUIRES_NEW satisfies this).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Contest c WHERE c.id = :id")
    Optional<Contest> findByIdWithLock(@Param("id") Long id);

}