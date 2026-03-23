-- Kobling til M22, eget view der som bare eksponerer det vi trenger av data.
CREATE FOREIGN TABLE IF NOT EXISTS matrikkelenhet_eiere_m22(
    id numeric(19),
    class varchar(255),
    eierforholdkodeid numeric(5),
    nr varchar(255)
) SERVER mapr2prd
  OPTIONS (schema 'MATRIKKEL2', table 'SERG_SYNC_MATRIKKELENHET_EIERE');

CREATE FOREIGN TABLE IF NOT EXISTS person_identer_m22(
    class varchar(255), -- AnnenPerson, FysiskPerson, JuridiskPerson
    nr varchar(255)
) SERVER mapr2prd
OPTIONS (schema 'MATRIKKEL2', table 'SERG_SYNC_PERSON_IDENTER');

GRANT SELECT ON matrikkelenhet_eiere_m22 TO matrikkel_serg_sync_user;
GRANT SELECT ON person_identer_m22 TO matrikkel_serg_sync_user;
