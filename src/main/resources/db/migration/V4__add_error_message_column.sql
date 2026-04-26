-- Add error_message column to documents table for FAILED status tracking
ALTER TABLE documents ADD COLUMN IF NOT EXISTS error_message TEXT;