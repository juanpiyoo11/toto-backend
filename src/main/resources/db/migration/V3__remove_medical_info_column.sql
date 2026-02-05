-- Migration to remove medical_info column from users table
-- This column is no longer needed as medical information is not stored

ALTER TABLE users DROP COLUMN IF EXISTS medical_info;
