# Spec — Plugin IntelliJ "Giuan Tomcat"

Plugin IntelliJ che genera un'istanza CATALINA_BASE (struttura + `server.xml` + `context.xml`) e
avvia Tomcat 9. Il `context.xml` viene generato dentro il catalina base, con il docBase scelto
dall'utente, le classi compilate (`target/classes`) dei moduli scelti montate su `/WEB-INF/classes`
e le librerie jar di dipendenza (escluso il JDK) su `/WEB-INF/lib`.

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

## 2. Opzioni (stato persistito) — `GiuanTomcatRunConfigurationOptions`

Campi (`StoredProperty`):
- `catalinaHome` (String) — installazione Tomcat (CATALINA_HOME).
- `catalinaBase` (String) — istanza (CATALINA_BASE).
- `webContent` (String) — docBase (contenuto web).
- `contextPath` (String) — es. `/myapp`.
- `httpPort` (String, default `"8080"`).
- `shutdownPort` (String, default `"8005"`).
- `moduleNames` (Set<String>).
- `modulesSkipJarScan` (Set<String>) — sottoinsieme dei moduli scelti per cui lo scan Tomcat dei jar di
  dipendenza viene saltato (spuntato per-modulo nella lista Selected modules): sia lo scan pluggability
  (SCI/annotazioni) sia quello TLD/taglib **dei soli jar di tali moduli** (vedi §6/§7).
- `hotSwapEnabled` (Boolean, default `false`) — abilita la modalità HotSwap.
- `dcevmJdkPath` (String) — home della JDK DCEVM dedicata (JDK 8 con DCEVM già installato).
- `hotswapAgentPath` (String) — percorso di `hotswap-agent.jar` (selezionato manualmente).

Rimosso `scriptName`.

## 3. Editor GUI — `GiuanTomcatSettingsEditor`

Input:
- `TextFieldWithBrowseButton` (directory chooser) per CATALINA_HOME, CATALINA_BASE, web content.
- `JTextField` per context path, HTTP port, shutdown port.
- Due `JBList<Module>` (Selected/Available) con drag&drop.
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

- `skipAnnotationScan` (Boolean) — flag globale: all'avvio il generator modifica il
  `WEB-INF/web.xml` del web content. Se attivo aggiunge `metadata-complete="true"` al `<web-app>` e
  un `<absolute-ordering/>` vuoto; se disattivo **rimuove** esattamente ciò che aveva aggiunto.
  Le aggiunte sono marcate da un commento `<!--[GiuanTomcat skip:annotation-scan]-->` così da
  distinguerle da contenuti preesistenti (mai rimossi). Idempotente.
- `getConfigurationEditor()` → `GiuanTomcatSettingsEditor`.
- `getState(executor, environment)` → `GiuanTomcatCommandLineState`.
- Implementa `JavaDebugAware` (debug nativo IntelliJ):
  - `isDebuggable() = true`.
  - `getRemoteConnection()`: se `executor` è `DefaultDebugExecutor` ritorna
    `new RemoteConnection(true, "127.0.0.1", <porta libera>, false)`, altrimenti `null`.

## 5. `GiuanTomcatCommandLineState extends JavaCommandLineState`

- `createJavaParameters()`:
  - Prima di tutto chiama `ClasspathResolver` + `CatalinaBaseGenerator`.
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
- **Skip scan (per modulo)**: i moduli in `skipScanModuleNames` marcano i propri jar come "da non
  scansionare"; un jar è saltato se compare in **almeno un** modulo flaggato. I nomi-file risultanti
  finiscono in `Classpath.skippedJarNames` (il mount resta comunque attivo).

## 7. `CatalinaBaseGenerator` + `ContextXmlBuilder`

1. Crea `conf/`, `logs/`, `work/`, `temp/`, `webapps/`, `conf/Catalina/localhost/`.
2. Copia da `CATALINA_HOME/conf` i default mancanti: `web.xml`, `catalina.properties`,
   `tomcat-users.xml`, `logging.properties`.
3. Genera `conf/server.xml` da template con `shutdownPort` e `httpPort`.
4. Genera `conf/Catalina/localhost/<context>.xml` (nome = `contextPath` senza `/`; `ROOT` per `/`).
   Se `skippedJarNames` non è vuoto, il `<Context>` riceve `reloadable="false"` e
   `containerSciFilter="org\.apache\.tomcat\.websocket\.server\.WsSci"` e viene anteposto un blocco
   `<JarScanner>` con `scanClassPath`/`scanBootstrapClassPath`/`scanAllDirectories`/`scanAllFiles`
   a `false` e un `<JarScanFilter pluggabilitySkip="..." tldSkip="..."/>` che esclude dallo scan
   (pluggability **e** TLD/taglib) **i soli jar dei moduli flaggati**; i jar degli altri moduli
   restano scansionati e tutti i jar restano montati:

```xml
<Context docBase="<webContent>" reloadable="false"
         containerSciFilter="org\.apache\.tomcat\.websocket\.server\.WsSci">
  <JarScanner scanClassPath="false" scanBootstrapClassPath="false"
              scanAllDirectories="false" scanAllFiles="false">
    <JarScanFilter pluggabilitySkip="dep1.jar,dep2.jar" tldSkip="dep1.jar,dep2.jar"/>
  </JarScanner>
  <Resources>
    <PreResources className="org.apache.catalina.webresources.DirResourceSet"
                   base="<module>/target/classes" webAppMount="/WEB-INF/classes"/>
    <PreResources className="org.apache.catalina.webresources.FileResourceSet"
                   base="<dep>.jar" webAppMount="/WEB-INF/lib/<dep>.jar"/>
  </Resources>
</Context>
```

Se invece non ci sono jar da saltare, il `<Context>` resta invariato (`<Context docBase="...">`)
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
