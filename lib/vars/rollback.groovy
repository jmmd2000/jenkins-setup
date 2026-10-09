/**
 * Runs after a failed deploy. Puts the last good deploy back when that is
 * safe: the deploy changed the VPS, and no migration ran. Every outcome ends
 * with a line that starts with ROLLED BACK or NOT rolled back.
 *
 * @param config the settings from deploy.yaml
 * @param deployment what this build deploys, from checkDeploy, with the flags
 *        the stages set
 */
void call(Map config, Map deployment) {
  if (!deployment.vpsTouched) {
    echo "NOT rolled back: the deploy failed before it changed the VPS."
    return
  }

  if (deployment.migrationsBefore != null) {
    def migrationsAfter = countMigrations(config, deployment)
    if (migrationsAfter != deployment.migrationsBefore) {
      echo "NOT rolled back: this deploy applied ${migrationsAfter - deployment.migrationsBefore} migration(s), so the old version may not work with the database. If the environment has backups, the Backup stage made one before this deploy."
      return
    }
  }

  runScriptOnVPS(deployment.environment, "restore-snapshot.sh", [:])

  if (deployment.environment.domain == null) {
    echo "ROLLED BACK to the last good deploy."
    return
  }

  try {
    verifyApp(config, deployment)
    echo "ROLLED BACK to the last good deploy. It passes Verify, so the new version caused the failure."
  } catch (error) {
    echo "ROLLED BACK to the last good deploy, but it fails Verify too, so the problem is outside the code. It is left running."
  }
}