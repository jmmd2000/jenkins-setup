/**
 * Runs a shell command on the VPS, in the environment's deploy folder.
 *
 * The command is sent as text, so it must only contain values that loadDeployConfig checks, such as service names.
 *
 * @param environment the environment's settings from deploy.yaml
 * @param command the command to run
 */
void call(Map environment, String command) {
  sshagent(["vps-ssh"]) {
    withEnv(["VPS=${env.VPS_USER}@${env.VPS_HOST}", "DEPLOY_DIR=${environment.dir}", "REMOTE_COMMAND=${command}"]) {
      sh 'ssh -o BatchMode=yes "$VPS" "cd $DEPLOY_DIR && $REMOTE_COMMAND"'
    }
  }
}