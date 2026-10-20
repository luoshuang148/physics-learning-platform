-- Link work deliveries to their durable item so pre-claim failures can be recovered.
ALTER TABLE report_outbox ADD COLUMN work_item_id BIGINT NULL;
CREATE INDEX idx_report_outbox_work_item ON report_outbox(work_item_id, id);
UPDATE report_outbox
SET work_item_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(payload, '$.itemId')) AS UNSIGNED)
WHERE JSON_VALID(payload) AND JSON_EXTRACT(payload, '$.kind') IS NULL;
