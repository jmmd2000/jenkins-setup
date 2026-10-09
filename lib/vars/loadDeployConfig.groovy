/**
 * Reads deploy.yaml from the app repo and checks its format. Fails the build with every problem it finds, so one run shows all the mistakes.
 *
 * Reads the file straight from git, before the build has a workspace, so the pipeline can build its params from it.
 *
 * @return the settings from deploy.yaml
 */
Map call() {
  def config = readYaml(text: readTrusted("deploy.yaml"))
  def problems = findProblems(config)
  if (problems) error("deploy.yaml is not valid:\n- " + problems.join("\n- "))
  return config
}

List findProblems(config) {
  if (!(config instanceof Map)) return ["The file must contain settings, such as app: and environments:."]

  def problems = []
  problems.addAll(findUnknownKeys(config, ["app", "images", "files", "database", "migrate", "healthcheck", "environments"], "deploy.yaml"))

  if (!isName(config.app)) problems.add("app must be lowercase letters, digits and hyphens.")

  if (!(config.images instanceof List) || config.images.isEmpty()) {
    problems.add("images must list at least one image.")
  } else {
    for (image in config.images) {
      problems.addAll(findImageProblems(image, config))
    }
  }

  if (!isListOfText(config.files)) problems.add("files must list the files to copy to the VPS.")

  for (key in ["database", "migrate"]) {
    if (config.containsKey(key) && !isText(config[key])) {
      problems.add("${key} must be the name of a service in the compose file.")
    }
  }

  if (config.containsKey("healthcheck") && !isPath(config.healthcheck)) problems.add("healthcheck must be a path that starts with /.")

  if (!(config.environments instanceof Map) || config.environments.isEmpty()) {
    problems.add("environments must list at least one environment.")
  } else {
    for (name in config.environments.keySet()) {
      problems.addAll(findEnvironmentProblems(name, config.environments[name], config))
    }
  }

  return problems
}

List findImageProblems(image, Map config) {
  if (!(image instanceof Map)) {
    return ["Each image must have a name and a dockerfile."]
  }

  def problems = findUnknownKeys(image, ["name", "dockerfile", "buildArgs"], "an image")
  if (!isName(image.name)) problems.add("Each image name must be lowercase letters, digits and hyphens.")

  if (!isText(image.dockerfile)) problems.add("Image ${image.name} must have a dockerfile.")

  if (image.containsKey("buildArgs")) problems.addAll(findBuildArgProblems(image, config))

  return problems
}

List findBuildArgProblems(Map image, Map config) {
  if (!(image.buildArgs instanceof Map)) return ["Image ${image.name} must list its buildArgs as NAME: value."]

  def knownPlaceholders = ["{tag}", "{commitMessageBase64}", "{builtAt}", "{domain}"]
  def problems = []
  for (name in image.buildArgs.keySet()) {
    def value = image.buildArgs[name]
    if (!(name ==~ /[A-Z][A-Z0-9_]*/)) problems.add("Build arg ${name} in image ${image.name} must be uppercase letters, digits and underscores.")

    if (!(value instanceof String)) {
      problems.add("Build arg ${name} in image ${image.name} must be text in quotes.")
      continue
    }

    for (placeholder in value.findAll(/\{[^}]*\}/)) {
      if (!knownPlaceholders.contains(placeholder)) problems.add("Build arg ${name} in image ${image.name} uses unknown value ${placeholder}.")
    }

    if (value.contains("{domain}") && !everyEnvironmentHasDomain(config)) problems.add("Build arg ${name} in image ${image.name} uses {domain}, so every environment needs a domain.")
  }
  return problems
}

boolean everyEnvironmentHasDomain(Map config) {
  if (!(config.environments instanceof Map)) return false
  for (environment in config.environments.values()) {
    if (!(environment instanceof Map) || !isText(environment.domain)) return false
  }
  return true
}

List findEnvironmentProblems(String name, environment, Map config) {
  if (!isName(name)) return ["Environment ${name} must be named with lowercase letters, digits and hyphens."]
  if (!(environment instanceof Map)) return ["Environment ${name} must have a dir and an envCredential."]

  def problems = findUnknownKeys(environment, ["dir", "domain", "envCredential", "backup", "restoreFromBackups"], "environment ${name}")

  if (!isPath(environment.dir)) problems.add("Environment ${name} must have a dir that starts with / and has no spaces or quotes.")

  if (!isText(environment.envCredential)) problems.add("Environment ${name} must have an envCredential.")

  if (environment.containsKey("domain") && !isText(environment.domain)) problems.add("Environment ${name} has an empty domain.")

  if (environment.containsKey("backup") && !(environment.backup instanceof Boolean)) problems.add("Environment ${name} must set backup to true or false.")

  if (environment.containsKey("restoreFromBackups")) {
    if (!isPath(environment.restoreFromBackups)) problems.add("Environment ${name} must set restoreFromBackups to a folder that starts with /.")

    if (!config.containsKey("database")) problems.add("Environment ${name} restores a database, so deploy.yaml must name the database service.")

    if (environment.backup == true || name == "production") problems.add("Environment ${name} is backed up, so it must not be restored over.")
  }

  return problems
}

List findUnknownKeys(Map settings, List knownKeys, String where) {
  def problems = []
  for (key in settings.keySet()) {
    if (!knownKeys.contains(key)) {
      problems.add("Unknown setting ${key} in ${where}.")
    }
  }
  return problems
}

boolean isText(value) {
  return value instanceof String && !value.isEmpty()
}

boolean isName(value) {
  return value instanceof String && value ==~ /[a-z0-9][a-z0-9-]*/
}

boolean isPath(value) {
  return value instanceof String && value ==~ /\/[A-Za-z0-9._\/-]*/
}

boolean isListOfText(value) {
  if (!(value instanceof List) || value.isEmpty()) return false
  for (item in value) {
    if (!isText(item)) {
      return false
    }
  }
  return true
}