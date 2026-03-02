CREATE TABLE IF NOT EXISTS serg_document
(
    matrikkelenhetId BIGINT primary key NOT NULL,
    hendelse         JSONB               NOT NULL,
    formueobjekt     JSONB,
    status           VARCHAR             NOT NULL, -- PENDING, FETCHED, FAILED, OK,
    kommentar        VARCHAR,
    sistOppdatert    timestamp           NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_serg_status ON serg_document (status);
CREATE INDEX IF NOT EXISTS idx_serg_hendelse ON serg_document USING gin (hendelse);
CREATE INDEX IF NOT EXISTS idx_serg_formueobjekt ON serg_document USING gin (formueobjekt);


CREATE TABLE IF NOT EXISTS keyvalue
(
    key             VARCHAR PRIMARY KEY NOT NULL,
    value           VARCHAR NOT NULL
);

INSERT INTO keyvalue VALUES ('sekvensnummer', '0');