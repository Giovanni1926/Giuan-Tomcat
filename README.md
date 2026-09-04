# Giuan Tomcat

Plugin IntelliJ che genera un'istanza `CATALINA_BASE` (struttura + `server.xml` + `context.xml`) e
avvia Tomcat 9. Il `context.xml` viene generato dentro il catalina base, con il docBase scelto
dall'utente, le classi compilate (`target/classes`) dei moduli scelti montate su `/WEB-INF/classes`
e le librerie jar di dipendenza (escluso il JDK) su `/WEB-INF/lib`.

Il catalina base è **auto-generato in una cartella temporanea di sistema**: ogni run configuration
usa una cartella dedicata `<tmp>/giuan-tomcat/<hash>/` dove `<hash>` deriva dal percorso del
progetto e dal nome della configurazione (SHA-256 troncato), così config omonime di progetti
diversi non si sovrappongono. All'interno: `catalina-base/` (istanza generata) e `giuan-merged/`
(risorse consolidate).

## Configurazione

La run configuration "Giuan Tomcat" espone i seguenti campi:

- **CATALINA_HOME** — percorso dell'installazione di Tomcat.
- **Web content (docBase)** — directory contenente il contenuto web.
- **Context path** — es. `/myapp`.
- **HTTP port** — porta del connettore HTTP (default `8080`).
- **Shutdown port** — porta di shutdown (default `8005`).
- **Modules** — riga con riepilogo di sola lettura dei moduli selezionati e bottone
  `Configure...` che apre un **dialog popup** a 3 colonne sulla stessa riga: moduli disponibili
  (raggruppati per cartella), moduli selezionati (i cui `target/classes` e jar di dipendenza vengono
  montati rispettivamente su `/WEB-INF/classes` e `/WEB-INF/lib`) e "Manage skips". Selezionando
  un modulo nella colonna centrale, la terza colonna mostra le sue dipendenze con skip granulari:
  - **Skip TLD / Skip pluggable (per singolo jar)** — spuntando le caselle di un jar di
    dipendenza del modulo selezionato, il plugin genera nel `context.xml` un blocco
    `<JarScanner>/<JarScanFilter>` che esclude **quel jar** dallo scan di startup: TLD/taglib
    (`tldSkip`) o pluggability/SCI (`pluggabilitySkip`) separatamente. Se almeno un jar è
    escluso, sul `<Context>` imposta `reloadable="false"` e `containerSciFilter`, e disattiva
    `scanClassPath`/`scanBootstrapClassPath`/`scanAllDirectories`/`scanAllFiles`. I jar non
    flaggati restano scansionati e tutti i jar restano montati e utilizzabili.
- **Skip annotation scan (globale)** — spunta esterna, comune a tutti i moduli: all'avvio il
  plugin modifica il `WEB-INF/web.xml` del web content aggiungendo `metadata-complete="true"` al
  `<web-app>` e un `<absolute-ordering/>` vuoto (impedisce scan delle annotazioni e scoperta di
  web-fragment/SCI sull'intera applicazione). Le aggiunte sono marcate con un commento: se poi
  togli la spunta, in una successiva esecuzione il plugin **rimuove** esattamente ciò che ha
  aggiunto, lasciando intatti elementi preesistenti. Idempotente.
- **Enable HotSwap (DCEVM + hotswap-agent)** — spuntando la checkbox il plugin apre un dialog
  dove selezionare:
  - **DCEVM JDK** — una JDK 8 con DCEVM già installato come altjvm (prerequisito: segui la
    guida ufficiale [hotswapagent.org](https://hotswapagent.org/mydoc_quickstart.html) per
    installare la patch DCEVM sulla JDK 8).
  - **hotswap-agent.jar** — scaricato a mano dalle
    [release di HotswapAgent](https://github.com/HotswapProjects/HotswapAgent/releases) e
    messo in un percorso qualsiasi.
  Il plugin registra la JDK come globale e la imposta nella configurazione.

> **Setup completo (JDK 8 + patch DCEVM + agent):** vedi [`GUIDA-HOTSWAP.md`](GUIDA-HOTSWAP.md).

## Esecuzione e debug

All'avvio il plugin:

1. Risolve `target/classes` e i jar di dipendenza dei moduli selezionati.
2. Genera la struttura del catalina base (`conf`, `logs`, `work`, `temp`, `webapps`) nella
   cartella temporanea `<tmp>/giuan-tomcat/<hash>/catalina-base` e crea
   `conf/server.xml` con le porte scelte e `conf/Catalina/localhost/<context>.xml`.
3. Lancia `org.apache.catalina.startup.Bootstrap` tramite `JavaCommandLineState`, così il debug
   usa il debugger nativo di IntelliJ (nessuna build forzata).

### Hot reload

Con la modalità HotSwap attiva il plugin avvia Tomcat con DCEVM obbligatorio
(`-XXaltjvm=dcevm`, sempre, anche in Debug) e l'agent:

```
-XXaltjvm=dcevm -javaagent:<path>/hotswap-agent.jar=autoHotswap=true
```

- **Run**: l'argomento `autoHotswap=true` abilita il watch a livello globale (ogni
  classloader, inclusa la webapp); il plugin genera inoltre in `target/classes` un
  `hotswap-agent.properties` con `autoHotswap=true` e aggiunge
  `-Dhotswap.extraClasspath=<target/classes>` così l'agente osserva le classi compilate.
- **Debug**: l'agent è caricato **senza** `autoHotswap` (i watcher dell'agent confliggono
  con il debugger); il reload delle classi lo fa il debugger di IntelliJ (JDWP + DCEVM
  attivo). Per il reload automatico dopo la compilazione: **Settings → Debugger → HotSwap →
  "Reload classes after compilation" = Always**.

Dopo una modifica alle classi: **Build (Ctrl+F9)** e la ridefinizione viene applicata senza
riavviare. Le modifiche strutturali (aggiunta di campi/metodi) richiedono DCEVM attivo.

### Debug e DCEVM

Con HotSwap attivo il plugin usa **sempre** `-XXaltjvm=dcevm`, anche in Debug (l'altjvm
è obbligatorio per i cambi strutturali).

## Struttura package

```
org.giuantomcat
├── runConfiguration          # GiuanTomcatConfigurationType, GiuanTomcatConfigurationFactory
├── runConfiguration.settings # GiuanTomcatRunConfigurationOptions, SkipTokens
├── runConfiguration.ui       # GiuanTomcatSettingsEditor, ClasspathModulesPanel/Controller,
│                             # ModuleTreeBuilder, HotSwapConfigDialog
├── runConfiguration.runner   # GiuanTomcatRunConfiguration, GiuanTomcatCommandLineState
└── tomcat                    # CatalinaBaseGenerator, ContextXmlBuilder, ClasspathResolver,
                              # ModuleDependencies, ResourceConsolidator, GiuanTomcatPaths
```

## Icona

L'icona della run configuration è `src/main/resources/icons/tomcat.svg` (caricata via
`IconLoader`), mentre `src/main/resources/META-INF/pluginIcon.svg` è l'icona del plugin.

## Progetto di esempio

Vedi [`examples/hello-webapp`](examples/hello-webapp) per una webapp Maven da usare per testare
la configurazione (servlet + dipendenza Gson).
