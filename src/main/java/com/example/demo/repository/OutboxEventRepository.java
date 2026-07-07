package com.example.demo.repository;

import com.example.demo.model.OutboxEvent;
import com.example.demo.model.OutboxEvent.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims one sentinel row per {@code (deviceId, metricName)} partition.
     *
     * <p>For every distinct partition that has at least one {@code PENDING} event,
     * this query returns the single row with the lowest {@code id} in that
     * partition and immediately locks it with {@code FOR UPDATE SKIP LOCKED}.
     *
     * <p>Because the lock is taken on the oldest row of each partition, a competing
     * scheduler node will see that row as locked and skip the entire partition via
     * {@code SKIP LOCKED} — it will not attempt to claim any row in that partition
     * during this tick. This guarantees that each partition is owned by at most one
     * node per scheduler cycle, preventing any cross-node interleaving of events
     * within the same {@code (deviceId, metricName)} sequence.
     *
     * <p>Must be called inside a {@code @Transactional} method — the lock is held
     * until the surrounding transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status = :status
              AND e.id = (
                SELECT MIN(e2.id) FROM OutboxEvent e2
                WHERE e2.deviceId   = e.deviceId
                  AND e2.metricName = e.metricName
                  AND e2.status     = :status
              )
            ORDER BY e.id ASC
            """)
    List<OutboxEvent> claimPartitionSentinels(@Param("status") OutboxStatus status);

    /**
     * Loads all {@code PENDING} events for a single {@code (deviceId, metricName)}
     * partition in strict {@code id ASC} order.
     *
     * <p>Called after {@link #claimPartitionSentinels} has already locked the
     * sentinel (oldest) row for this partition in the current transaction.
     * Because the sentinel lock blocks competing nodes from entering this
     * partition, it is safe to read all remaining rows here without an additional
     * {@code FOR UPDATE} — they are implicitly protected by the sentinel lock.
     */
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status     = :status
              AND e.deviceId   = :deviceId
              AND e.metricName = :metricName
            ORDER BY e.id ASC
            """)
    List<OutboxEvent> findAllPendingByPartition(
            @Param("status") OutboxStatus status,
            @Param("deviceId") Long deviceId,
            @Param("metricName") String metricName);
}
