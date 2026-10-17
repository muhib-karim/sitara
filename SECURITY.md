# Security policy

## Supported versions

Sitara is in early development. Security fixes are made on `main` and shipped in the next release.

| Version | Supported |
| --- | :---: |
| Latest release and `main` | Yes |
| Older releases | No |

## Reporting a vulnerability

**Please do not report security issues in public issues, discussions or pull requests.**

Use GitHub private vulnerability reporting:

1. Go to the [Security tab](https://github.com/muhib-karim/sitara/security) of the repository.
2. Choose **Report a vulnerability** (or open the [new advisory form](https://github.com/muhib-karim/sitara/security/advisories/new) directly).
3. Describe the issue, the affected version, steps to reproduce and the impact you expect. A proof of concept helps, but is not required.

What to expect:

- An acknowledgement within 7 days.
- An initial assessment and, where confirmed, a plan for a fix within 30 days.
- Credit in the release notes and advisory if you would like it.

Please give us a reasonable chance to release a fix before disclosing the issue publicly.

## Scope

In scope:

- The Sitara Android app and the code in this repository
- The build and release workflows, including release signing and checksums
- The project website published from `site/`

Out of scope:

- Vulnerabilities in third-party services or upstream libraries (please report those upstream; tell us if Sitara is affected so we can update)
- Issues that need a rooted or already compromised device
- Social engineering

## Verifying releases

Every GitHub Release includes a `SHA256SUMS` file. Verify a download with:

```sh
sha256sum -c SHA256SUMS
```

Only install Sitara from the [official releases page](https://github.com/muhib-karim/sitara/releases) or a source that pulls from it, such as Obtainium.

## Secrets

The repository never contains signing keys, tokens or passwords. Release signing material is held only in encrypted GitHub Actions secrets, and every push is scanned with gitleaks.
