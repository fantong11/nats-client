package com.example.natsclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NatsRequestLogDto {

    private Long id;
    private String requestId;
    private String subject;
    private String requestPayload;
    private LocalDateTime requestTimestamp;
    private String responsePayload;
    private LocalDateTime responseTimestamp;
    private RequestStatus status;
    private String errorMessage;
    private Integer retryCount;
    private Long timeoutDuration;
    private String correlationId;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    public enum RequestStatus {
        PENDING, SUCCESS, FAILED, TIMEOUT, ERROR
    }

    public static class RequestStatusBuilder {
        private RequestStatus status = RequestStatus.PENDING;
        private Integer retryCount = 0;

        public RequestStatusBuilder status(RequestStatus status) {
            this.status = status;
            return this;
        }

        public RequestStatusBuilder retryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }
    }
}