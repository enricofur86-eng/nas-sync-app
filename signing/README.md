`nassync-release.jks.asc` is the release signing key, **encrypted** (GPG, AES-256).
It can only be decrypted with the passphrase stored as the `NASSYNC_PASSPHRASE`
GitHub Actions secret. It is not used by F-Droid builds.
