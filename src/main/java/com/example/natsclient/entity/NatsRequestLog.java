package com.example.natsclient.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "NATS_REQUEST_LOG")
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
    private RequestStatus status = RequestStatus.PENDING;

    @Lob
    @Column(name = "ERROR_MESSAGE")
    private String errorMessage;

    @Column(name = "RETRY_COUNT")
    private Integer retryCount = 0;

    @Column(name = "TIMEOUT_DURATION")
    private Long timeoutDuration;

    @Column(name = "CORRELATION_ID")
    private String correlationId;

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

    public NatsRequestLog() {}

    public NatsRequestLog(String requestId, String subject, String requestPayload, String correlationId) {
        this.requestId = requestId;
        this.subject = subject;
        this.requestPayload = requestPayload;
        this.correlationId = correlationId;
        this.requestTimestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getRequestPayload() { return requestPayload; }
    public void setRequestPayload(String requestPayload) { this.requestPayload = requestPayload; }

    public LocalDateTime getRequestTimestamp() { return requestTimestamp; }
    public void setRequestTimestamp(LocalDateTime requestTimestamp) { this.requestTimestamp = requestTimestamp; }

    public String getResponsePayload() { return responsePayload; }
    public void setResponsePayload(String responsePayload) { this.responsePayload = responsePayload; }

    public LocalDateTime getResponseTimestamp() { return responseTimestamp; }
    public void setResponseTimestamp(LocalDateTime responseTimestamp) { this.responseTimestamp = responseTimestamp; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Long getTimeoutDuration() { return timeoutDuration; }
    public void setTimeoutDuration(Long timeoutDuration) { this.timeoutDuration = timeoutDuration; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; }
}