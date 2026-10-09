/**
 * The deploy pipeline. Called from whatever app and follows that app's deploy.yaml
 */
void call() {
  def config = loadDeployConfig()
  def deployment = [:]

  pipeline {
    agent any

    options {
      disableConcurrentBuilds()
      timeout(time: 30, unit: "MINUTES")
      buildDiscarder(logRotator(numToKeepStr: "50"))
    }

    parameters {
      choice(name: "ENVIRONMENT", choices: config.environments.keySet() as List, description: "Where to deploy")
      booleanParam(name: "BUILD_IMAGES", defaultValue: true, description: "Build and push new images?")
      booleanParam(name: "CONFIRM_TESTED", defaultValue: false, description: "I have run the tests locally")
      booleanParam(name: "TAKE_BACKUP", defaultValue: true, description: "Back up the db first")
      string(name: "TAG", defaultValue: "", description: "Image tag to deploy. Empty means the current commit.")
    }

    stages {
      stage("Check") {
        steps {
          script {
            deployment.putAll(checkDeploy(config))
          }
        }
      }
    }

    post {
      success {
        echo "Deployed ${deployment.tag} to ${deployment.environmentName}."
      }
      failure {
        echo "The deploy failed, check the logs."
      }
    }
  }
}
