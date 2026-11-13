-- Tag receipts survive deletion or merging, just as game and movement receipts do.
ALTER TABLE mcp_operation DROP CONSTRAINT mcp_operation_resource_type_check;
ALTER TABLE mcp_operation ADD CONSTRAINT mcp_operation_resource_type_check
    CHECK (resource_type IN ('GAME', 'MOVEMENT', 'TAG'));
