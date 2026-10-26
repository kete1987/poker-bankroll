-- Durable receipts, independent of MCP sessions. Pending rows never commit: the resource and
-- its receipt are written in one transaction. No foreign keys: traces survive resource deletion.
CREATE TABLE mcp_operation (
    idempotency_key UUID PRIMARY KEY,
    tool_name VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_type VARCHAR(16) NOT NULL CHECK (resource_type IN ('GAME', 'MOVEMENT')),
    resource_id BIGINT,
    result JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    invalidated BOOLEAN NOT NULL DEFAULT false,
    CHECK ((result IS NULL AND resource_id IS NULL) OR
           (result IS NOT NULL AND resource_id IS NOT NULL AND completed_at IS NOT NULL)),
    CHECK (NOT invalidated OR (result IS NULL AND resource_id IS NULL))
);
CREATE INDEX mcp_operation_created ON mcp_operation (created_at DESC, idempotency_key);

