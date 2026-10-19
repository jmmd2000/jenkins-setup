/**
 * Runs the Build stage. Builds every image in deploy.yaml at the same time.
 *
 * @param config the settings from deploy.yaml
 * @param deployment what this build deploys, from checkDeploy
 */
void call(Map config, Map deployment) {
  def buildValues = [
    tag: deployment.tag,
    commitMessageBase64: sh(script: "git log -1 --pretty=%s | base64 -w0", returnStdout: true).trim(),
    builtAt: sh(script: "date -u +%Y-%m-%dT%H:%M:%SZ", returnStdout: true).trim(),
    domain: deployment.environment.domain ?: "",
  ]

  def builds = [failFast: true]
  for (image in config.images) {
    def currentImage = image
    builds[image.name] = {
      buildImage(currentImage, deployment.tag, buildValues)
    }
  }
  parallel(builds)
}

void buildImage(Map image, String tag, Map buildValues) {
  def buildArgs = image.buildArgs ?: [:]
  def environment = [
    "DOCKERFILE=${image.dockerfile}",
    "IMAGE=${imageReference(image.name, tag)}",
  ]
  def buildArgFlags = []

  for (name in buildArgs.keySet()) {
    def value = buildArgs[name]
    for (placeholder in buildValues.keySet()) {
      value = value.replace("{${placeholder}}", buildValues[placeholder])
    }
    environment.add("${name}=${value}")
    buildArgFlags.add("--build-arg ${name}")
  }
  environment.add("BUILD_ARG_FLAGS=${buildArgFlags.join(" ")}")

  def buildSecrets = image.buildSecrets ?: [:]
  def secretBindings = []
  def secretFlags = []

  for (name in buildSecrets.keySet()) {
    def variable = "BUILD_SECRET_${name.toUpperCase()}"
    secretBindings.add(string(credentialsId: buildSecrets[name], variable: variable))
    secretFlags.add("--secret id=${name},env=${variable}")
  }
  environment.add("BUILD_SECRET_FLAGS=${secretFlags.join(" ")}")

  withCredentials(secretBindings) {
    withEnv(environment) {
      // A --build-arg with only a name takes its value from the environment.
      // A --secret with env= also takes its value from the environment, and keeps it out of the image and the log.
      // The flag variables stay unquoted so each flag becomes its own word.
      sh 'docker build --file "$DOCKERFILE" --tag "$IMAGE" $BUILD_ARG_FLAGS $BUILD_SECRET_FLAGS .'
    }
  }
}