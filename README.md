# matrikkel-ekstern-data

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

This project follows the suggested multi-module setup and consists of the `app` and `utils` subprojects.
The shared build logic was extracted to a convention plugin located in `buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies
and both a build cache and a configuration cache (see `gradle.properties`).

## Local compose runtime

This repository includes a Docker Compose setup for local integration testing with:

- `postgres` on `localhost:5432`
- `serg-mock` on `localhost:8094`
- `serg-sync` on `localhost:8090`

### Start

```bash
docker compose up --build
```

### Seed mock data

After services are up, generate sample events in `serg-mock`:

```bash
curl -X POST http://localhost:8094/admin/api/generate \
  -H 'Content-Type: application/json' \
  -d '{"count":20,"preset":"BALANCED"}'
```

### Verify services

Check that `serg-mock` serves generated events:

```bash
curl http://localhost:8094/v1/hendelser
```

Check that `serg-sync` is ready:

```bash
curl http://localhost:8090/internal/isReady
```

Check synchronized data in Postgres:

```bash
docker compose exec postgres psql -U postgres -d postgres -c "select matrikkelenhetid, status, sistoppdatert from serg_document order by sistoppdatert desc limit 20;"
```

### Stop and clean up

Stop services:

```bash
docker compose down
```

`postgres` is configured without a volume, so database state is ephemeral and will be reset when the container is recreated. `docker compose down -v` is safe but not required for DB cleanup in this setup.
