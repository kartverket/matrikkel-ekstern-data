-- Forretningsfiltrert view av eierdiff som skjuler SERG-endringer vi ikke forventer i matrikkelen.
CREATE OR REPLACE VIEW eierdiff_filtered AS
SELECT
    e.id,
    e.nr,
    e.eierforholdkodeid,
    e.diff_type
FROM eierdiff e
WHERE e.diff_type <> 'missing_in_matrikkelenhet_eiere'
   OR EXISTS (
    SELECT 1
    FROM person_identer_local p
    WHERE p.nr = e.nr
      AND p.class <> 'AnnenPerson'
);

CREATE INDEX IF NOT EXISTS idx_person_identer_local_nr ON person_identer_local (nr);
CREATE INDEX IF NOT EXISTS idx_eierdiff_nr ON eierdiff (nr);