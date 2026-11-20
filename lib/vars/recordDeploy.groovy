import groovy.json.JsonOutput
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

/**
 * Tells Lookout that a deploy started or ended. It only sends when LOOKOUT_URL
 * is set and the lookout-deploy-token credential exists. Whatever goes wrong
 * here only logs a line: it never fails the deploy. An abort still stops it.
 *
 * @param config the settings from deploy.yaml
 * @param deployment what this build deploys, from checkDeploy, with the flags
 *        the stages set. When the Check stage failed, it only holds the stage
 * @param event "started" or "finished"
 */
void call(Map config, Map deployment, String event) {
  if (!env.LOOKOUT_URL) return
  // The record goes with the token, so it is only ever sent encrypted
  if (!env.LOOKOUT_URL.startsWith("https://")) {
    echo "No deploy record sent: LOOKOUT_URL must start with https://."
    return
  }

  try {
    send(config, deployment, event)
  } catch (FlowInterruptedException abort) {
    throw abort
  } catch (CredentialNotFoundException missing) {
    echo "No deploy record sent: add the Secret text credential lookout-deploy-token, or empty LOOKOUT_URL."
  } catch (error) {
    echo "Lookout did not get the ${event} deploy record. The deploy itself is not affected. ${error.message}"
  }
}

void send(Map config, Map deployment, String event) {
  // When the Check stage fails, checkDeploy returns nothing, so the record falls back to the build parameter
  def environmentName = deployment.environmentName ?: params.ENVIRONMENT
  def environment = deployment.environment ?: config.environments[environmentName]
  if (environment == null) {
    echo "No deploy record sent: the build parameters do not name an environment in deploy.yaml."
    return
  }

  if (event == "finished") countAppliedMigrations(config, deployment)
  def record = deployRecord(deployment, environmentName, environment, event)

  withCredentials([string(credentialsId: "lookout-deploy-token", variable: "LOOKOUT_DEPLOY_TOKEN")]) {
    def endpoint = env.LOOKOUT_URL.replaceAll('/+$', '') + "/api/deploys"
    withEnv(["RECORD_JSON=${JsonOutput.toJson(record)}", "LOOKOUT_ENDPOINT=${endpoint}"]) {
      // Single quotes so the shell reads the token and Groovy never puts it in the script
      sh '''
        curl --silent --show-error --fail-with-body --max-time 10 \
          --request POST "$LOOKOUT_ENDPOINT" \
          --header "Authorization: Bearer $LOOKOUT_DEPLOY_TOKEN" \
          --header "Content-Type: application/json" \
          --data "$RECORD_JSON"
      '''
    }
  }
}

/**
 * The record as Lookout's /api/deploys reads it. Fields with no value are left
 * out, because Lookout refuses null.
 */
Map deployRecord(Map deployment, String environmentName, Map environment, String event) {
  def record = [
    event: event,
    job: env.JOB_NAME,
    buildNumber: env.BUILD_NUMBER as Integer,
    startedAt: isoTime(currentBuild.startTimeInMillis),
    buildURL: env.BUILD_URL,
    environment: environmentName,
    deployDir: environment.dir,
    tag: deployment.tag,
  ]

  // The commit message is only known when the build deploys the commit it checked out
  if (deployment.tag && !params.TAG) {
    def subject = sh(script: "git log -1 --pretty=%s", returnStdout: true).trim()
    // Lookout refuses a commit message over 500 characters, and with it the whole record
    record.commitMessage = subject.length() > 500 ? subject.substring(0, 500) : subject
  }

  if (event == "finished") {
    record.finishedAt = isoTime(System.currentTimeMillis())
    record.result = deployResult()
    if (record.result == "failure" && deployment.stage) record.failedStage = deployment.stage
    if (deployment.rolledBack != null) record.rolledBack = deployment.rolledBack
    if (deployment.migrationsApplied != null) record.migrationsApplied = deployment.migrationsApplied
  }

  return withoutEmptyValues(record)
}

@NonCPS
Map withoutEmptyValues(Map record) {
  def present = [:]
  for (entry in record) {
    if (entry.value != null && entry.value != "") present[entry.key] = entry.value
  }
  return present
}

/**
 * Saves how many migrations this deploy applied in deployment.migrationsApplied.
 * It stays unset when no migration count was taken, or the count fails.
 */
void countAppliedMigrations(Map config, Map deployment) {
  if (deployment.migrationsBefore == null) return
  try {
    deployment.migrationsApplied = countMigrations(config, deployment) - deployment.migrationsBefore
  } catch (FlowInterruptedException abort) {
    throw abort
  } catch (error) {
    echo "Could not count this deploy's migrations for Lookout. ${error.message}"
  }
}

String isoTime(long milliseconds) {
  return java.time.Instant.ofEpochMilli(milliseconds).toString()
}

/** Lookout knows success, failure and aborted. Unstable counts as a failure */
String deployResult() {
  def result = currentBuild.currentResult
  if (result == "SUCCESS") return "success"
  if (result == "ABORTED") return "aborted"
  return "failure"
}
