# Style & Conventions — Giuan Tomcat

Regole di stile e organizzazione del codice del progetto. Ogni nuova feature deve rispettarle.

## 1. Costanti sempre `static final`

- Nessun literal disperso nel codice. Ogni valore ripetuto o con significato di dominio
  (default di configurazione, nomi di path/cartelle, marker, header) va estratto in una
  costante `static final`.
- I valori condivisi tra più classi sono centralizzati in una classe dedicata
  (`org.giuantomcat.GiuanTomcatConstants`) e importati dove servono.
- I valori usati da una sola classe restano come costanti `private static final` locali.
- Le stringhe dei *message* (testi utente/log) sono l'unica eccezione tollerata: vanno comunque
  preferite costanti nominate quando ripetute.

## 2. Una classe controller per ogni UI

- Separazione tra la costruzione della view e la logica/stato.
- Ogni interfaccia UI (panel/dialog) ha una classe dedicata che la gestisce: la parte grafica
  costruisce i componenti, il controller mantiene lo stato e reagisce agli eventi.
- `SettingsEditor` e compagni devono delegare la gestione dei pannelli complessi a controller
  espliciti (es. `ClasspathModulesPanel` + `ClasspathModulesController`).

## 3. Metodi con stessa firma su più OS → interfaccia + implementazioni iniettate

- Quando un'operazione ha lo stesso contratto ma implementazione diversa per ogni sistema
  operativo (es. linker filesystem), si definisce una **interfaccia** e le **implementazioni**
  vengono selezionate a runtime via factory (pattern strategy/factory).
- Mai usare rami `if (isWindows())` sparsi nel codice: incapsularli nell'implementazione scelta.
- Esempio: `org.giuantomcat.tomcat.link.FileLinker` con `WindowsFileLinker`/`UnixFileLinker`
  restituite da `FileLinkerFactory.get()`.
