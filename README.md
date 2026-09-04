# Giuan Tomcat

IntelliJ plugin that generates a `CATALINA_BASE` instance (structure + `server.xml` + `context.xml`)
and starts Tomcat 9. The `context.xml` is generated inside the catalina base, with the docBase chosen
by the user, the compiled classes (`target/classes`) of the chosen modules mounted on `/WEB-INF/classes`
and the dependency jars (excluding the JDK) on `/WEB-INF/lib`.

The catalina base is **auto-generated in a system temporary folder**: each run configuration
uses a dedicated folder `<tmp>/giuan-tomcat/<hash>/` where `<hash>` derives from the project
path and the configuration name (truncated SHA-256), so homonymous configurations from different
projects never overlap. Inside it: `catalina-base/` (generated instance) and `giuan-merged/`
(consolidated resources).

## Configuration

The "Giuan Tomcat" run configuration exposes the following fields:

- **CATALINA_HOME** — path of the Tomcat installation.
- **Web content (docBase)** — directory containing the web content.
- **Context path** — e.g. `/myapp`.
- **HTTP port** — HTTP connector port (default `8080`).
- **Shutdown port** — shutdown port (default `8005`).
- **Modules** — row with a read-only summary of the selected modules and a
  `Configure...` button that opens a **3-column popup dialog** on the same row: available
  modules (grouped by folder), selected modules (whose `target/classes` and dependency jars are
  mounted on `/WEB-INF/classes` and `/WEB-INF/lib` respectively) and "Manage skips". Selecting
  a module in the middle column, the third column shows its dependencies with granular skips:
  - **Skip TLD / Skip pluggable (per single jar)** — ticking the checkboxes of a dependency jar
    of the selected module makes the plugin generate in the `context.xml` a
    `<JarScanner>/<JarScanFilter>` block that excludes **that jar** from the startup scan: TLD/taglib
    (`tldSkip`) or pluggability/SCI (`pluggabilitySkip`) separately. If at least one jar is
    excluded, it sets `reloadable="false"` and `containerSciFilter` on the `<Context>`, and disables
    `scanClassPath`/`scanBootstrapClassPath`/`scanAllDirectories`/`scanAllFiles`. Non-flagged jars
    remain scanned and all jars stay mounted and usable.
- **Skip annotation scan (global)** — external checkbox, common to all modules: at startup the
  plugin modifies the `WEB-INF/web.xml` of the web content by adding `metadata-complete="true"` to
  the `<web-app>` and an empty `<absolute-ordering/>` (prevents annotation scan and discovery of
  web-fragment/SCI across the whole application). The additions are marked with a comment: if you
  later untick the checkbox, on a subsequent run the plugin **removes** exactly what it added,
  leaving pre-existing elements intact. Idempotent.
- **Enable HotSwap (DCEVM + hotswap-agent)** — ticking the checkbox opens a dialog
  where you select:
  - **DCEVM JDK** — a JDK 8 with DCEVM already installed as altjvm (prerequisite: follow the
    official guide [hotswapagent.org](https://hotswapagent.org/mydoc_quickstart.html) to
    install the DCEVM patch on the JDK 8).
  - **hotswap-agent.jar** — downloaded by hand from the
    [HotswapAgent releases](https://github.com/HotswapProjects/HotswapAgent/releases) and
    placed in any location.
  The plugin registers the JDK as global and sets it in the configuration.

> **Full setup (JDK 8 + DCEVM patch + agent):** see [`GUIDA-HOTSWAP.md`](GUIDA-HOTSWAP.md).

## Execution and debug

At startup the plugin:

1. Resolves `target/classes` and the dependency jars of the selected modules.
2. Generates the catalina base structure (`conf`, `logs`, `work`, `temp`, `webapps`) in the
   temporary folder `<tmp>/giuan-tomcat/<hash>/catalina-base` and creates
   `conf/server.xml` with the chosen ports and `conf/Catalina/localhost/<context>.xml`.
3. Launches `org.apache.catalina.startup.Bootstrap` via `JavaCommandLineState`, so the debug
   uses IntelliJ's native debugger (no forced build).

### Hot reload

With HotSwap mode active the plugin starts Tomcat with mandatory DCEVM
(`-XXaltjvm=dcevm`, always, even in Debug) and the agent:

```
-XXaltjvm=dcevm -javaagent:<path>/hotswap-agent.jar=autoHotswap=true
```

- **Run**: the `autoHotswap=true` argument enables the watch at a global level (every
  classloader, including the webapp); the plugin additionally generates in `target/classes` a
  `hotswap-agent.properties` with `autoHotswap=true` and adds
  `-Dhotswap.extraClasspath=<target/classes>` so the agent watches the compiled classes.
- **Debug**: the agent is loaded **without** `autoHotswap` (the agent watchers conflict
  with the debugger); class reload is done by the IntelliJ debugger (JDWP + DCEVM
  active). For automatic reload after compilation: **Settings → Debugger → HotSwap →
  "Reload classes after compilation" = Always**.

After modifying classes: **Build (Ctrl+F9)** and the redefinition is applied without
restarting. Structural changes (adding fields/methods) require DCEVM active.

### Debug and DCEVM

With HotSwap active the plugin **always** uses `-XXaltjvm=dcevm`, even in Debug (the altjvm
is mandatory for structural changes).

