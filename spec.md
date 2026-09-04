# Spec — Plugin IntelliJ "Giuan Tomcat"

Plugin IntelliJ che genera un'istanza CATALINA_BASE (struttura + `server.xml` + `context.xml`) e
avvia Tomcat 9. Il catalina base è **auto-generato** nella cartella temporanea di sistema (niente
campo utente): ogni run configuration usa `<java.io.tmpdir>/giuan-tomcat/<hash>/` dove `<hash>` è
uno SHA-256 (hex troncato a 16 caratteri) di `project.getLocationHash() + "|" + nomeConfig`,
così config omonime in progetti diversi non collidono. Il `context.xml` viene generato dentro il
catalina base, con il docBase scelto dall'utente, le classi compilate (`target/classes`) dei moduli
scelti montate su `/WEB-INF/classes` e le librerie jar di dipendenza (escluso il JDK) su
`/WEB-INF/lib`.

## 1. Rinomina e suddivisione in package

Root package: `org.giuantomcat`.

| Classe attuale                     | Nuova classe                      | Package                            |
|------------------------------------|-----------------------------------|------------------------------------|
| `DemoRunConfigurationType`         | `GiuanTomcatConfigurationType`    | `org.giuantomcat.runConfiguration` |
| `DemoConfigurationFactory`         | `GiuanTomcatConfigurationFactory` | `org.giuantomcat.runConfiguration` |
| `DemoRunConfigurationOptions`      | `GiuanTomcatRunConfigurationOptions` | `org.giuantomcat.runConfiguration.settings` |
| `DemoRunConfiguration`             | `GiuanTomcatRunConfiguration`     | `org.giuantomcat.runConfiguration.runner` |
| `DemoSettingsEditor`               | `GiuanTomcatSettingsEditor`       | `org.giuantomcat.runConfiguration.ui` |

- ID configurazione: `GiuanTomcatRunConfiguration`.
- Nome visualizzato: `Giuan Tomcat`.

Nuove classi nel package `org.giuantomcat.tomcat`:
- `ClasspathResolver` — risolve `target/classes` e jar di dipendenza dei moduli.
- `ContextXmlBuilder` — genera il contenuto del `context.xml`.
- `CatalinaBaseGenerator` — genera struttura catalina base + `server.xml` + `context.xml`.
- `GiuanTomcatPaths` — calcola i path di runtime sotto `java.io.tmpdir`:
  - `runtimeRoot = <tmp>/giuan-tomcat/<hash(project, configName)>`
  - `catalinaBase = runtimeRoot/catalina-base`
  - `mergedRoot = runtimeRoot/giuan-merged`

## 2. Opzioni (stato persistito) — `GiuanTomcatRunConfigurationOptions`

Campi (`StoredProperty`):
- `catalinaHome` (String) — installazione Tomcat (CATALINA_HOME).
- `webContent` (String) — docBase (contenuto web).
- `contextPath` (String) — es. `/myapp`.
- `httpPort` (String, default `"8080"`).
- `shutdownPort` (String, default `"8005"`).
- `moduleNames` (Set<String>).
- `jarSkipTokens` (Set<String>) — skip granulare persistito come token nella forma
  `"<module>|<entry>|tld"` o `"<module>|<entry>|pluggable"` (vedi `SkipTokens`): escludono **un
  singolo jar** di un modulo dallo scan TLD/taglib o pluggability/SCI.
  Dedup per-flag: un jar è escluso se compare in almeno un modulo selezionato (vedi §6/§7).
- `skipAnnotationScan` (Boolean, default `false`) — spunta esterna **globale**: all'avvio il
  generator modifica il `WEB-INF/web.xml` del web content (vedi §4).
- `hotSwapEnabled` (Boolean, default `false`) — abilita la modalità HotSwap.
- `dcevmJdkPath` (String) — home della JDK DCEVM dedicata (JDK 8 con DCEVM già installato).
- `hotswapAgentPath` (String) — percorso di `hotswap-agent.jar` (selezionato manualmente).

Rimosso `scriptName`.

## 3. Editor GUI — `GiuanTomcatSettingsEditor`

Input:
- `TextFieldWithBrowseButton` (directory chooser) per CATALINA_HOME, web content.
- `JTextField` per context path, HTTP port, shutdown port.
- Riga **Modules** con riepilogo di sola lettura (conteggio/nomi dei moduli selezionati) e bottone
  `Configure...` che apre il popup modale `ModuleSelectorDialog` (`DialogWrapper`). Il popup mostra
  un pannello classpath a **3 colonne sulla stessa riga** (via `Splitter` orizzontali):
  - **Available modules** — `Tree` dei moduli raggruppati per cartella (vedi `ModuleTreeBuilder`),
    drag&drop + bottone `>`.
  - **Selected modules** — `Tree`, con rail di trasferimento (`>`, `✕`, `✕ all`) tra Available e
    Selected, drag&drop.
  - **Manage skips** — selezionando un modulo nella colonna centrale mostra (vedi
    `ModuleDependencies`) la lista dei suoi jar di dipendenza (righe con checkbox **Skip TLD** /
    **Skip pluggable**, con due "select all" in testa). Nessuno skip annotation per-modulo: quello è
    la spunta esterna globale sottostante.
  Stato e logica della selezione/skip sono separati dalla view in
  `ClasspathModulesController`/`ClasspathModulesPanel`; il dialog espone `getModuleNames()` /
  `getSkipTokens()` e OK/Cancel (Cancel non salva).
- `JBCheckBox` **"Skip Servlet annotation scan…"** — spunta esterna, **globale e comune a tutti i
  moduli** (fuori dal popup Modules), vedi §4.
- `JBCheckBox` "Enable HotSwap (DCEVM + hotswap-agent)": alla spunta apre
  `HotSwapConfigDialog` (chooser cartella JDK DCEVM + file `hotswap-agent.jar`) e salva i
  percorsi; se annullato la checkbox resta deselezionata. Nessun download automatico: si dà per
  scontato che esista già una JDK 8 con DCEVM installato come altjvm (vedi guida
  [hotswapagent.org](https://hotswapagent.org/mydoc_quickstart.html)) e che
  `hotswap-agent.jar` sia stato scaricato dalle release di HotswapAgent.

Metodi:
- `resetEditorFrom` — carica i valori dalla configurazione nella GUI.
- `applyEditorTo` — salva i valori dalla GUI nella configurazione.
- `createEditor` — restituisce il pannello.

## 4. Esecuzione — `GiuanTomcatRunConfiguration`

- `skipAnnotationScan` (Boolean, default `false`) — flag **globale** (spunta esterna): quando è
  attivo, all'avvio il generator modifica il `WEB-INF/web.xml` del web content aggiungendo
  `metadata-complete="true"` al `<web-app>` e un `<absolute-ordering/>` vuoto; quando non è attivo
  **rimuove** esattamente ciò che aveva aggiunto. Le aggiunte sono marcate da un commento
  `<!--[GiuanTomcat skip:annotation-scan]-->` così da distinguerle da contenuti preesistenti (mai
  rimossi). Idempotente.
- `getConfigurationEditor()` → `GiuanTomcatSettingsEditor`.
- `getState(executor, environment)` → `GiuanTomcatCommandLineState`.
- Implementa `JavaDebugAware` (debug nativo IntelliJ):
  - `isDebuggable() = true`.
  - `getRemoteConnection()`: se `executor` è `DefaultDebugExecutor` ritorna
    `new RemoteConnection(true, "127.0.0.1", <porta libera>, false)`, altrimenti `null`.

## 5. `GiuanTomcatCommandLineState extends JavaCommandLineState`

- `createJavaParameters()`:
  - Prima di tutto chiama `ClasspathResolver` + `CatalinaBaseGenerator`. I path dell'istanza
    vengono da `GiuanTomcatPaths.catalinaBase(project, nomeConfig)` /
    `.mergedRoot(...)`; `CatalinaBaseGenerator.generate(...)` non riceve più `catalinaBase`
    (né `mergedRoot`): li calcola internamente via `GiuanTomcatPaths`.
  - classpath: `$CATALINA_HOME/bin/bootstrap.jar`, `$CATALINA_HOME/bin/tomcat-juli.jar`.
  - main class `org.apache.catalina.startup.Bootstrap`, arg `start`.
  - sysprops: `-Dcatalina.home`, `-Dcatalina.base`,
    `-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager`,
    `-Djava.util.logging.config.file=$CATALINA_BASE/conf/logging.properties`.
  - working directory = `CATALINA_BASE`.
- Se `hotSwapEnabled`: aggiunge `-XXaltjvm=dcevm` (sempre, anche in Debug) +
  `-Dhotswap.extraClasspath=<dirs classi>`. L'agent è `-javaagent:<agent>=autoHotswap=true`
  in Run (watch globale che ricarica le classi compilate) e `-javaagent:<agent>` senza
  autoHotswap in Debug, dove il reload lo fa il debugger di IntelliJ (JDWP + DCEVM), come da
  guida [hotswapagent.org](https://hotswapagent.org/mydoc_quickstart.html). Imposta
  la JDK del processo risolvendo `dcevmJdkPath` (via `ProjectJdkTable` o `JavaSdk.createJdk`),
  con fallback all'SDK di progetto/modulo.

## 6. `ClasspathResolver`

- **Classi (`/WEB-INF/classes`)**: per ogni modulo scelto, content root del modulo
  (`ModuleRootManager.getContentRoots()`) → `<root>/target/classes` (path Maven letterale).
  Se manca → skip + warning (nessuna build forzata).
- **Jar (`/WEB-INF/lib`)**: `OrderEnumerator.orderEntries(module).recursively().withoutSdk().libraries().getRoots()`
  filtrando i file `.jar` (esclude JDK).
- Deduplicazione in `LinkedHashSet`.
- **Skip scan (granulare, per jar)**: per ogni modulo selezionato si considerano i token
  `"<module>|<jarName>|tld"` e `"<module>|<jarName>|pluggable"`; i nomi risultanti finiscono in
  `Classpath.skippedTldJarNames` / `Classpath.skippedPluggabilityJarNames` (liste separate, dedup per
  flag: un jar è escluso se flaggato in almeno un modulo; il mount resta comunque attivo).

## 7. `CatalinaBaseGenerator` + `ContextXmlBuilder`

1. Crea `conf/`, `logs/`, `work/`, `temp/`, `webapps/`, `conf/Catalina/localhost/`.
2. Copia da `CATALINA_HOME/conf` i default mancanti: `web.xml`, `catalina.properties`,
   `tomcat-users.xml`, `logging.properties`.
3. Genera `conf/server.xml` da template con `shutdownPort` e `httpPort`.
4. Genera `conf/Catalina/localhost/<context>.xml` (nome = `contextPath` senza `/`; `ROOT` per `/`).
   Se `tldSkip` o `pluggabilitySkip` non è vuota, il `<Context>` riceve `reloadable="false"` e
   `containerSciFilter="org\.apache\.tomcat\.websocket\.server\.WsSci"` e viene anteposto un blocco
   `<JarScanner>` con `scanClassPath`/`scanBootstrapClassPath`/`scanAllDirectories`/`scanAllFiles`
   a `false` e un `<JarScanFilter>` che esclude dallo scan i soli jar flaggati, con liste
   **indipendenti**: `pluggabilitySkip="..."` solo se la lista pluggability non è vuota e
   `tldSkip="..."` solo se la lista TLD non è vuota; i jar non flaggati restano scansionati e tutti
   i jar restano montati:

```xml
<Context docBase="<webContent>" reloadable="false"
         containerSciFilter="org\.apache\.tomcat\.websocket\.server\.WsSci">
  <JarScanner scanClassPath="false" scanBootstrapClassPath="false"
              scanAllDirectories="false" scanAllFiles="false">
    <JarScanFilter pluggabilitySkip="dep1.jar" tldSkip="dep1.jar,dep2.jar"/>
  </JarScanner>
  <Resources>
    <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                   base="<module>/target/classes" webAppMount="/WEB-INF/classes"/>
    <PreResources className="org.apache.catalina.webresources.FileResourceSet"
                   base="<dep>.jar" webAppMount="/WEB-INF/lib/<dep>.jar"/>
  </Resources>
</Context>
```

Se invece le due liste sono vuote, il `<Context>` resta invariato (`<Context docBase="...">`)
e nessun `<JarScanner>` viene emesso.

## 8. Config / build

- `plugin.xml`: aggiungere `<depends>com.intellij.modules.java</depends>`,
  aggiornare name e `configurationType` con il nuovo FQN.
- `gradle.properties`: `group = org.giuantomcat`.
- `README.md`: aggiornare nomi e descrizione.

## Note / rischi

- Il debug nativo richiede `com.intellij.modules.java` (API `com.intellij.debugger`).
- Porta di debug allocata dinamicamente (porta libera) per evitare conflitti.
- `target/classes` mancante → mount saltato con warning (nessun errore bloccante).
