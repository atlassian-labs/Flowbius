Releasing
=========

1. Change the version in `gradle.properties` to a non-SNAPSHOT verson.
2. Update the `CHANGELOG.md` for the impending release.
3. Update the `README.md` with the new version.
4. `git commit -am "Prepare for release X.Y.Z."` (where X.Y.Z is the new version)
5. `./gradlew clean publishAllPublicationsToMavenCentralRepository`.
6. Visit [Sonatype Nexus](https://oss.sonatype.org/) and promote the artifact.
7. `git tag -a X.Y.Z -m "Version X.Y.Z"` (where X.Y.Z is the new version)
8. Update the `gradle.properties` to the next SNAPSHOT version.
9. `git commit -am "Prepare next development version."`
10. `git push && git push --tags`
11. Open a PR.


Prerequisites
-------------

In `~/.gradle/gradle.properties`, set the following:

* `mavenCentralUsername` - Sonatype username for releasing to `com.trello`.
* `mavenCentralPassword` - Sonatype password for releasing to `com.trello`.