/**
 * Counts the drizzle migrations that the environment's database has applied.
 *
 * @param config the settings from deploy.yaml
 * @param deployment what this build deploys, from checkDeploy
 * @return the count, or null when deploy.yaml names no database
 */
Integer call(Map config, Map deployment) {
  if (config.database == null) return null

  def output = runScriptOnVPS(deployment.environment, "count-migrations.sh", [DATABASE_SERVICE: config.database], true)
  return output.toInteger()
}