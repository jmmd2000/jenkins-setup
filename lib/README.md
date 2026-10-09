# deploy library

A Jenkins shared library that deploys an app to the VPS. Each app defines config in a `deploy.yaml`, and the library runs the same stages for every app.

## Use it in an app

Add two files to the app repo.

`Jenkinsfile`:

```groovy
// Deploy stages: Check, Build, Push, Prepare VPS, Backup, Database, Restore, Migrate, Start, Verify
@Library("deploy") _
deployPipeline()
```

`deploy.yaml`, for example:

```yaml
app: album-reviews
images:
  - name: album-api
    dockerfile: apps/api/Dockerfile
    buildArgs:
      GIT_SHA: "{tag}"
      GIT_MESSAGE_B64: "{commitMessageBase64}"
      BUILT_AT: "{builtAt}"
  - name: album-web
    dockerfile: apps/web/Dockerfile
files: [docker-compose.yml, backup.sh]
database: db
migrate: migrate
healthcheck: /api/health
environments:
  staging:
    dir: /home/james/album-review/staging
    domain: staging.jamesreviewsmusic.com
    envCredential: album-env-staging
    restoreFromBackups: /home/james/backups/album-reviews-production
  production:
    dir: /home/james/album-review/prod
    domain: jamesreviewsmusic.com
    envCredential: album-env-prod
    backup: true
```

## deploy.yaml

The Check stage reads the file and lists every mistake in it, including unknown keys.

| Key            | Needed | What it does                                                                                                          |
| -------------- | ------ | --------------------------------------------------------------------------------------------------------------------- |
| `app`          | yes    | The app name. With the environment it makes `APP_NAME`, such as `album-reviews-production`.                           |
| `images`       | yes    | The images to build. Each has a `name`, a `dockerfile` and optional `buildArgs`.                                      |
| `files`        | yes    | The files to copy to the deploy folder, such as the compose file and `backup.sh`. Each lands under its own file name. |
| `database`     | no     | The compose service of the database. Turns on the Database stage.                                                     |
| `migrate`      | no     | The compose service that runs migrations. Turns on the Migrate stage.                                                 |
| `healthcheck`  | no     | The path Verify asks for. The default is `/`.                                                                         |
| `environments` | yes    | One entry per place to deploy. The first one is the default on the build form.                                        |

Each environment:

| Key                  | Needed | What it does                                                                                                                                         |
| -------------------- | ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `dir`                | yes    | The deploy folder on the VPS. Letters, digits, `.`, `_`, `-` and `/` only.                                                                           |
| `envCredential`      | yes    | The ID of the Jenkins **Secret file** credential that holds the app's `.env`.                                                                        |
| `domain`             | no     | The public domain. Turns on the Verify stage.                                                                                                        |
| `backup`             | no     | `true` runs the app's `./backup.sh` before each deploy.                                                                                              |
| `restoreFromBackups` | no     | A folder of `.sql` dumps. Each deploy loads the newest one into this environment's database. Not allowed on a backed-up or `production` environment. |

### Build args

`buildArgs` maps each build arg to a value in quotes. A value can use these placeholders:

| Placeholder             | Value                                                            |
| ----------------------- | ---------------------------------------------------------------- |
| `{tag}`                 | The image tag                                                    |
| `{commitMessageBase64}` | The commit subject, base64 encoded                               |
| `{builtAt}`             | The build time in UTC, such as `2026-10-09T19:48:12Z`            |
| `{domain}`              | The environment's domain. Every environment then needs a domain. |

### The .env file

Prepare VPS writes `.env` in the deploy folder. It holds the credential file, then these values:

- `IMAGE_TAG`
- `GITHUB_USER`
- `APP_NAME`
- `DOMAIN`, when the environment has a domain

If the credential file sets one of these, the deploy's value replaces it.

## Build parameters

| Parameter        | What it does                                                                          |
| ---------------- | ------------------------------------------------------------------------------------- |
| `ENVIRONMENT`    | Where to deploy, from `deploy.yaml`.                                                  |
| `BUILD_IMAGES`   | Build and push new images. Untick it to deploy a tag that is already in the registry. |
| `CONFIRM_TESTED` | Needed when building, to confirm the tests ran locally.                               |
| `TAKE_BACKUP`    | Runs the backup on environments with `backup: true`.                                  |
| `TAG`            | The image tag. Empty means the short hash of the current commit.                      |

## Stages

| Stage       | Runs when                        | What it does                                                                                                    |
| ----------- | -------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Check       | always                           | Checks `deploy.yaml`, the parameters and the files. When not building, checks that the images exist at the tag. |
| Build       | `BUILD_IMAGES`                   | Builds every image at the same time.                                                                            |
| Push        | `BUILD_IMAGES`                   | Pushes the images, then removes them and week-old build cache from the Jenkins PC.                              |
| Prepare VPS | always                           | Creates the deploy folder, copies the files, writes `.env` and pulls the images.                                |
| Backup      | `backup: true` and `TAKE_BACKUP` | Runs `./backup.sh`. A failure stops the deploy.                                                                 |
| Database    | `database` is set                | Starts the database and waits until it is healthy.                                                              |
| Restore     | `restoreFromBackups` is set      | Loads the newest dump in one transaction. A failure changes nothing.                                            |
| Migrate     | `migrate` is set                 | Runs the migrate service once.                                                                                  |
| Start       | always                           | Starts the app and waits up to 5 minutes for it to be healthy. Removes week-old unused images on the VPS.       |
| Verify      | the environment has a `domain`   | Queries `https://<domain><healthcheck>` for a healthy response, up to 10 times, 6 seconds apart.                |

A deploy times out after 30 minutes. Jenkins keeps the last 100 builds of each job.

## Shared settings

- `VPS_HOST`, `VPS_USER` and `GHCR_USER` come from `.env` in this repo. See the main README.
- The library uses two Jenkins credentials: `github-ghcr` (username and token for the registry) and `vps-ssh` (the SSH key for the VPS).
- The VPS needs this repo's SSH key in `authorized_keys`, and a `docker login ghcr.io`, once per server.

## First runs

- **After the parameters change,** the first build stops with "The build parameters have changed". Jenkins only shows new parameters after a run reads them. Run the build again. This happens when a job first switches to the library, and when you rename an environment.
- **On a brand-new environment,** untick `TAKE_BACKUP` for the first deploy. Backup runs before the database exists.

## Change the library

Jenkins loads the library from the `master` branch, so a change only applies once it is committed.

To try a change first:

1. Commit it on a branch.
2. In one app, change the Jenkinsfile to `@Library("deploy@my-branch") _`.
3. Run a staging deploy.
4. Merge the branch, and change the Jenkinsfile back.

The pre-commit hook runs the tests in `test/` whenever anything in `lib/` changes.

## Files

- `vars/`: the steps. `deployPipeline.groovy` is the pipeline, and each stage has its own file.
- `resources/deploy/`: the bash scripts the stages run.
- `test/`: bats tests for those scripts.
