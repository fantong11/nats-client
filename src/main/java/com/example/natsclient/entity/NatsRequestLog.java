package com.example.natsclient.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "NATS_REQUEST_LOG")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NatsRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nats_request_log_seq")
    @SequenceGenerator(name = "nats_request_log_seq", sequenceName = "NATS_REQUEST_LOG_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REQUEST_ID", nullable = false, unique = true)
    private String requestId;

    @Column(name = "SUBJECT", nullable = false)
    private String subject;

    @Lob
    @Column(name = "REQUEST_PAYLOAD")
    private String requestPayload;

    @Column(name = "REQUEST_TIMESTAMP")
    private LocalDateTime requestTimestamp;

    @Lob
    @Column(name = "RESPONSE_PAYLOAD")
    private String responsePayload;

    @Column(name = "RESPONSE_TIMESTAMP")
    private LocalDateTime responseTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @Lob
    @Column(name = "ERROR_MESSAGE")
    private String errorMessage;

    @Column(name = "RETRY_COUNT")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "TIMEOUT_DURATION")
    private Long timeoutDuration;


    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "UPDATED_BY")
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "UPDATED_DATE")
    private LocalDateTime updatedDate;

    public enum RequestStatus {
        PENDING, SUCCESS, FAILED, TIMEOUT, ERROR
    }
}