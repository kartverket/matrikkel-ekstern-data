# SERG Sync

Applikasjon for henting og synkroninsering av SERG-eiere fra skatteetateten til M22.

Applikasjonen består av to separate jobber, `HendelserSync` og `FormueobjektSync`, som bygger opp konseptet `SergDokument`.

Ett `SergDokument` er en samling av informasjon fra SERG for en gitt *matrikkelenhetId*. 
Dokumentet holder på siste *hendelse*, *formueobjekt* og en *status* som indikerer hvorvidt *formueobjektet* er synkronisert.

## Hendelser Sync

Henter hendelser fra SERG i bolk på `${config.antall}` (default: 1000), om jobben ser at det sannsynligvis finnes mer data hentes disse fortløpende.
Om det er sannsynlig at jobben har fått all dataen den trenger så ventes `${config.interval}` (default 60s) før det sjekkes på nytt.

**Prosess:**
1. Henting av bolk fra SERG fra forrige `sekvensnummer` (Retry: 3 ved feil)
2. For hver hendelse:
   1. Hendelse persisteres i `hendelser` tabellen
   2. SergDokument oppdateres med ny hendelse. *status* settes til `KREVER_SYNKRONISERING` så sant *hendelsestype* ikke er `SLETTET`
3. `sekvensnummer` oppdateres


## Formueobjekt Sync

Henter formueobjekt fra SERG i bolk på `${config.antall}` (default: 10), om jobben ser at det sannsynligvis finnes mer data hentes disse fortløpende.
Om det er sannsynlig at jobben har fått all dataen den trenger så ventes `${config.interval}` (default 60s) før det sjekkes på nytt.

**Prosess:**
1. Hent de `${config.antall}` eldste innslagene fra `serg_dokument` tabellen med status `KREVER_SYNKRONISERING`.
2. I parallell (10 om gangen) hentes formueobjektene (Retry: 3 ved feil)
3. Formueobjekt oppdateres i `serg_dokument` tabellen


## Henting av M22 data

Postgres databasen til applikasjonen er koblet til Matrikkelen(M22) sin oracle database vha `oracle_fwd`.  
Dette muliggjør å opprette `foreign tables` som fra appen sitt ståsted ser ut som normale postgres tabeller, men informasjonen hentes live fra Oracle.

I appen defineres to `foreign tables`; `matrikkelenhet_eiere_m22` og `person_identer_m22`.
For å minimere mengden data som hentes inn er begge disse basert på dedikerte `views` i M22.

Begge tabellene er også materalisert i henholdsvis `matrikkel_eiere_local` og `person_identer_local` for å sikre ytelse og minimere trykk mot Oracle basen.
(Begge refreshes ved kjøre `refresh_avvik()`)

## Avviksanalyse

Avviksanalysen kan kjøres når `serg_dokument` er oppdatert med nyeste informasjon fra SERG. 
For å få nyeste data til analysen kalles så `refresh_avvik()`. Dette henter ned nyeste informasjon fra M22, og kjøres avviksanalysen.

Resultatet persisteres i ett materalisert view `avvik` på følgende format;

|id| nr          |eierforholdkodeid|diff_type|
|--|-------------|-----------------|---------|
|107203557| 01025XXXXXX |18|missing_in_matrikkelenhet_eiere|
|107203557| 06126XXXXXX |18|missing_in_matrikkelenhet_eiere|
|107203557| 05058XXXXXX |18|extra_in_matrikkelenhet_eiere|
|107203557| 16088XXXXXX |18|extra_in_matrikkelenhet_eiere|

Visse avvik er by-design, f.eks ved mottak av løpenummer fra SERG så skal ikke disse lagres i M22.
Disse forventede avvikene filtreres bort direkte i `avvik`.

## Uttrekk av serg avvik til patch-kjøring

Når en vesentlig mengde avvik har akkumulert seg bør man vurdere å gjøre en manuell patch for å håndtere avvikene.
For dette finnes det en patch i matrikkelen [MatrikkelDBPatch_TH2587.kt](https://github.com/kartverket/matrikkel/blob/main/server/mat-patcheprogram/src/main/java/no/statkart/matrikkel/patch/MatrikkelDBPatch_TH2587.kt) som kan gjøres.

Siden dataene patchen trenger kan inneholde FNR/DNR/OrgNR må det gjøres manuelt uttrekk som krypteres og sendes over til app-drift for kjøring.

For å gjøre uttrekket brukes følgende SQL (**NB!!**: Husk å huke av for kolonne-headere);
```sql
select a.*, s.sistoppdatert from avvik a
LEFT JOIN serg_dokument s ON a.id::bigint = s.matrikkelenhetid
WHERE s.sistoppdatert IS NULL OR s.sistoppdatert < NOW() - INTERVAL '1 day';
```

Dette lagres til fil, og kan krypteres vha av kommandoen; `zip -er sergavvik.zip sergreg_public_avvik.csv`
Filen kan så sendes til app-drift, og pakkes ut til `matrikkel/server/mat-patcheprogram/TH2587data.csv` for kjøring av patchen, som gjøres på f.eks bygge-maskinene.

Så kan patches kjøres vha `./gradlew runPatchTH2587 --args="skrivEndringer=true"`,
for testing/dry-run bruk `./gradlew runPatchTH2587 --args="skrivEndringer=false"`.

Når patchen er kjørt kan man refreshe avvikstellingen ved å kjøre `select refresh_avvik();` (**NB!!** Det kreves administrator tilgang for dette.), 
alternativt venter man 1 time og det vil automatisk bli refreshed. 

**Ved feil:** kan man ikke kjøre filen på nytt igjen uten videre siden data fra patchen blir lagret i bolker.
Man må da rekalkulere avvikene på nytt og lage ny fil for neste kjøring.