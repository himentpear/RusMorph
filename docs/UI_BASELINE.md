# Protected WeRus UI baseline

The following files define the current v0.006 UI and must be reviewed together when changed:

- `release/ui-baseline.json` and `release/brand-assets.json`
- `app/src/main/java/org/namchieh/rusmorph/ui/design/**`
- `app/src/main/java/org/namchieh/rusmorph/ui/navigation/**`
- Launcher resources under `app/src/main/res/mipmap-*/ic_launcher*`, `app/src/main/res/drawable/ic_launcher*`, and `app/src/main/res/values/ic_launcher_background.xml`
- The current five-destination app shell: Home, Learning, Dictionary, Review, Profile

`RusMorphTheme` and `RusMorphColors` are compatibility wrappers around the Werus design system. `Tools` is not a top-level destination; `Courses` remains a compatibility redirect to Learning. The wallpaper settings and current launcher art are part of the present UI. Use `python tools/verify_product_contract.py` and `python tools/check_app_baseline.py` to check the product contract.
