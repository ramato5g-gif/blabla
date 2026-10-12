# Bla Bla

App Android di Comunicazione Aumentativa Alternativa (Kotlin + Jetpack Compose), pensata per
l'uso logopedico e la raccolta dati di una tesi di laurea.

## Funzioni
- **Repertorio di circa 700 tessere** in 30 argomenti (parole base, saluti, bisogni, domande, persone,
  emozioni, routine, azioni, cibo, frutta e verdura, bevande, corpo, salute, vestiti, casa, scuola, giochi,
  animali, luoghi, trasporti, tempo, meteo, colori, numeri, descrizioni, posizioni, parole piccole, sport, feste).
  **🔎 Pittogrammi automatici**: sostituisce le emoji con i pittogrammi ARASAAC (per argomento o per tutte).
- **📖 Storie in simboli**: storie sociali e sequenze, pagina per pagina con testo, simboli e immagine.
  ✨ i simboli si generano dal testo; lettura della frase intera 🔊 o parola per parola 👆 con evidenziazione.
- **Comunicatore**: barra della frase (tocca 🔊 o la barra per leggerla), ⌫ cancella l'ultima, ✖ svuota;
  riga delle **parole base** (vocabolario nucleare) sempre visibile; argomenti a sinistra (orizzontale)
  o in alto (verticale); griglia di tessere.
- **⌨ Tastiera CAA**: tasti grandi in stampato maiuscolo, ordine ABC o QWERTY, carattere ad alta
  leggibilità **Atkinson Hyperlegible**; suggerimenti di completamento con il pittogramma della tessera;
  le parole scritte si aggiungono alla frase.
- **🎬 Video delle storie**: MP4 1280×720 con voce narrante e simboli che si illuminano durante la lettura,
  creato sul dispositivo e salvato in Download.
- **Tessere e argomenti** (Area terapista → ✏️): crea, modifica, riordina, elimina.
  Immagine da **pittogrammi ARASAAC** (ricerca in italiano), galleria, fotocamera o emoji.
  Categoria grammaticale con i colori della **chiave di Fitzgerald**. Frase facoltativa e **voce registrata**.
- **Sessioni**: avvia/termina una sessione per ogni incontro; i dati vengono raggruppati per sessione, con note.
- **Statistiche**: tessere toccate, tessere diverse, frasi, lunghezza media della frase (in tessere),
  frase più lunga, categorie grammaticali, argomenti, tessere più usate, ultime frasi.
  Esportazione **CSV** degli eventi e del riepilogo per sessione (si apre in Excel).
- **Profili** (un utente per profilo), **impostazioni** (colonne, maiuscolo, voce, tocchi ravvicinati),
  **protezione bambino**, **blocco app sullo schermo**, **backup/ripristino**.

## Compilare l'APK
Carica il contenuto di questa cartella in un repository GitHub e crea il file
`.github/workflows/build-apk.yml` (Add file → Create new file). A build verde, l'APK è in Releases.

## Crediti
Pittogrammi: autore Sergio Palao, origine ARASAAC (https://arasaac.org), licenza CC BY-NC-SA,
proprietà del Governo di Aragona (Spagna).

## Carattere
Atkinson Hyperlegible © 2020 Braille Institute of America, Inc., licenza SIL Open Font License 1.1
(testo della licenza in `app/src/main/assets/licenze`).

## Privacy
Tutti i dati restano sul dispositivo. Nei profili usa iniziali o codici.
