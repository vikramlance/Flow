# .local — Machine-Local Configuration

> **This folder is gitignored and MUST NOT be committed.**
> It exists to store machine-specific environment settings that
> would otherwise be hardcoded into tracked files, violating
> Constitution Principle VI (Security & Privacy).

## Purpose

Store local environment variables (SDK paths, tool locations,
signing credentials) here so that every tracked file can use
portable expressions like `$env:ANDROID_HOME` instead of
`C:\Users\<your-name>\AppData\Local\Android\Sdk`.

## Setup

1. Copy `env.ps1.template` to `env.ps1` in this folder:
   ```powershell
   Copy-Item .local\env.ps1.template .local\env.ps1
   ```
2. Edit `.local\env.ps1` and fill in the values for your machine.
3. Source it at the start of any PowerShell session or script:
   ```powershell
   . .\.local\env.ps1
   ```

Once sourced, all scripts in `.specify/scripts/` and all
`quickstart.md` commands will resolve `$env:ANDROID_HOME`
automatically.

## Files

| File | Purpose |
|---|---|
| `env.ps1.template` | Template — copy to `env.ps1` and edit |
| `env.ps1` | Your local overrides — **gitignored, never commit** |

## Why Not `local.properties`?

`local.properties` is read by Gradle only. PowerShell scripts
and markdown examples cannot read it at runtime.
`.local/env.ps1` is the PowerShell equivalent: a gitignored
shell script that sets environment variables before running
any tool commands.
