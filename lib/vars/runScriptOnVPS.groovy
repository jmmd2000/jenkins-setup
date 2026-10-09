/**
 * Runs one of the library's scripts on the VPS, in the environment's deploy folder. The script never needs to be copied to the VPS.
 *
 * @param environment the environment's settings from deploy.yaml
 * @param name the script's file name in resources/deploy
 * @param values the variables the script needs. Each value must be one that loadDeployConfig checks, such as a name or a path.
 * @param returnOutput true to return what the script prints instead of logging it
 * @return what the script printed, when returnOutput is true. Otherwise an empty string.
 */
String call(Map environment, String name, Map values, boolean returnOutput = false) {
  def assignments = []
  for (key in values.keySet()) {
    assignments.add("${key}=${values[key]}")
  }

  sshagent(["vps-ssh"]) {
    withEnv([
      "VPS=${env.VPS_USER}@${env.VPS_HOST}",
      "DEPLOY_DIR=${environment.dir}",
      "SCRIPT=${deployScript(name)}",
      "SCRIPT_VALUES=${assignments.join(" ")}",
    ]) {
      // bash -s reads the script from standard input, which is the local copy.
      def command = 'ssh -o BatchMode=yes "$VPS" "cd $DEPLOY_DIR && $SCRIPT_VALUES bash -s" < "$SCRIPT"'
      if (returnOutput) return sh(script: command, returnStdout: true).trim()

      sh(command)
      return ""
    }
  }
}