# Guida HotSwap — Giuan Tomcat (JDK 8 + DCEVM + hotswap-agent)

Mini guida per configurare l'ambiente necessario alla modalità **HotSwap** del plugin
(`Enable HotSwap (DCEVM + hotswap-agent)`).

## 1. JDK da usare

Serve una **JDK 8** (la versione consigliata è **8u181**, quella per cui esiste la patch
DCEVM light stabile). Se non l'hai già, scarica ed installa:

- **JDK 8u181 (Windows x64)** — https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html
  (file `jdk-8u181-windows-x64.exe`)

> Le classi della tua webapp devono essere compilate **per Java 8** (bytecode versione 52):
> in IntelliJ imposta Project SDK / module SDK = JDK 8 e language level 8 (o
> `maven.compiler.source/target = 1.8`). Se compili con una JDK più nuova il runtime
> 8 non carica le classi (`UnsupportedClassVersionError`).

## 2. Patchare la JDK con DCEVM

DCEVM è un "patch" della JVM che permette la ridefinizione strutturale delle classi
(aggiungere campi/metodi) a runtime.

1. Scarica l'installer light per 8u181:
   https://github.com/dcevm/dcevm/releases/tag/light-jdk8u181%2B2
   → file `DCEVM-8u181-installer-build2.jar`
2. Avvialo **come Amministratore** (click destro → "Esegui come amministratore"):

   ```
   java -jar DCEVM-8u181-installer-build2.jar
   ```

3. Nella finestra:
   - seleziona la cartella della **JDK 8u181** (es. `C:\Users\giova\Java\jdk1.8.0_181`)
     — deve combaciare ESATTAMENTE con la versione dell'installer;
   - premi **"Install DCEVM as altjvm"**.
4. Verifica che la patch sia presente:

   ```
   C:\Users\giova\Java\jdk1.8.0_181\bin\dcevm\jvm.dll
   ```

   Se il file esiste, la JDK è pronta (si userà con `-XXaltjvm=dcevm`).

## 3. Scaricare l'agent

Scarica il jar di **hotswap-agent** (versione stabile 2.x, la più recente):

https://github.com/HotswapProjects/HotswapAgent/releases
→ file `hotswap-agent-<versione>.jar`

Mettilo dove preferisci (es. `C:\Users\giova\Downloads\hotswap-agent.jar`) e ricordati
il percorso.

Riferimenti ufficiali: https://hotswapagent.org/ (Quick Start JDK 8).

## 4. Configurare la run configuration

In **Edit Configurations → Giuan Tomcat**:

1. Spunta **Enable HotSwap (DCEVM + hotswap-agent)** (si apre il dialog).
2. **DCEVM JDK** → seleziona la cartella della JDK 8 patchata (es.
   `C:\Users\giova\Java\jdk1.8.0_181`).
3. **hotswap-agent.jar** → seleziona il jar scaricato.
4. OK. Il plugin ora lancia sempre:

   ```
   -XXaltjvm=dcevm -javaagent:<path>/hotswap-agent.jar[=autoHotswap=true]
   ```

   (DCEVM è **obbligatorio** quando HotSwap è attivo, anche in Debug).

## 5. Uso

### Run
- L'agent osserva `target/classes` (autoHotswap) e ricarica le classi compilate da solo.
- Dopo una modifica: **Build (Ctrl+F9)** → la ridefinizione viene applicata senza riavviare.

### Debug
- I watcher dell'agent sono disattivati (confliggono col debugger); il reload lo fa il
  debugger di IntelliJ su JDWP + DCEVM.
- Imposta il reload automatico: **Settings → Build, Execution, Deployment → Debugger →
  HotSwap → "Reload classes after compilation" = Always** (oppure usa l'azione
  "Hot Swap" dell'IDE dopo la build).
- Con DCEVM valgono anche i cambi strutturali (nuovi campi/metodi).

### Limiti
- Non supportato: cambi alla gerarchia (superclasse/interfacce), spostamenti di classi.
- La webapp deve essere compilata con JDK 8 (bytecode 52).
