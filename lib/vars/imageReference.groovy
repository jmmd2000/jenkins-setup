/**
 * Gives the full registry name of an app image.
 *
 * @param name the image name from deploy.yaml
 * @param tag the image tag
 * @return the name to build, push and pull, such as ghcr.io/user/app-api:abc1234
 */
String call(String name, String tag) {
  return "ghcr.io/${env.GHCR_USER}/${name}:${tag}".toString()
}