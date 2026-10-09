/**
 * Copies one of the library's scripts into the build's temp folder, so a shell step can run it.
 *
 * @param name the script's file name in resources/deploy
 * @return the path of the copy
 */
String call(String name) {
  def path = "${env.WORKSPACE_TMP}/deploy-scripts/${name}".toString()
  writeFile(file: path, text: libraryResource("deploy/${name}"))
  return path
}