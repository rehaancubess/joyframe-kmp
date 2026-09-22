# Contributing

This is an experimental extraction. Small reproducible examples, hardware test
reports and documentation improvements are especially useful. Include OS/device,
GPU, Kotlin version and the smallest scene that reproduces an issue.

Run the desktop tests and platform compile checks from README before a pull
request. Keep game rules out of the toolkit. Never add credentials, private app
assets or user/device identifiers to fixtures or logs.

Changes to water displacement should keep the Kotlin CPU query, GLSL and Metal
implementations consistent. Changes to scene values should compile on every
target. Contributions are made under the repository's Apache-2.0 license.
