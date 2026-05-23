package com.server.contestControl.contestServer.oracle.repository;

import com.server.contestControl.contestServer.oracle.entity.GeneratedTestCase;
import com.server.contestControl.contestServer.oracle.enums.GeneratedTestCaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GeneratedTestCaseRepository extends JpaRepository<GeneratedTestCase, Long> {
    List<GeneratedTestCase> findByBatch_IdOrderByTestNumberAsc(Long batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GeneratedTestCase g where g.id = :id")
    Optional<GeneratedTestCase> findByIdForPromotion(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GeneratedTestCase g where g.id in :ids order by g.batch.id asc, g.testNumber asc")
    List<GeneratedTestCase> findAllByIdInForPromotion(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select g
            from GeneratedTestCase g
            where g.batch.id = :batchId and g.status = :status
            order by g.testNumber asc
            """)
    List<GeneratedTestCase> findByBatchIdAndStatusForPromotion(
            @Param("batchId") Long batchId,
            @Param("status") GeneratedTestCaseStatus status
    );
}
