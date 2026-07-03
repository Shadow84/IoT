package com.example.demo.repository;

import com.example.demo.model.OutboxEvent;
import com.example.demo.model.OutboxEvent.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Atomically claims a batch of PENDING events for this node only.
     * {@code FOR UPDATE SKIP LOCKED} ensures that rows already locked by another
     * cluster node are skipped rather than waited on, so no two nodes process the
     * same event concurrently.
     *
     * <p>Must be called inside a {@code @Transactional} method — the lock is held
     * until the surrounding transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status")
    List<OutboxEvent> findByStatusForUpdate(@Param("status") OutboxStatus status);
}
