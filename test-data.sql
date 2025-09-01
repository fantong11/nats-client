-- Insert test PENDING requests to verify distributed locking recovery
INSERT INTO NATS_REQUEST_LOG (
    REQUEST_ID, SUBJECT, PAYLOAD, RESPONSE_SUBJECT, RESPONSE_ID_FIELD, 
    STATUS, REQUEST_TIMESTAMP, RESPONSE_TIMEOUT_MS
) VALUES (
    'test-req-001', 'orders.create', 
    '{"orderId": "ORD-001", "customerId": "CUST-001", "amount": 100.50}',
    'orders.response', 'orderId',
    'PENDING', SYSTIMESTAMP, 30000
);

INSERT INTO NATS_REQUEST_LOG (
    REQUEST_ID, SUBJECT, PAYLOAD, RESPONSE_SUBJECT, RESPONSE_ID_FIELD, 
    STATUS, REQUEST_TIMESTAMP, RESPONSE_TIMEOUT_MS
) VALUES (
    'test-req-002', 'users.create', 
    '{"userId": "USR-001", "email": "test@example.com", "name": "Test User"}',
    'users.response', 'userId',
    'PENDING', SYSTIMESTAMP, 30000
);

INSERT INTO NATS_REQUEST_LOG (
    REQUEST_ID, SUBJECT, PAYLOAD, RESPONSE_SUBJECT, RESPONSE_ID_FIELD, 
    STATUS, REQUEST_TIMESTAMP, RESPONSE_TIMEOUT_MS
) VALUES (
    'test-req-003', 'orders.create', 
    '{"orderId": "ORD-002", "customerId": "CUST-002", "amount": 250.75}',
    'orders.response', 'orderId',
    'PENDING', SYSTIMESTAMP, 30000
);

COMMIT;
EXIT;