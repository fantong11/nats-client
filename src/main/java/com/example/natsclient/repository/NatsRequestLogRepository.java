package com.example.natsclient.repository;

import com.example.natsclient.entity.NatsRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NatsRequestLogRepository extends JpaRepository<NatsRequestLog, Long> {

    Optional<NatsRequestLog> findByRequestId(String requestId);


    List<NatsRequestLog> findByStatus(NatsRequestLog.RequestStatus status);

    List<NatsRequestLog> findByStatusAndCreatedDateBefore(
            NatsRequestLog.RequestStatus status, 
            LocalDateTime dateTime
    );

    @Modifying
    @Query("UPDATE NatsRequestLog n SET n.status = :status, n.responsePayload = :responsePayload, " +
           "n.responseTimestamp = :responseTimestamp, n.updatedBy = :updatedBy WHERE n.requestId = :requestId")
    int updateResponseByRequestId(
            @Param("requestId") String requestId,
            @Param("status") NatsRequestLog.RequestStatus status,
            @Param("responsePayload") String responsePayload,
            @Param("responseTimestamp") LocalDateTime responseTimestamp,
            @Param("updatedBy") String updatedBy
    );

    @Modifying
    @Query("UPDATE NatsRequestLog n SET n.status = :status, n.errorMessage = :errorMessage, " +
           "n.retryCount = n.retryCount + 1, n.updatedBy = :updatedBy WHERE n.requestId = :requestId")
    int updateErrorByRequestId(
            @Param("requestId") String requestId,
            @Param("status") NatsRequestLog.RequestStatus status,
            @Param("errorMessage") String errorMessage,
            @Param("updatedBy") String updatedBy
    );

    @Query("SELECT COUNT(n) FROM NatsRequestLog n WHERE n.status = :status")
    long countByStatus(@Param("status") NatsRequestLog.RequestStatus status);

    @Query("SELECT n FROM NatsRequestLog n WHERE n.subject = :subject AND n.status = :status ORDER BY n.createdDate DESC")
    List<NatsRequestLog> findBySubjectAndStatusOrderByCreatedDateDesc(
            @Param("subject") String subject,
            @Param("status") NatsRequestLog.RequestStatus status
    );
    
    @Query("SELECT n FROM NatsRequestLog n WHERE n.requestPayload LIKE %:correlationId% AND n.status IN ('PENDING', 'PROCESSING')")
    List<NatsRequestLog> findByCorrelationIdInPayload(@Param("correlationId") String correlationId);
}