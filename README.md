# Giuan Tomcat

Plugin IntelliJ che genera un'istanza `CATALINA_BASE` (struttura + `server.xml` + `context.xml`) e
avvia Tomcat 9. Il `context.xml` viene generato dentro il catalina base, con il docBase scelto
dall'utente, le classi compilate (`target/classes`) dei moduli scelti montate su `/WEB-INF/classes`
e le librerie jar di dipendenza (escluso il JDK) su `/WEB-INF/lib`.

## Configurazione

La run configuration "Giuan Tomcat" espone i seguenti campi:

- **CATALINA_HOME** — percorso dell'installazione di Tomcat.
- **CATALINA_BASE** — percorso dell'istanza generata dal plugin.
- **Web content (docBase)** — directory contenente il contenuto web.
- **Context path** — es. `/myapp`.
- **HTTP port** — porta del connettore HTTP (default `8080`).
- **Shutdown port** — porta di shutdown (default `8005`).
- **Selected/Available modules** — moduli i cui `target/classes` e jar di dipendenza vengono
  montati rispettivamente su `/WEB-INF/classes` e `/WEB-INF/lib`.
  - **Skip scan (per modulo, spunta nella lista Selected)** — per i moduli flaggati il plugin
    genera nel `context.xml` un blocco `<JarScanner>/<JarScanFilter>` che salta lo scan di startup
    (pluggability/SCI e TLD/taglib) dei **soli jar di dipendenza di quei moduli**: sul `<Context>`
    imposta `reloadable="false"` e `containerSciFilter`, e disattiva
    `scanClassPath`/`scanBootstrapClassPath`/`scanAllDirectories`/`scanAllFiles`.
  - **Skip annotation scan (globale)** — all'avvio, se attivo, il plugin modifica il
    `WEB-INF/web.xml` del web content aggiungendo `metadata-complete="true"` al `<web-app>` e un
    `<absolute-ordering/>` vuoto (impedisce scan delle annotazioni e scoperta di
    web-fragment/SCI). Idempotente: aggiunge soltanto, non rimuove nulla.
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
2. Genera la struttura del catalina base (`conf`, `logs`, `work`, `temp`, `webapps`) e crea
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
├── runConfiguration.settings # GiuanTomcatRunConfigurationOptions
├── runConfiguration.ui       # GiuanTomcatSettingsEditor
├── runConfiguration.runner   # GiuanTomcatRunConfiguration, GiuanTomcatCommandLineState
└── tomcat                    # CatalinaBaseGenerator, ContextXmlBuilder, ClasspathResolver
```

## Icona

L'icona della run configuration è `src/main/resources/icons/tomcat.svg` (caricata via
`IconLoader`), mentre `src/main/resources/META-INF/pluginIcon.svg` è l'icona del plugin.

## Progetto di esempio

Vedi [`examples/hello-webapp`](examples/hello-webapp) per una webapp Maven da usare per testare
la configurazione (servlet + dipendenza Gson).
