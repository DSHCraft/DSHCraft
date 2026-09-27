# Security policy

Please do not publish API keys, credential-manager dumps, updater private keys, or full diagnostic logs in an issue.

For a suspected vulnerability, contact the project maintainer privately with:

- the affected Java/HMCL component;
- the DSHCraft version and operating system;
- reproduction steps that do not include real credentials;
- the smallest relevant log excerpt with secrets removed.

DSHCraft stores desktop Provider credentials in the operating system credential store when supported. DSH receives them through an environment variable reference at launch. Pack export, backups and diagnostics must remain secret-free.
