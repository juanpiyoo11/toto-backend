-- Migration to make email and password nullable for elderly users
-- Run this manually or use Flyway/Liquibase

-- Make email nullable
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- Make password nullable  
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Note: You may also need to drop the unique constraint on email if you want multiple elderly with null emails
-- ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
