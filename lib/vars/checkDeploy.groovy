/**
 * Runs the Check stage. Checks the build parameters, the shared settings and the files deploy.yaml mentions, then figures out what this build deploys.
 *
 * @param config the settings from deploy.yaml
 * @return the deployment: environment name and settings, image tag, and the
 *         compose project name
 */
Map call(Map config) {
  def knownParameters = ["ENVIRONMENT", "BUILD_IMAGES", "CONFIRM_TESTED", "TAKE_BACKUP", "TAG"]
  def hasOldParameters = false
  for (name in params.keySet()) {
    if (!knownParameters.contains(name)) {
      hasOldParameters = true
    }
  }

  if (hasOldParameters || !config.environments.containsKey(params.ENVIRONMENT)) error("The build parameters have changed. Run the build again to use the new ones.")

  if (params.BUILD_IMAGES && !params.CONFIRM_TESTED) error("Run the tests locally, then tick CONFIRM_TESTED.")

  for (name in ["VPS_HOST", "VPS_USER", "GHCR_USER"]) {
    if (!env.getProperty(name)) {
      error("${name} is not set. Add it to .env in jenkins-setup and restart Jenkins.")
    }
  }

  def missingFiles = []
  for (file in config.files) {
    if (!fileExists(file)) missingFiles.add(file)
  }

  for (image in config.images) {
    if (!fileExists(image.dockerfile)) missingFiles.add(image.dockerfile)
  }

  if (missingFiles) error("deploy.yaml mentions files that are not in the repo: ${missingFiles.join(", ")}")

  def tag = params.TAG ?: sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
  if (!params.BUILD_IMAGES) checkImagesExist(config, tag)

  def deployment = [
    environmentName: params.ENVIRONMENT,
    environment: config.environments[params.ENVIRONMENT],
    tag: tag,
    appName: "${config.app}-${params.ENVIRONMENT}".toString(),
  ]

  currentBuild.description = "${deployment.environmentName} ${tag}"
  echo "Deploying ${config.app} ${tag} to ${deployment.environmentName}."
  return deployment
}

void checkImagesExist(Map config, String tag) {
  def missingImages = []
  withRegistryLogin {
    for (image in config.images) {
      def reference = imageReference(image.name, tag)
      def status = withEnv(["IMAGE=${reference}"]) {
        sh(script: 'docker buildx imagetools inspect "$IMAGE" > /dev/null 2>&1', returnStatus: true)
      }
      if (status != 0) missingImages.add(reference)
    }
  }
  if (missingImages) error("These images are not in the registry: ${missingImages.join(", ")}")
}
