/**
 * Runs the Verify stage. Pings the app for a healthy response through its public domain
 *
 * @param config the settings from deploy.yaml
 * @param deployment what this build deploys, from checkDeploy
 */
void call(Map config, Map deployment) {
  def healthURL = "https://${deployment.environment.domain}${config.healthcheck ?: "/"}"

  withEnv(["SCRIPT=${deployScript("check-health.sh")}", "HEALTH_URL=${healthURL}"]) {
    sh 'bash "$SCRIPT"'
  }
}