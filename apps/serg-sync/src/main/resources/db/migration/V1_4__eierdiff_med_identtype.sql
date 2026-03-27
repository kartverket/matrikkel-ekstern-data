DROP MATERIALIZED VIEW IF EXISTS avvik;

-- Kalkuleringen av mismatch mellom serg_eiere og matrikkel_eiere
CREATE MATERIALIZED VIEW avvik AS
WITH serg_eiere AS (
    SELECT DISTINCT
        id,
        nr,
        eierforholdkodeid
    FROM serg_eiere_normalisert
    WHERE ident_type <> 'loepenummer'
),
     matrikkel_eiere AS (
         SELECT DISTINCT
             id,
             nr,
             eierforholdkodeid
         FROM matrikkel_eiere_local
     ),
     missing_in_matrikkel AS (
         SELECT
             s.id,
             s.nr,
             s.eierforholdkodeid,
             'missing_in_matrikkelenhet_eiere'::text AS diff_type
         FROM serg_eiere s
         EXCEPT
         SELECT
             m.id,
             m.nr,
             m.eierforholdkodeid,
             'missing_in_matrikkelenhet_eiere'::text AS diff_type
         FROM matrikkel_eiere m
     ),
     extra_in_matrikkel AS (
         SELECT
             m.id,
             m.nr,
             m.eierforholdkodeid,
             'extra_in_matrikkelenhet_eiere'::text AS diff_type
         FROM matrikkel_eiere m
         EXCEPT
         SELECT
             s.id,
             s.nr,
             s.eierforholdkodeid,
             'extra_in_matrikkelenhet_eiere'::text AS diff_type
         FROM serg_eiere s
     ),
     alle_avvik AS (
         SELECT *
         FROM missing_in_matrikkel
         UNION ALL
         SELECT *
         FROM extra_in_matrikkel
     )
SELECT
    a.id,
    a.nr,
    a.eierforholdkodeid,
    a.diff_type,
    p.class as ident_type
FROM alle_avvik a
LEFT JOIN person_identer_local p ON (a.nr = p.nr)
WHERE a.diff_type <> 'missing_in_matrikkelenhet_eiere'
   OR EXISTS (
    SELECT 1
    FROM person_identer_local p
    WHERE p.nr = a.nr
      AND p.class <> 'AnnenPerson'
);

CREATE INDEX IF NOT EXISTS idx_avvik_nr ON avvik (nr);