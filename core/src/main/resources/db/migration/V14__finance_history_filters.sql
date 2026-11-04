-- Date filters of the finance history (audit by created_at) and cash statement (movements by effective_date)
CREATE INDEX ix_finance_audit_pelada_created ON finance_audit(pelada_id, created_at DESC);
CREATE INDEX ix_finance_movements_pelada_effective ON finance_movements(pelada_id, effective_date DESC);
