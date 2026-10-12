# VØRZEX VPN — Privacy Policy

Last updated: 12 October 2026

## Information sent to the VØRZEX service

At startup, the app makes a lightweight HTTPS request to the configured VØRZEX Worker health endpoint. Like any network request, the Worker and network infrastructure may receive the request's public source IP address and standard connection metadata.

The app does not automatically upload device details, VPN profiles, subscription URLs, private keys, browsing history, or traffic contents. The home screen may fetch the public configuration feed so administrators can publish configs into the app; this request includes ordinary network metadata such as the public source IP, but no device report payload.

## Device screen and local storage

The Device screen displays the device model, Android version, app version, a locally stored 12-digit pseudonymous device code, and local IPv4 addresses where available. These fields are uploaded only after the user presses the Device-screen report button and confirms the consent dialog. Registration also sends an opaque random installation key, the app version, and the consent time so that authenticated remote management can be associated with the opted-in installation. The API stores the report fields and the last report time. The app does not request a third-party service to discover the public IP address.

The 12-digit pseudonymous device code is derived from Android's app-scoped device identifier and stored locally. It can change after a factory reset or a signing-identity change. A separate high-entropy random API installation key is stored locally and used only after the explicit Device-screen opt-in.

## VPN profiles and traffic

VPN profiles, subscription URLs, credentials, private keys, browsing history, and traffic contents are not sent through the API health request. Public configuration sources opened from the CNR screen are hosted by the referenced public GitHub repository; importing a source makes a request to that host.

## Contact

For questions or privacy requests, use the VØRZEX Telegram channel: https://t.me/VORZEX_TU
