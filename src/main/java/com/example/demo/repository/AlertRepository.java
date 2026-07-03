package com.example.demo.repository;

import com.example.demo.model.Alert;
import com.example.demo.model.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("SELECT a FROM Alert a JOIN FETCH a.device JOIN FETCH a.rule " +
           "WHERE a.rule.id IN :ruleIds AND a.status = :status")
    List<Alert> findByRuleIdInAndStatusWithDevice(
            @Param("ruleIds") Collection<Long> ruleIds,
            @Param("status") AlertStatus status);

    @Query(value = "SELECT a FROM Alert a JOIN FETCH a.device JOIN FETCH a.rule",
           countQuery = "SELECT COUNT(a) FROM Alert a")
    Page<Alert> findAllWithDeviceAndRule(Pageable pageable);

    @Query("SELECT a FROM Alert a JOIN FETCH a.device JOIN FETCH a.rule WHERE a.id = :id")
    java.util.Optional<Alert> findByIdWithDeviceAndRule(@Param("id") Long id);
}
