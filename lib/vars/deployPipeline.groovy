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

      stage("Build") {
        when { expression { params.BUILD_IMAGES } }
        steps {
          buildImages(config, deployment)
        }
      }

      stage("Push") {
        when { expression { params.BUILD_IMAGES } }
        steps {
          pushImages(config, deployment)
        }
      }

      stage("Prepare VPS") {
        steps {
          prepareVPS(config, deployment)
        }
      }

      stage("Backup") {
        when { expression { deployment.environment.backup == true && params.TAKE_BACKUP } }
        steps {
          runOnVPS(deployment.environment, "./backup.sh")
        }
      }

      stage("Database") {
        when { expression { config.database != null } }
        steps {
          runOnVPS(deployment.environment, "docker compose up --detach --wait ${config.database}")
        }
      }

      stage("Restore") {
        when { expression { deployment.environment.restoreFromBackups != null } }
        steps {
          runScriptOnVPS(deployment.environment, "restore-database.sh", [
            DEPLOY_ENV: deployment.environmentName,
            DATABASE_SERVICE: config.database,
            BACKUP_DIR: deployment.environment.restoreFromBackups,
          ])
        }
      }

      stage("Migrate") {
        when { expression { config.migrate != null } }
        steps {
          runOnVPS(deployment.environment, "docker compose run --rm ${config.migrate}")
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
