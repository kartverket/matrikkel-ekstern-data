# SERG Sync

Hendelsestyper som kan komme inn;

- ny
- endret
- slettet

Disse styrer overgangen av statuser vi bruker i databasen våres;

- REQUIRE_SYNCHRONIZATION
- SYNCRHONIZED
- FAILURE
- DELETED

| Hendelse/Nåværende status | Require Sync                                                                | Synced                                          | Failure                                         |
|---------------------------|-----------------------------------------------------------------------------|-------------------------------------------------|-------------------------------------------------|
| Ny                        | Burde aldri skje, men vi oppdaterer da hendelseId og status til RequireSync | Oppdaterer hendelseId og setter til RequireSync | Oppdaterer hendelseId og setter til RequireSync |
| Endret                    | Oppdaterer hendelseId og setter til RequireSync                             | Oppdaterer hendelseId og setter til RequireSync | Oppdaterer hendelseId og setter til RequireSync |
| Slettet                   | Setter status til DELETED                                                   | Setter status til DELETED                       | Setter status til DELETED                       |

