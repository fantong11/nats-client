package com.example.natsclient.repository;

import com.example.natsclient.dto.NatsRequestLogDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JdbcNatsRequestLogRepository {

    NatsRequestLogDto save(NatsRequestLogDto requestLog);
    
    Optional<NatsRequestLogDto> findById(Long id);
    
    Optional<NatsRequestLogDto> findByRequestId(String requestId);
    
    Optional<NatsRequestLogDto> findByCorrelationId(String correlationId);
    
    List<NatsRequestLogDto> findByStatus(NatsRequestLogDto.RequestStatus status);
    
    List<NatsRequestLogDto> findByStatusAndCreatedDateBefore(
            NatsRequestLogDto.RequestStatus status, 
            LocalDateTime dateTime
    );
    
    int updateResponseByRequestId(
            String requestId,
            NatsRequestLogDto.RequestStatus status,
            String responsePayload,
            LocalDateTime responseTimestamp,
            String updatedBy
    );
    
    int updateErrorByRequestId(
            String requestId,
            NatsRequestLogDto.RequestStatus status,
            String errorMessage,
            String updatedBy
    );
    
    long countByStatus(NatsRequestLogDto.RequestStatus status);
    
    List<NatsRequestLogDto> findBySubjectAndStatusOrderByCreatedDateDesc(
            String subject,
            NatsRequestLogDto.RequestStatus status
    );
    
    List<NatsRequestLogDto> findAll();
    
    void deleteById(Long id);
    
    boolean existsById(Long id);
    
    void deleteAll();
    
    long count();
}