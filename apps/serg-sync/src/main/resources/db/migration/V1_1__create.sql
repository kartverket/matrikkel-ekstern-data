CREATE TABLE IF NOT EXISTS hendelse
(
    sekvensnummer BIGINT PRIMARY KEY NOT NULL,
    hendelse      JSONB               NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_hendelse_hendelse ON hendelse USING gin (hendelse);

CREATE TABLE IF NOT EXISTS serg_dokument
(
    matrikkelenhetId BIGINT primary key NOT NULL,
    hendelse         JSONB              NOT NULL,
    formueobjekt     JSONB,
    status           VARCHAR            NOT NULL CHECK ( status IN ('KREVER_SYNKRONISERING', 'SYNKRONISERT', 'FEIL', 'SLETTET') ),
    kommentar        VARCHAR,
    sistOppdatert    timestamp          NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_serg_dokument_status ON serg_dokument (status);
CREATE INDEX IF NOT EXISTS idx_serg_dokument_sistOppdatert ON serg_dokument (status, sistOppdatert ASC);
CREATE INDEX IF NOT EXISTS idx_serg_dokument_hendelse ON serg_dokument USING gin (hendelse);
CREATE INDEX IF NOT EXISTS idx_serg_dokument_formueobjekt ON serg_dokument USING gin (formueobjekt);


CREATE TABLE IF NOT EXISTS keyvalue
(
    key   VARCHAR PRIMARY KEY NOT NULL,
    value VARCHAR             NOT NULL
);

INSERT INTO keyvalue
VALUES ('sekvensnummer', '1');
