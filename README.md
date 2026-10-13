# Firefight Mod Manager (Android)

**English** | [中文](README.zh-CN.md)

Append a **pure-Java mod manager UI** to your own Firefight Android APK:
game logic is untouched, the package name and signature stay the same, and saves,
the mod directory and permissions are shared with the game.

* On launch a prompt asks "use the mod manager?": "Yes" opens the manager, "No" or the
  15-second timeout goes straight into the game.
* The manager has three tabs: **Mods** (enable / disable / delete / import),
  **Maps** (import, enable / disable, thumbnails) and **About** (help, manager info,
  game site, repository, author, licence).
* The interface paints itself in a light or dark set of colours and follows the system
  day/night setting.
* Both lists are one continuous scrolling list with no cap on the number of mods or maps: a
  search box at the top filters by name, code or number, a "Jump" button scrolls to a given line,
  and every row has its own order number (and, for maps, an in-game load number) with up/down
  buttons and a "set the number" dialog.
* Nothing asks for a restart while a change is made: leaving the manager through the `[Exit]`
  button is what offers "restart the game now", "back to the manager" or "exit the game".
* 28 UI languages, switchable from the title bar at any time, without affecting the game.
* The manager is appended as an extra `classes4.dex`, so the existing dex files and the
  huge `.so` entries are never rewritten.

## Quick start

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
pwsh -File core/tools/prepare_work.ps1 -GameApk "D:\APK\your-firefight.apk"  # decode + patch
pwsh -File core/tools/build.ps1                                             # compile + inject + sign
pwsh -File core/tools/verify_apk.ps1                                        # static verification
adb install -r out\Firefight-Mod-Manager-13.2.0-mgr0.1.2.apk
```

`pwsh` in the examples is PowerShell 7; under Windows PowerShell 5.1 use `powershell -File` instead (both work).

Which tools you need, what each step does and what to do when the install fails:
**[docs/deploy.md](docs/deploy.md)**.

## Layout

```
core/                 # everything "core": sources, resources, build scripts
  src/                #   manager Java sources (com.windowsgames.firefight)
  stub/               #   SDLActivity stub, compile-time only, never packaged
  assets/Mod/         #   sample mods shipped with the package
  tools/              #   build and verification scripts (PowerShell + Python)
  work/               #   [generated] decoded game worktree
  build/ out/ logs/   #   [generated] intermediates / artifacts / logs
docs/                 # documentation
```

The worktree, build artifacts, logs and framework file are never committed, see
`.gitignore`; the repository holds only sources, scripts and docs (< 1 MB).

## Documentation

| Document | Contents |
| --- | --- |
| [docs/deploy.md](docs/deploy.md) | **Deployment guide**: required tools, what the two commands do, what is added to the package, troubleshooting, rollback |
| [docs/deploy-mt-mcp.md](docs/deploy-mt-mcp.md) | **On-device deployment**: inject the compiled dex with MT Manager's MCP, patch the manifest and sign, without a PC toolchain |
| [docs/build.md](docs/build.md) | Build script arguments, toolchain resolution order, how the scripts verify themselves |
| [docs/architecture.md](docs/architecture.md) | Manager code structure: what each class does, how the UI is built, where data lives |
| [docs/mod-format.md](docs/mod-format.md) | File formats of mod packages and custom maps (read this when building mods/maps) |
| [docs/project-layout.md](docs/project-layout.md) | Directory conventions and "what must not be committed" |
| [docs/history.md](docs/history.md) | Development log: what changed in each round and which traps were hit |

Chinese versions of every document live in [docs/zh-CN/](docs/zh-CN/).

## Repository conventions

Everything published on GitHub is written in **English**: the repository description,
commit messages, Pull Request titles and bodies, and issue text. Source comments and
script output are English too. Only the documents have a Chinese version, in
`docs/zh-CN/`, and `README.zh-CN.md`.

The only Chinese and Japanese left in the English tree is translation **data**, which has to
stay verbatim: the translation tables in `core/tools/l10n_*.py`, the `自定义地图` /
`カスタムマップ` patterns in `verify_apk.ps1` (they match strings inside the built package),
the `name_zh` field of the sample mod, and the Chinese versions of the documents.

## Links

| Item | Address |
| --- | --- |
| Website (game) | <http://www.windowsgames.co.uk/ff.html> |
| Repository | <https://github.com/BiancaoAwa/Firefight-Mod-Manager-Android> |
| Author | <https://github.com/BiancaoAwa> |
| Bilibili | <https://space.bilibili.com/1499437543> |
| Afdian | <https://afdian.com/a/bc_awa> |
| E-mail | BC_awa@163.com |

## License

MIT, see [LICENSE](LICENSE).

The manager code was written by this project's author; the Firefight game itself, the
official packager and the official framework are copyright Windows Games Ltd. Before
publishing a full package with the manager inside, make sure you are allowed to
redistribute the game content it contains.
