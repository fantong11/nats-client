package com.example.natsclient.repository.impl;

import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.repository.JdbcNatsRequestLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class JdbcNatsRequestLogRepositoryImpl implements JdbcNatsRequestLogRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
        INSERT INTO NATS_REQUEST_LOG (
            REQUEST_ID, SUBJECT, REQUEST_PAYLOAD, REQUEST_TIMESTAMP,
            RESPONSE_PAYLOAD, RESPONSE_TIMESTAMP, STATUS, ERROR_MESSAGE,
            RETRY_COUNT, TIMEOUT_DURATION, CORRELATION_ID, CREATED_BY,
            UPDATED_BY
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    private static final String UPDATE_SQL = """
        UPDATE NATS_REQUEST_LOG SET
            REQUEST_ID = ?, SUBJECT = ?, REQUEST_PAYLOAD = ?, REQUEST_TIMESTAMP = ?,
            RESPONSE_PAYLOAD = ?, RESPONSE_TIMESTAMP = ?, STATUS = ?, ERROR_MESSAGE = ?,
            RETRY_COUNT = ?, TIMEOUT_DURATION = ?, CORRELATION_ID = ?, UPDATED_BY = ?,
            UPDATED_DATE = ?
        WHERE ID = ?
    """;

    private static final String SELECT_BY_ID_SQL = "SELECT * FROM NATS_REQUEST_LOG WHERE ID = ?";
    private static final String SELECT_BY_REQUEST_ID_SQL = "SELECT * FROM NATS_REQUEST_LOG WHERE REQUEST_ID = ?";
    private static final String SELECT_BY_CORRELATION_ID_SQL = "SELECT * FROM NATS_REQUEST_LOG WHERE CORRELATION_ID = ?";
    private static final String SELECT_BY_STATUS_SQL = "SELECT * FROM NATS_REQUEST_LOG WHERE STATUS = ?";
    private static final String SELECT_BY_STATUS_AND_DATE_SQL = 
        "SELECT * FROM NATS_REQUEST_LOG WHERE STATUS = ? AND CREATED_DATE < ?";
    private static final String SELECT_BY_SUBJECT_AND_STATUS_SQL = 
        "SELECT * FROM NATS_REQUEST_LOG WHERE SUBJECT = ? AND STATUS = ? ORDER BY CREATED_DATE DESC";
    private static final String COUNT_BY_STATUS_SQL = "SELECT COUNT(*) FROM NATS_REQUEST_LOG WHERE STATUS = ?";
    private static final String SELECT_ALL_SQL = "SELECT * FROM NATS_REQUEST_LOG";
    private static final String DELETE_BY_ID_SQL = "DELETE FROM NATS_REQUEST_LOG WHERE ID = ?";
    private static final String DELETE_ALL_SQL = "DELETE FROM NATS_REQUEST_LOG";
    private static final String COUNT_ALL_SQL = "SELECT COUNT(*) FROM NATS_REQUEST_LOG";
    private static final String EXISTS_BY_ID_SQL = "SELECT COUNT(*) FROM NATS_REQUEST_LOG WHERE ID = ?";

    private static final String UPDATE_RESPONSE_SQL = """
        UPDATE NATS_REQUEST_LOG SET 
            STATUS = ?, RESPONSE_PAYLOAD = ?, RESPONSE_TIMESTAMP = ?, 
            UPDATED_BY = ?, UPDATED_DATE = CURRENT_TIMESTAMP 
        WHERE REQUEST_ID = ?
    """;

    private static final String UPDATE_ERROR_SQL = """
        UPDATE NATS_REQUEST_LOG SET 
            STATUS = ?, ERROR_MESSAGE = ?, RETRY_COUNT = RETRY_COUNT + 1, 
            UPDATED_BY = ?, UPDATED_DATE = CURRENT_TIMESTAMP 
        WHERE REQUEST_ID = ?
    """;

    private final RowMapper<NatsRequestLogDto> rowMapper = new NatsRequestLogDtoRowMapper();

    @Override
    public NatsRequestLogDto save(NatsRequestLogDto requestLog) {
        if (requestLog.getId() == null) {
            return insert(requestLog);
        } else {
            update(requestLog);
            return requestLog;
        }
    }

    private NatsRequestLogDto insert(NatsRequestLogDto requestLog) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"ID"});
            setInsertPreparedStatementValues(ps, requestLog);
            return ps;
        }, keyHolder);

        Long id = keyHolder.getKey().longValue();
        requestLog.setId(id);
        return requestLog;
    }

    private void update(NatsRequestLogDto requestLog) {
        requestLog.setUpdatedDate(LocalDateTime.now());
        jdbcTemplate.update(UPDATE_SQL,
                requestLog.getRequestId(),
                requestLog.getSubject(),
                requestLog.getRequestPayload(),
                Timestamp.valueOf(requestLog.getRequestTimestamp()),
                requestLog.getResponsePayload(),
                requestLog.getResponseTimestamp() != null ? Timestamp.valueOf(requestLog.getResponseTimestamp()) : null,
                requestLog.getStatus().name(),
                requestLog.getErrorMessage(),
                requestLog.getRetryCount(),
                requestLog.getTimeoutDuration(),
                requestLog.getCorrelationId(),
                requestLog.getUpdatedBy(),
                Timestamp.valueOf(requestLog.getUpdatedDate()),
                requestLog.getId()
        );
    }

    private void setInsertPreparedStatementValues(PreparedStatement ps, NatsRequestLogDto requestLog) throws SQLException {
        ps.setString(1, requestLog.getRequestId());
        ps.setString(2, requestLog.getSubject());
        ps.setString(3, requestLog.getRequestPayload());
        ps.setTimestamp(4, requestLog.getRequestTimestamp() != null ? Timestamp.valueOf(requestLog.getRequestTimestamp()) : null);
        ps.setString(5, requestLog.getResponsePayload());
        ps.setTimestamp(6, requestLog.getResponseTimestamp() != null ? Timestamp.valueOf(requestLog.getResponseTimestamp()) : null);
        ps.setString(7, requestLog.getStatus().name());
        ps.setString(8, requestLog.getErrorMessage());
        ps.setInt(9, requestLog.getRetryCount() != null ? requestLog.getRetryCount() : 0);
        ps.setLong(10, requestLog.getTimeoutDuration() != null ? requestLog.getTimeoutDuration() : 0);
        ps.setString(11, requestLog.getCorrelationId());
        ps.setString(12, requestLog.getCreatedBy());
        ps.setString(13, requestLog.getUpdatedBy());
    }

    @Override
    public Optional<NatsRequestLogDto> findById(Long id) {
        try {
            NatsRequestLogDto result = jdbcTemplate.queryForObject(SELECT_BY_ID_SQL, rowMapper, id);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<NatsRequestLogDto> findByRequestId(String requestId) {
        try {
            NatsRequestLogDto result = jdbcTemplate.queryForObject(SELECT_BY_REQUEST_ID_SQL, rowMapper, requestId);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<NatsRequestLogDto> findByCorrelationId(String correlationId) {
        try {
            NatsRequestLogDto result = jdbcTemplate.queryForObject(SELECT_BY_CORRELATION_ID_SQL, rowMapper, correlationId);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<NatsRequestLogDto> findByStatus(NatsRequestLogDto.RequestStatus status) {
        return jdbcTemplate.query(SELECT_BY_STATUS_SQL, rowMapper, status.name());
    }

    @Override
    public List<NatsRequestLogDto> findByStatusAndCreatedDateBefore(
            NatsRequestLogDto.RequestStatus status, LocalDateTime dateTime) {
        return jdbcTemplate.query(SELECT_BY_STATUS_AND_DATE_SQL, rowMapper, 
                status.name(), Timestamp.valueOf(dateTime));
    }

    @Override
    public int updateResponseByRequestId(String requestId, NatsRequestLogDto.RequestStatus status,
                                         String responsePayload, LocalDateTime responseTimestamp, String updatedBy) {
        return jdbcTemplate.update(UPDATE_RESPONSE_SQL,
                status.name(), responsePayload, Timestamp.valueOf(responseTimestamp), updatedBy, requestId);
    }

    @Override
    public int updateErrorByRequestId(String requestId, NatsRequestLogDto.RequestStatus status,
                                      String errorMessage, String updatedBy) {
        return jdbcTemplate.update(UPDATE_ERROR_SQL,
                status.name(), errorMessage, updatedBy, requestId);
    }

    @Override
    public long countByStatus(NatsRequestLogDto.RequestStatus status) {
        Long count = jdbcTemplate.queryForObject(COUNT_BY_STATUS_SQL, Long.class, status.name());
        return count != null ? count : 0;
    }

    @Override
    public List<NatsRequestLogDto> findBySubjectAndStatusOrderByCreatedDateDesc(String subject, NatsRequestLogDto.RequestStatus status) {
        return jdbcTemplate.query(SELECT_BY_SUBJECT_AND_STATUS_SQL, rowMapper, subject, status.name());
    }

    @Override
    public List<NatsRequestLogDto> findAll() {
        return jdbcTemplate.query(SELECT_ALL_SQL, rowMapper);
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update(DELETE_BY_ID_SQL, id);
    }

    @Override
    public boolean existsById(Long id) {
        Long count = jdbcTemplate.queryForObject(EXISTS_BY_ID_SQL, Long.class, id);
        return count != null && count > 0;
    }

    @Override
    public void deleteAll() {
        jdbcTemplate.update(DELETE_ALL_SQL);
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject(COUNT_ALL_SQL, Long.class);
        return count != null ? count : 0;
    }

    private static class NatsRequestLogDtoRowMapper implements RowMapper<NatsRequestLogDto> {
        @Override
        public NatsRequestLogDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return NatsRequestLogDto.builder()
                    .id(rs.getLong("ID"))
                    .requestId(rs.getString("REQUEST_ID"))
                    .subject(rs.getString("SUBJECT"))
                    .requestPayload(rs.getString("REQUEST_PAYLOAD"))
                    .requestTimestamp(rs.getTimestamp("REQUEST_TIMESTAMP") != null ? 
                        rs.getTimestamp("REQUEST_TIMESTAMP").toLocalDateTime() : null)
                    .responsePayload(rs.getString("RESPONSE_PAYLOAD"))
                    .responseTimestamp(rs.getTimestamp("RESPONSE_TIMESTAMP") != null ? 
                        rs.getTimestamp("RESPONSE_TIMESTAMP").toLocalDateTime() : null)
                    .status(NatsRequestLogDto.RequestStatus.valueOf(rs.getString("STATUS")))
                    .errorMessage(rs.getString("ERROR_MESSAGE"))
                    .retryCount(rs.getInt("RETRY_COUNT"))
                    .timeoutDuration(rs.getLong("TIMEOUT_DURATION"))
                    .correlationId(rs.getString("CORRELATION_ID"))
                    .createdBy(rs.getString("CREATED_BY"))
                    .updatedBy(rs.getString("UPDATED_BY"))
                    .createdDate(rs.getTimestamp("CREATED_DATE") != null ? 
                        rs.getTimestamp("CREATED_DATE").toLocalDateTime() : null)
                    .updatedDate(rs.getTimestamp("UPDATED_DATE") != null ? 
                        rs.getTimestamp("UPDATED_DATE").toLocalDateTime() : null)
                    .build();
        }
    }
}