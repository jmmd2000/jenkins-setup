/**
 * Runs the Push stage. Pushes every image to the registry at the same time, then removes the local copies and old build cache.
 *
 * @param config the settings from deploy.yaml
 * @param deployment what this build deploys, from checkDeploy
 */
void call(Map config, Map deployment) {
  withRegistryLogin {
    def pushes = [failFast: true]
    for (image in config.images) {
      def reference = imageReference(image.name, deployment.tag)
      pushes[image.name] = {
        withEnv(["IMAGE=${reference}"]) {
          sh 'docker push "$IMAGE"'
        }
      }
    }
    parallel(pushes)
  }

  for (image in config.images) {
    withEnv(["IMAGE=${imageReference(image.name, deployment.tag)}"]) {
      sh 'docker image rm "$IMAGE"'
    }
  }
  sh 'docker builder prune --force --filter until=168h'
}