pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "SlateLauncher"
include(":app")
include(":music")
include(":pwnapple")
project(":pwnapple").projectDir = file("third_party/pwnapple")
