-- Migration para normalizar la tabla reminders
-- Agregar campos específicos en lugar de usar description como pipe-delimited

-- Agregar columna reminder_type (MEDICATION, APPOINTMENT, EVENT)
ALTER TABLE reminders ADD COLUMN reminder_type VARCHAR(20);

-- Agregar campos específicos para MEDICATION
ALTER TABLE reminders ADD COLUMN dosage VARCHAR(100);

-- Agregar campos específicos para APPOINTMENT y EVENT
ALTER TABLE reminders ADD COLUMN doctor VARCHAR(100);
ALTER TABLE reminders ADD COLUMN location VARCHAR(200);
ALTER TABLE reminders ADD COLUMN lead_time_minutes INTEGER DEFAULT 0;

-- Migrar datos existentes desde description hacia los nuevos campos
-- Formato actual: type|dosage:value|doctor:value|location:value|leadTime:value|description

UPDATE reminders
SET 
    reminder_type = CASE
        WHEN description LIKE 'medication%' THEN 'MEDICATION'
        WHEN description LIKE 'appointment%' THEN 'APPOINTMENT'
        WHEN description LIKE 'event%' THEN 'EVENT'
        ELSE 'MEDICATION' -- default
    END
WHERE reminder_type IS NULL;

-- Extraer dosage para medicamentos (formato: dosage:valor)
UPDATE reminders
SET dosage = SUBSTRING(description FROM 'dosage:([^|]+)')
WHERE reminder_type = 'MEDICATION' AND description LIKE '%dosage:%';

-- Extraer doctor para citas (formato: doctor:valor)
UPDATE reminders
SET doctor = SUBSTRING(description FROM 'doctor:([^|]+)')
WHERE reminder_type = 'APPOINTMENT' AND description LIKE '%doctor:%';

-- Extraer location para citas y eventos (formato: location:valor)
UPDATE reminders
SET location = SUBSTRING(description FROM 'location:([^|]+)')
WHERE (reminder_type = 'APPOINTMENT' OR reminder_type = 'EVENT') AND description LIKE '%location:%';

-- Extraer leadTime para citas y eventos (formato: leadTime:numero)
UPDATE reminders
SET lead_time_minutes = CAST(SUBSTRING(description FROM 'leadTime:([0-9]+)') AS INTEGER)
WHERE (reminder_type = 'APPOINTMENT' OR reminder_type = 'EVENT') AND description LIKE '%leadTime:%';

-- Limpiar description: extraer la parte después del último pipe que no sea un campo estructurado
UPDATE reminders
SET description = CASE
    WHEN description ~ '\|[^|]*$' AND description !~ '\|dosage:|doctor:|location:|leadTime:' THEN
        REGEXP_REPLACE(description, '.*\|([^|]+)$', '\1')
    ELSE
        NULL
END;

-- Agregar constraint para reminder_type
ALTER TABLE reminders ALTER COLUMN reminder_type SET NOT NULL;

-- Agregar constraint check para reminder_type
ALTER TABLE reminders ADD CONSTRAINT chk_reminder_type 
    CHECK (reminder_type IN ('MEDICATION', 'APPOINTMENT', 'EVENT'));

-- Agregar índice para reminder_type para optimizar consultas
CREATE INDEX idx_reminder_type ON reminders(reminder_type);

-- Agregar índice compuesto para consultas de recordatorios activos por tipo
CREATE INDEX idx_active_type ON reminders(active, reminder_type);

-- Comentarios para documentación
COMMENT ON COLUMN reminders.reminder_type IS 'Tipo de recordatorio: MEDICATION, APPOINTMENT, EVENT';
COMMENT ON COLUMN reminders.dosage IS 'Dosis del medicamento (solo para MEDICATION)';
COMMENT ON COLUMN reminders.doctor IS 'Nombre del doctor/especialista (solo para APPOINTMENT)';
COMMENT ON COLUMN reminders.location IS 'Ubicación del evento/cita (para APPOINTMENT y EVENT)';
COMMENT ON COLUMN reminders.lead_time_minutes IS 'Minutos de anticipación para notificar (para APPOINTMENT y EVENT). El recordatorio sonará en reminderTime - leadTimeMinutes';
COMMENT ON COLUMN reminders.description IS 'Notas adicionales o descripción libre del recordatorio';
