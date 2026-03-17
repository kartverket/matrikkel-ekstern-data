-- View som pakker ut formueobjekt-json til ett mer brukbart format
CREATE OR REPLACE VIEW serg_eiere_normalized AS
SELECT DISTINCT
    d.matrikkelenhetid AS id,
    COALESCE(
        pi ->> 'foedselsnummer',
        pi ->> 'dNummer',
        pi ->> 'organisasjonsnummer',
        pi ->> 'loepenummer'
    ) AS nr,
    CASE eo -> 'eierforhold' ->> 'eiernivaa'
        WHEN 'eiendomsrett' THEN 18
        WHEN 'feste' THEN 19
        WHEN 'framfeste1' THEN 20
        WHEN 'framfeste2' THEN 21
        WHEN 'framfeste3' THEN 22
    END::integer AS eierforholdkodeid,
    CASE
        WHEN pi ->> 'foedselsnummer' IS NOT NULL THEN 'foedselsnummer'
        WHEN pi ->> 'dNummer' IS NOT NULL THEN 'dNummer'
        WHEN pi ->> 'organisasjonsnummer' IS NOT NULL THEN 'organisasjonsnummer'
        WHEN pi ->> 'loepenummer' IS NOT NULL THEN 'loepenummer'
    END AS ident_type
FROM serg_dokument d
CROSS JOIN LATERAL jsonb_array_elements(d.formueobjekt::jsonb -> 'eieropplysninger') AS eo
CROSS JOIN LATERAL (SELECT eo -> 'personidentifikator' AS pi) p
WHERE COALESCE(
    pi ->> 'foedselsnummer',
    pi ->> 'dNummer',
    pi ->> 'organisasjonsnummer',
    pi ->> 'loepenummer'
) IS NOT NULL
  AND COALESCE((pi ->> 'ukjentRettighetshaver')::boolean, false) = false
  AND CASE eo -> 'eierforhold' ->> 'eiernivaa'
      WHEN 'eiendomsrett' THEN 18
      WHEN 'feste' THEN 19
      WHEN 'framfeste1' THEN 20
      WHEN 'framfeste2' THEN 21
      WHEN 'framfeste3' THEN 22
  END IS NOT NULL
  AND d.status <> 'SLETTET';

DROP MATERIALIZED VIEW IF EXISTS eierdiff;
DROP MATERIALIZED VIEW IF EXISTS person_identer_local;
DROP MATERIALIZED VIEW IF EXISTS matrikkel_eiere_local;

-- Materialisering av M22 data for å unngå at masse data må streames senere
CREATE MATERIALIZED VIEW matrikkel_eiere_local AS
SELECT DISTINCT
    id,
    nr::text AS nr,
    eierforholdkodeid::integer AS eierforholdkodeid
FROM matrikkelenhet_eiere_m22
WHERE eierforholdkodeid >= 18;

-- Materialisering av M22 data for å unngå at masse data må streames senere
CREATE MATERIALIZED VIEW person_identer_local AS
SELECT DISTINCT
    class,
    nr::text AS nr
FROM person_identer_m22;

-- Kalkuleringen av mismatch mellom serg_eiere og matrikkel_eiere
CREATE MATERIALIZED VIEW eierdiff AS
WITH serg_eiere AS (
    SELECT DISTINCT
        id,
        nr,
        eierforholdkodeid
    FROM serg_eiere_normalized
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
     )
SELECT *
FROM missing_in_matrikkel
UNION ALL
SELECT *
FROM extra_in_matrikkel;

CREATE OR REPLACE FUNCTION refresh_eierdiff()
    RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    REFRESH MATERIALIZED VIEW person_identer_local;
    REFRESH MATERIALIZED VIEW matrikkel_eiere_local;
    REFRESH MATERIALIZED VIEW eierdiff;
END;
$$;

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
