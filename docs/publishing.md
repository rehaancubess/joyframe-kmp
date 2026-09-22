# Publishing Joyframe

There are two separate milestones: public source on GitHub and installable
artifacts on Maven Central. A GitHub release does not make Maven coordinates
available from `mavenCentral()`.

## Local rehearsal (no upload)

```sh
./gradlew :joyframe:desktopTest :sample:compileKotlinDesktop
./gradlew :joyframe:publishAllPublicationsToStagingRepository
```

The staging Maven repository is `build/staging/`, ignored by Git. This is useful
for inspecting POMs, source archives, target artifacts and dependency metadata.
It is unsigned unless release signing has been enabled. Run on macOS to include
all iOS variants. Never upload a repository archive containing build directories.

## Before the first public release

- Review the public source tree and source JARs. No private game source/history,
  assets, local paths, accounts, store configuration or credentials may be added.
- Run the platform checks and hardware smoke tests listed in `platforms.md`.
- Verify artifact coordinates/version and README claims match what is tested.
- Keep the release marked experimental until the documented limitations are fixed.

## Maven Central account setup (owner action)

1. Sign in to the [Central Portal](https://central.sonatype.com/) using the
   `rehaancubess` GitHub account and verify namespace `io.github.rehaancubess`.
2. Generate a Central publishing user token. This is not your GitHub token.
3. Create or select a GPG signing key, retain a secure backup and distribute its
   public key as required by Central. Never commit a private key or token.
4. Supply these environment variables through your secret manager or CI secrets:

   - `ORG_GRADLE_PROJECT_mavenCentralUsername`
   - `ORG_GRADLE_PROJECT_mavenCentralPassword`
   - `ORG_GRADLE_PROJECT_signingInMemoryKey`
   - `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` (if protected)

Do not paste credentials into an issue, chat, command-line argument or public log.
The library uses the Vanniktech publishing plugin recommended by Kotlin's guide.

## Upload for validation, then publish deliberately

Only after credentials, signing and source review are complete:

```sh
./gradlew :joyframe:publishToMavenCentral -Pjoyframe.release=true
```

This contacts Sonatype and uploads the release. Review the validated deployment
in the Central Portal, then explicitly publish it. Versions on Central cannot be
overwritten. Do not use `publishAndReleaseToMavenCentral` during rehearsal.

After Central confirms publication, verify the dependency from a clean consumer
without `mavenLocal()`, then update README's availability statement and create a
matching GitHub prerelease. No automated tag-triggered publishing is enabled.

References:
- [Kotlin publishing guide](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html)
- [Central namespace verification](https://central.sonatype.org/register/namespace/)
- [Publishing plugin documentation](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
