# VØRZEX VPN — Privacy Policy

Last updated: 12 October 2026

## Information sent to the VØRZEX service

At startup, the app makes a lightweight HTTPS request to the configured VØRZEX Worker health endpoint. Like any network request, the Worker and network infrastructure may receive the request's public source IP address and standard connection metadata.

The current Android client does **not** automatically submit the local 12-digit device code, Android ID, device model, app version, consent timestamp, VPN profile contents, subscription URLs, private keys, browsing history, or traffic payloads to the VØRZEX API. Installation registration, telemetry, and access-policy calls are not enabled in the startup flow.

## Device screen and local storage

The Device screen displays the device model, Android version, app version, a locally stored 12-digit pseudonymous device code, and local IPv4 addresses where available. These displayed values are not uploaded by that screen. The app does not request a third-party service to discover the public IP address.

The pseudonymous device code is derived from Android's app-scoped device identifier and stored locally. It can change after a factory reset or a signing-identity change.

## VPN profiles and traffic

VPN profiles, subscription URLs, credentials, private keys, browsing history, and traffic contents are not sent through the API health request. Public configuration sources opened from the CNR screen are hosted by the referenced public GitHub repository; importing a source makes a request to that host.

## Contact

For questions or privacy requests, use the VØRZEX Telegram channel: https://t.me/VORZEX_TU
