package com.server.contestControl.contestServer.moderation.repository;

import com.server.contestControl.contestServer.moderation.entity.ContestModerationAuditLog;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ContestModerationAuditLogSearchRepositoryImpl implements ContestModerationAuditLogSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ContestModerationAuditLog> search(
            Long contestId,
            Long teamId,
            Long adminId,
            ModerationActionType actionType,
            LocalDateTime fromTime,
            LocalDateTime toTime
    ) {
        var criteriaBuilder = entityManager.getCriteriaBuilder();
        var query = criteriaBuilder.createQuery(ContestModerationAuditLog.class);
        var root = query.from(ContestModerationAuditLog.class);

        root.fetch("contest", JoinType.INNER);
        root.fetch("team", JoinType.LEFT);
        root.fetch("admin", JoinType.INNER);
        root.fetch("problem", JoinType.LEFT);

        List<Predicate> predicates = new ArrayList<>();
        if (contestId != null) {
            predicates.add(criteriaBuilder.equal(root.get("contest").get("id"), contestId));
        }
        if (teamId != null) {
            predicates.add(criteriaBuilder.equal(root.get("team").get("id"), teamId));
        }
        if (adminId != null) {
            predicates.add(criteriaBuilder.equal(root.get("admin").get("id"), adminId));
        }
        if (actionType != null) {
            predicates.add(criteriaBuilder.equal(root.get("actionType"), actionType));
        }
        if (fromTime != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), fromTime));
        }
        if (toTime != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), toTime));
        }

        query.select(root)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(
                        criteriaBuilder.desc(root.get("createdAt")),
                        criteriaBuilder.desc(root.get("id"))
                );

        return entityManager.createQuery(query).getResultList();
    }
}
