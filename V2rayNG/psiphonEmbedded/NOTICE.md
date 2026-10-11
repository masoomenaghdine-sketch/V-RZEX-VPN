# VXV embedded client notices

VXV embeds the Android client source from [shirokhorshid/shirokhorshid-android](https://github.com/shirokhorshid/shirokhorshid-android), pinned as a Git submodule at commit `a3b91cf5104c256592d276202d3a4e49f96c6ee5`.

The upstream client is licensed under GNU GPL v3.0. Its original copyright and license headers are retained in source files. Any redistribution of a combined VØRZEX APK must comply with GPL-3.0 obligations, including providing corresponding source and license notices.

The embedded client requires `PSIPHON_PROPAGATION_CHANNEL_ID` and `PSIPHON_SPONSOR_ID` to be supplied by the build environment. Do not invent or hard-code guessed values. See the upstream project README and build.gradle for configuration requirements.
