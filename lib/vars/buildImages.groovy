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

  withEnv(environment) {
    // A --build-arg with only a name takes its value from the environment.
    // BUILD_ARG_FLAGS stays unquoted so each flag becomes its own word.
    sh 'docker build --file "$DOCKERFILE" --tag "$IMAGE" $BUILD_ARG_FLAGS .'
  }
}