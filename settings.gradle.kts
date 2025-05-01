rootProject.name = "kairon-bot"

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	@Suppress("UnstableApiUsage")
	repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
	@Suppress("UnstableApiUsage")
	repositories {
		mavenLocal()
		mavenCentral()
	}

	versionCatalogs {
		create("connectorLibs") {
			from(files("./kabot-db-connector/libs.versions.toml"))
		}
	}

}

include(":kabot-db-connector")