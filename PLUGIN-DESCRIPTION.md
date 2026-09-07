# Giuan Tomcat — Plugin Description

Markdown version of the plugin description declared in
[`src/main/resources/META-INF/plugin.xml`](src/main/resources/META-INF/plugin.xml).

## Run Apache Tomcat directly from IntelliJ IDEA

"Giuan Tomcat" adds a **Giuan Tomcat** run configuration that starts an Apache Tomcat instance
without requiring the bundled Tomcat integration or any external plugin. The plugin:

- **auto-generates the CATALINA_BASE** (conf, logs, work, temp, webapps) in the system temp folder;
- writes `server.xml` and the per-application `context.xml`;
- launches `Bootstrap` with full native IntelliJ debug support (breakpoints, variables, stack
  traces).

No WAR is assembled: the web-content directory you choose is used directly as the `docBase`, and
classes/libraries are attached through the generated `context.xml`.

## Supported versions

### Tomcat

The plugin works with any Apache Tomcat that keeps the standard layout
(`bin/bootstrap.jar` + `bin/tomcat-juli.jar`):

| Version | Status | Notes |
|---|---|---|
| Tomcat 9.x | Developed and tested against | Recommended reference version |
| Tomcat 8.x / 8.5 | Compatible | Same javax layout |
| Tomcat 7.x | Compatible | Standard layout, same mechanism |
| Tomcat 10+ / 11 | Compatible with caveats | Your web application must use the **Jakarta EE** namespace (`jakarta.*`) and a suitable JDK (Java 11+) |

All generated elements (`PreResources`/`DirResourceSet`, `JarScanner`/`JarScanFilter`,
`containerSciFilter`) are available from Tomcat 7 onward, so the plugin does not depend on a
specific Tomcat version.

### Java / JDK

- **Normal run (no HotSwap):** any JDK — the plugin uses the project SDK.
- **HotSwap mode:** requires a JDK patched with DCEVM. Per the
  [DCEVM project](https://dcevm.github.io/) the supported HotSpot versions are **7, 8, 9, 10 and
  11** (see "HotSwap prerequisites" below).

## Features

### Class & library consolidation (`giuan-merged`)

For each run the plugin builds a merged classpath under
`<tmp>/giuan-tomcat/<hash>/giuan-merged`:

- the `target/classes` of the selected modules are merged into a single `WEB-INF/classes`;
- their compile-scope dependency jars are gathered into `WEB-INF/lib`.

Class files and jars are exposed via **hard links**, and whole class/resource subtrees that are
unique to a single module are linked directly as a directory junction/symlink; folders shared by
several modules (e.g. the same package present in different `target/classes`) are merged into one
real directory. Only when the filesystem does not allow linking does the plugin fall back to a
physical copy.

Because links share the underlying files, a recompile is immediately visible to the running
server. The merge is **incremental**: a manifest remembers the previous state so unchanged
modules and jars are not relinked on the next run.

### No build/deploy step

The consolidated `WEB-INF/classes` and `WEB-INF/lib` are mounted on the web app through Tomcat
`PreResources` (`DirResourceSet`) at `/WEB-INF/classes` and `/WEB-INF/lib`, so nothing is copied
into your web content.

### Startup scan skip (granular, per jar)

The classpath panel has three columns (Available / Selected modules / Manage skips) letting you
choose, for every dependency jar of a module, whether it is skipped or not, and speed up boot on
large applications:

- **Per dependency jar (Skip TLD / Skip pluggable)** — for each jar of the module selected in the
  middle list you can independently decide to skip (**skip**) or keep (**non-skip**) it in the
  TLD/taglib scan (`tldSkip`) or in the pluggability/SCI scan (`pluggabilitySkip`) of the
  generated `context.xml`. As soon as at least one jar is excluded the plugin sets
  `reloadable="false"` and `containerSciFilter` on the context and disables `scanClassPath`,
  `scanBootstrapClassPath`, `scanAllDirectories` and `scanAllFiles`. Non-flagged jars remain
  scanned and every jar stays mounted and usable; jars of the other modules are still scanned.

- **Annotation scan skip (global checkbox, common to all modules)** — on run the plugin edits the
  `WEB-INF/web.xml` of the web content, adding `metadata-complete="true"` to the `<web-app>`
  element and an empty `<absolute-ordering/>`. What is skipped: Servlet 3.0 annotation scanning
  (`@WebServlet`/`@WebFilter`/...) and the discovery and processing of web-fragments and
  ServletContainerInitializer registered in the application jars. The additions are tracked with a
  marker comment so that removing the flag on a later run **removes exactly what the plugin
  added**; any pre-existing `metadata-complete`/`<absolute-ordering>` are left untouched. Changes
  are applied idempotently.

### Simple configuration

Choose CATALINA_HOME, web content (docBase), context path and HTTP/shutdown ports from a single
dialog, plus the modules that contribute to the application classpath. The CATALINA_BASE is
auto-generated in the system temp folder
(`<tmp>/giuan-tomcat/<hash>/catalina-base`, where the hash derives from the project location and
the configuration name).

### Optional HotSwap mode (DCEVM + hotswap-agent)

Enable unlimited runtime class redefinition (add/remove fields and methods without restarting).
It requires a JDK patched with DCEVM (see prerequisites below); the patched JDK is always forced
when HotSwap is active, in Run and Debug alike: in Debug the IDE debugger performs the reload
(JDWP), in Run the agent watches `target/classes` automatically (`autoHotswap`).

### Generated instance reuse

Each configuration maps to its own folder under the system temp dir (hash of project +
configuration name, no collisions between projects); the instance is kept between runs and can be
started again after it is stopped.

### Server Manager tool window

A right-side tool window (Maven-style toolbar + collapsible instance list) to manage **remote**
Tomcat instances through the **Manager text API** (`/manager/text`):

- **Instances** are added by machine address + HTTP port, with per-instance Manager credentials
  (username/password) stored securely in the IDE PasswordSafe; SSH for the logs is **optional** and
  must be enabled per instance. The list of instances is persisted at IDE level.
- For every instance you see the deployed **applications** with their state
  (🟢 running / 🔴 stopped) and **session count**.
- Per application you can **start**, **stop**, **reload** and **undeploy** (icon buttons with
  tooltip, plus context actions); new applications are **deployed** by uploading a local **WAR**
  under the chosen context path.
- **Logs** (only when SSH is enabled for the instance): an SSH connection (password or private
  key, port/username/log file configurable per instance) tails the remote log into an IDE console
  tab, closed together with the tab.

## HotSwap prerequisites

A JDK patched with DCEVM — per the [DCEVM project](https://dcevm.github.io/) the supported HotSpot
versions are **7, 8, 9, 10 and 11**:

- light installers for **8u181**, **8u152** and **7u79**:
  [DCEVM binaries](https://dcevm.github.io/) / [all releases](https://github.com/dcevm/dcevm/releases)
- **Java 11** via [Trava OpenJDK](https://github.com/TravaOpenJDK/trava-jdk-11-dcevm/releases),
  which ships with hotswap-agent integrated;
- a `hotswap-agent.jar` downloaded from the
  [HotswapAgent releases](https://github.com/HotswapProjects/HotswapAgent/releases).

The plugin launches Tomcat with `-XXaltjvm=dcevm` plus the agent. For a normal run (no HotSwap)
any JDK works: the plugin uses the project SDK.

> **HotSwap environment setup:** see [`GUIDA-HOTSWAP.md`](GUIDA-HOTSWAP.md).
