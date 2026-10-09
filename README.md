# jenkins-setup

Local Jenkins setup.

- `image/`: the Jenkins image. Jenkins LTS, the Docker CLI and a pinned set of plugins.
- `config/jenkins.yaml`: Configuration as Code (CasC). Jenkins applies it on every start.
- `docker-compose.yml`: runs the image.
- `lib/`: the `deploy` shared library that app Jenkinsfiles load. See [lib/README.md](lib/README.md).

## Set up a clone

Turn on the pre-commit hook to lint the Dockerfile, YAML and shell scripts. It also runs the library's tests in `lib/test` when anything in `lib/` changes.

```bash
git config core.hooksPath .githooks
```

## Run Jenkins

Copy `.env.example` to `.env` and fill in the VPS and GitHub details. Compose refuses to start without them.

```bash
docker compose up -d
```

Jenkins is at <http://localhost:8081>.

Jenkins home is the host folder `/var/jenkins_home`, mounted at the same path in the container. Builds start containers through the host's Docker engine, and those containers can only mount workspaces by their host path.

## Structure

| Item                     | Location                                                   |
| ------------------------ | ---------------------------------------------------------- |
| `config/jenkins.yaml`    | Library, theme, Timestamper, AnsiColor, executors, URL     |
| Jenkins home             | Credentials, users, security settings, jobs, build history |
| `image/plugins-lock.txt` | Every plugin and its version                               |

Config stored in `config/jenkins.yaml` replaces the settings it lists on every start. A change made in the UI instead of the yaml to one of those settings is lost on the next restart.

## Back up Jenkins home

`/var/jenkins_home` holds the credentials and `secrets/master.key`, the key that decrypts them. Without that folder, every credential must be added again. Back it up.

## Update Jenkins

1. Back up `/var/jenkins_home`. A new version can change files in it that an older version can't read.
2. Change the `FROM` tag in `image/Dockerfile`.
3. Update the plugins as below.

## Update plugins

`image/plugins.txt` lists the plugins. `image/plugins-lock.txt` lists those plus every dependency, and the image installs from it. Both are pinned exactly.

1. Show the newest versions of the chosen plugins:

   ```bash
   docker run --rm --volume "$PWD/image:/image" --entrypoint jenkins-plugin-cli jenkins/jenkins:2.580.1-lts-jdk21 --plugin-file /image/plugins.txt --available-updates --output txt
   ```

   Use the `FROM` tag from the Dockerfile. Copy the output into `image/plugins.txt`.

2. Regenerate the lock file:

   ```bash
   ./image/lock-plugins.sh
   ```

3. Rebuild and restart. The script builds the image first, so a failed build leaves Jenkins running. It then replaces the plugins in Jenkins home with the image's:

   ```bash
   ./scripts/restart-jenkins.sh
   ```

To add or remove a plugin, add/remove it in `image/plugins.txt` with a version, then do steps 2 and 3.

## Update the Docker CLI

List the versions available to the image:

```bash
docker run --rm --user root --entrypoint bash jenkins-setup -c "apt-get update -qq && apt-cache madison docker-ce-cli docker-buildx-plugin"
```

Change the two versions in `image/Dockerfile`, then run `./scripts/restart-jenkins.sh`.

## Add an app

1. In the app repo, add a `Jenkinsfile` and a `deploy.yaml`. See [lib/README.md](lib/README.md).
2. In Jenkins, add each environment's `.env` as a **Secret file** credential, with the ID that `deploy.yaml` names.
3. Create a **New Item** of type **Pipeline**.
4. Under **Pipeline**, choose **Pipeline script from SCM** and **Git**. Set the repository URL to the repo's local path, for example `/home/<user>/projects/my-app/`, and set the branch.
5. Tick **Lightweight checkout**, then save.
6. Run the job once. It fails at Check with "Run the tests locally". The run only loads the parameters, so the build form shows them from then on.
