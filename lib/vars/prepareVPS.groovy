/**
 * Runs the Prepare VPS stage. Creates the deploy folder, copies the files that deploy.yaml lists, writes the .env file and pulls the images.
 *
 * @param config the settings from deploy.yaml
 * @param deployment what this build deploys, from checkDeploy
 */
void call(Map config, Map deployment) {
  def environment = deployment.environment

  sshagent(["vps-ssh"]) {
    withEnv(["VPS=${env.VPS_USER}@${env.VPS_HOST}", "DEPLOY_DIR=${environment.dir}"]) {
      sh 'ssh -o BatchMode=yes "$VPS" mkdir -p "$DEPLOY_DIR"'

      for (file in config.files) {
        withEnv(["FILE=${file}"]) {
          sh 'scp -o BatchMode=yes "$FILE" "$VPS:$DEPLOY_DIR/"'
        }
      }

      writeEnvFile(environment.envCredential, [
        "IMAGE_TAG=${deployment.tag}",
        "GITHUB_USER=${env.GHCR_USER}",
        "APP_NAME=${deployment.appName}",
        "DOMAIN=${environment.domain ?: ""}",
      ])

      sh 'ssh -o BatchMode=yes "$VPS" "cd $DEPLOY_DIR && docker compose pull --quiet"'
    }
  }
}

void writeEnvFile(String credentialID, List deployValues) {
  def builtFile = "${env.WORKSPACE_TMP}/deploy.env"

  withCredentials([file(credentialsId: credentialID, variable: "ENV_FILE")]) {
    withEnv(deployValues + ["SCRIPT=${deployScript("build-env-file.sh")}", "BUILT_FILE=${builtFile}"]) {
      try {
        // The file is built first and sent second. A failed build then stops here, before the .env on the VPS is touched.
        sh 'umask 077 && bash "$SCRIPT" > "$BUILT_FILE"'
        sh 'ssh -o BatchMode=yes "$VPS" "umask 077 && cat > $DEPLOY_DIR/.env.new && mv $DEPLOY_DIR/.env.new $DEPLOY_DIR/.env" < "$BUILT_FILE"'
      } finally {
        sh 'rm -f "$BUILT_FILE"'
      }
    }
  }
}