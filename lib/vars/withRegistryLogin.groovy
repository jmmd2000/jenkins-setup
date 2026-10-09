/**
 * Logs in to the image registry, runs the body, then logs out.
 * The login is kept in a folder that only this build uses, so it is never saved in Jenkins home.
 *
 * @param body the steps that need the login
 */
void call(Closure body) {
  withCredentials([usernamePassword(credentialsId: "github-ghcr", usernameVariable: "REGISTRY_USER", passwordVariable: "REGISTRY_TOKEN")]) {
    withEnv(["DOCKER_CONFIG=${env.WORKSPACE_TMP}/docker-config"]) {
      sh 'echo "$REGISTRY_TOKEN" | docker login ghcr.io --username "$REGISTRY_USER" --password-stdin'
      try {
        body()
      } finally {
        sh 'docker logout ghcr.io; rm -rf "$DOCKER_CONFIG"'
      }
    }
  }
}