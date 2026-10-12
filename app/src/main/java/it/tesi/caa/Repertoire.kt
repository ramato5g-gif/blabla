package it.tesi.caa

/**
 * Repertorio iniziale: circa 600 tessere in 30 argomenti.
 * Formato compatto: "parola emoji" separate da ';'. Un suffisso #X cambia la categoria:
 * P=Persone, A=Azioni, D=Descrizioni, C=Cose, S=Sociale, Q=Domande, X=Altro.
 * Le immagini si possono poi sostituire con i pittogrammi ARASAAC (Area terapista → ✏️).
 */
object Repertoire {

    private class T(val id: String, val name: String, val emoji: String, val cls: WordClass, val items: String)

    private val data = listOf(
        T(CORE_TOPIC, "Parole base", "⭐", WordClass.ALTRO,
            "io 🙋 #P; tu 👉 #P; noi 👫 #P; voglio 🤲 #A; non voglio 🙅 #A; mi piace 👍 #A; non mi piace 👎 #A; " +
            "fare 🛠️ #A; andare 🚶 #A; avere 🫴 #A; essere 🧍 #A; posso? 🙏 #Q; aiuto 🆘 #S; ancora 🔁 #D; basta ✋ #S; " +
            "finito ✅ #D; sì ✔️ #S; no ❌ #S; non 🚫; qui 📍; più ➕; altro 🔄; dove? 📌 #Q; cosa? ❓ #Q; chi? 🧐 #Q; " +
            "aspetta ⏳ #S; guarda 👀 #A; dammi 🤝 #A; questo 👇; quello 👉"),
        T("saluti", "Saluti e cortesia", "👋", WordClass.SOCIALE,
            "ciao 👋; buongiorno 🌅; buonasera 🌆; buonanotte 🌙; arrivederci 🙋; a dopo ⏰; grazie 🙏; prego 😊; " +
            "per favore 🥺; scusa 😔; permesso 🚪; bravo 👏; evviva 🎉; va bene 👌; auguri 🎁; benvenuto 🤗; " +
            "salute 🤧; buon appetito 🍽️; come stai? 🙂; sto bene 😄"),
        T("bisogni", "Bisogni e richieste", "🙋", WordClass.SOCIALE,
            "ho fame 😋; ho sete 🥤; devo andare in bagno 🚽; ho sonno 😴; mi fa male 🤕; ho freddo 🥶; ho caldo 🥵; " +
            "voglio una pausa ⏸️; voglio stare da solo 🧘; non capisco 🤷; ripeti per favore 🔁; aiutami 🆘; " +
            "sono pronto 👍; ho finito ✅; voglio giocare 🧸; voglio andare a casa 🏠; voglio un abbraccio 🤗; " +
            "è troppo rumoroso 🔊; mi annoio 🥱; voglio cambiare 🔄; voglio uscire 🚪; non mi sento bene 🤒"),
        T("domande", "Domande", "❓", WordClass.DOMANDE,
            "chi? 🧐; cosa? ❓; dove? 📌; quando? ⏰; perché? 🤔; come? 🛠️; quanto? 🔢; quale? 👆; " +
            "posso? 🙏; che ore sono? 🕒; cosa facciamo? 🗓️; dove andiamo? 🗺️; chi viene? 🚪; cos'è? 🔍"),
        T("persone", "Persone", "👨‍👩‍👧", WordClass.PERSONE,
            "mamma 👩; papà 👨; fratello 👦; sorella 👧; nonno 👴; nonna 👵; zio 🧔; zia 👱‍♀️; cugino 🧒; neonato 👶; " +
            "bambino 👦; bambina 👧; amico 🧑; amica 👩‍🦰; maestra 👩‍🏫; maestro 👨‍🏫; dottore 👨‍⚕️; dottoressa 👩‍⚕️; " +
            "infermiere 🧑‍⚕️; logopedista 🗣️; terapista 🤝; poliziotto 👮; pompiere 🧑‍🚒; cuoco 🧑‍🍳; " +
            "autista 🧑‍✈️; contadino 🧑‍🌾; famiglia 👨‍👩‍👧‍👦; tutti 👥"),
        T("emozioni", "Emozioni", "😊", WordClass.DESCRIZIONI,
            "felice 😀; contento 😊; triste 😢; arrabbiato 😠; paura 😨; sorpreso 😲; stanco 😴; annoiato 🥱; " +
            "nervoso 😬; calmo 😌; imbarazzato 😳; orgoglioso 😎; geloso 😒; preoccupato 😟; innamorato 😍; " +
            "malato 🤒; confuso 😕; timido 🙈; disgustato 🤢; divertito 😂"),
        T("routine", "Routine e igiene", "🪥", WordClass.AZIONI,
            "svegliarsi ⏰; alzarsi 🛏️; lavarsi 🧼; lavare le mani 👐; lavare i denti 🪥; fare la doccia 🚿; " +
            "fare il bagno 🛁; vestirsi 👕; spogliarsi 🩲; pettinarsi 🪮; asciugarsi 🧻; andare in bagno 🚽; " +
            "fare colazione 🥐; pranzare 🍝; cenare 🍲; fare i compiti 📝; riposare 🛋️; dormire 🛌; " +
            "mettere le scarpe 👟; prendere la medicina 💊"),
        T("azioni", "Azioni", "🏃", WordClass.AZIONI,
            "mangiare 🍴; bere 🥤; venire 🫵; correre 🏃; saltare 🦘; camminare 🚶; sedersi 🪑; aprire 📂; " +
            "chiudere 📁; prendere 🫳; dare 🤲; mettere 📥; togliere 📤; guardare 👀; ascoltare 👂; parlare 🗣️; " +
            "cantare 🎤; ballare 💃; disegnare 🖍️; colorare 🎨; leggere 📖; scrivere ✍️; giocare 🎲; aiutare 🤝; " +
            "aspettare ⏳; finire 🏁; cominciare ▶️; cercare 🔍; trovare 🎯; comprare 🛒; spingere 🫸; tirare 🫷; " +
            "lanciare 🤾; abbracciare 🤗; baciare 😘; ridere 😂; piangere 😭; pensare 💭; volere 🙏; potere 💪; " +
            "cucinare 🍳; pulire 🧽; riordinare 🧺; nuotare 🏊; telefonare 📞; costruire 🧱; rompere 💥; tagliare ✂️"),
        T("cibo", "Cibo", "🍝", WordClass.COSE,
            "pane 🍞; pasta 🍝; pizza 🍕; riso 🍚; carne 🥩; pollo 🍗; pesce 🐟; uovo 🥚; prosciutto 🥓; " +
            "formaggio 🧀; insalata 🥗; patate 🥔; patatine 🍟; zuppa 🍲; panino 🥪; hamburger 🍔; hot dog 🌭; " +
            "biscotto 🍪; torta 🍰; gelato 🍦; cioccolato 🍫; caramella 🍬; cereali 🥣; cornetto 🥐; burro 🧈; " +
            "miele 🍯; sale 🧂; popcorn 🍿; lecca lecca 🍭; ciambella 🍩; budino 🍮; spaghetti 🍜"),
        T("frutta", "Frutta e verdura", "🍎", WordClass.COSE,
            "mela 🍎; pera 🍐; banana 🍌; arancia 🍊; uva 🍇; fragola 🍓; ciliegia 🍒; pesca 🍑; anguria 🍉; " +
            "melone 🍈; ananas 🍍; kiwi 🥝; limone 🍋; cocco 🥥; mirtilli 🫐; pomodoro 🍅; carota 🥕; broccoli 🥦; " +
            "mais 🌽; peperone 🫑; melanzana 🍆; cetriolo 🥒; funghi 🍄; cipolla 🧅; aglio 🧄; avocado 🥑; " +
            "lattuga 🥬; piselli 🫛; castagne 🌰; arachidi 🥜"),
        T("bevande", "Bevande", "🥤", WordClass.COSE,
            "acqua 💧; latte 🥛; succo 🧃; tè 🍵; caffè ☕; cioccolata calda ☕; bibita 🥤; frullato 🥤; " +
            "spremuta 🍊; tisana 🫖; ghiaccio 🧊; cannuccia 🥤"),
        T("cucina", "Pasti e cucina", "🍽️", WordClass.COSE,
            "colazione 🥐; pranzo 🍽️; merenda 🍪; cena 🍲; piatto 🍽️; bicchiere 🥛; tazza ☕; forchetta 🍴; " +
            "cucchiaio 🥄; coltello 🔪; tovagliolo 🧻; pentola 🍲; padella 🍳; bottiglia 🍼; ciotola 🥣; " +
            "frigorifero 🧊; tavola 🍽️; cucina 🍳"),
        T("corpo", "Corpo", "🧍", WordClass.COSE,
            "testa 🙆; capelli 💇; occhi 👀; orecchie 👂; naso 👃; bocca 👄; denti 🦷; lingua 👅; braccio 💪; " +
            "mano ✋; dita 🖐️; pancia 🫃; gamba 🦵; piede 🦶; cuore ❤️; cervello 🧠; ossa 🦴; unghie 💅; " +
            "faccia 🙂; sedere 🍑"),
        T("salute", "Salute", "🩺", WordClass.COSE,
            "male 🤕 #D; febbre 🤒 #D; tosse 😷 #D; raffreddore 🤧 #D; vomito 🤮 #D; ferita 🩹; cerotto 🩹; " +
            "medicina 💊; puntura 💉; termometro 🌡️; ospedale 🏥; ambulanza 🚑; dottore 👨‍⚕️ #P; " +
            "mal di testa 🤯 #D; mal di pancia 🫃 #D; mal di denti 🦷 #D; stanco 😴 #D; sangue 🩸; occhiali 👓; " +
            "carrozzina 🦽; stampelle 🩼; apparecchio acustico 🦻"),
        T("vestiti", "Vestiti", "👕", WordClass.COSE,
            "maglietta 👕; maglione 🧶; pantaloni 👖; pantaloncini 🩳; vestito 👗; calze 🧦; scarpe 👟; " +
            "stivali 👢; ciabatte 🩴; giacca 🧥; cappello 🧢; sciarpa 🧣; guanti 🧤; mutande 🩲; costume 👙; " +
            "pigiama 🛌; occhiali 👓; zaino 🎒; borsa 👜; ombrello ☂️; cintura 🪢; bottone 🔘"),
        T("casa", "Casa", "🏠", WordClass.COSE,
            "casa 🏠; camera 🛏️; letto 🛏️; divano 🛋️; sedia 🪑; porta 🚪; finestra 🪟; bagno 🚽; doccia 🚿; " +
            "vasca 🛁; specchio 🪞; luce 💡; chiavi 🔑; telefono 📞; televisione 📺; computer 💻; tablet 📱; " +
            "scopa 🧹; spazzatura 🗑️; sapone 🧼; carta igienica 🧻; spazzolino 🪥; pettine 🪮; lampada 🪔; " +
            "orologio 🕰️; giardino 🌷; balcone 🪴; scale 🪜"),
        T("scuola", "Scuola", "🏫", WordClass.COSE,
            "scuola 🏫; classe 🧑‍🏫; zaino 🎒; libro 📖; quaderno 📓; matita ✏️; penna 🖊️; colori 🖍️; " +
            "forbici ✂️; righello 📏; compiti 📝; ricreazione 🛝; mensa 🍽️; palestra 🤸; numeri 🔢; lettere 🔤; " +
            "musica 🎵; computer 💻; lavagna 🧑‍🏫; astuccio 👝; diario 📔; gita 🚌; compagni 👫 #P"),
        T("giochi", "Giochi", "🧸", WordClass.COSE,
            "palla ⚽; pallone 🏀; peluche 🧸; bambola 🪆; macchinina 🚗; trenino 🚂; costruzioni 🧱; puzzle 🧩; " +
            "carte 🃏; dadi 🎲; videogioco 🎮; palloncino 🎈; bolle di sapone 🫧; scivolo 🛝; aquilone 🪁; " +
            "cartoni animati 📺; disegno 🎨; bicicletta 🚲; monopattino 🛴; pattini 🛼; nascondino 🙈; " +
            "altalena 🎠; robot 🤖; trottola 🪀; pistola ad acqua 🔫; sabbia 🏖️"),
        T("animali", "Animali", "🐶", WordClass.COSE,
            "cane 🐶; gatto 🐱; pesce 🐟; uccello 🐦; cavallo 🐴; mucca 🐮; maiale 🐷; pecora 🐑; capra 🐐; " +
            "gallina 🐔; pulcino 🐥; coniglio 🐰; topo 🐭; criceto 🐹; tartaruga 🐢; rana 🐸; serpente 🐍; " +
            "leone 🦁; tigre 🐯; elefante 🐘; giraffa 🦒; scimmia 🐵; orso 🐻; panda 🐼; zebra 🦓; " +
            "coccodrillo 🐊; delfino 🐬; balena 🐳; squalo 🦈; farfalla 🦋; ape 🐝; ragno 🕷️; lumaca 🐌; " +
            "formica 🐜; coccinella 🐞; dinosauro 🦕; pinguino 🐧; gufo 🦉; papera 🦆; polpo 🐙; volpe 🦊; lupo 🐺"),
        T("luoghi", "Luoghi", "🗺️", WordClass.COSE,
            "casa 🏠; scuola 🏫; parco 🌳; piscina 🏊; mare 🏖️; montagna ⛰️; città 🏙️; negozio 🏪; " +
            "supermercato 🛒; ristorante 🍽️; pizzeria 🍕; bar ☕; ospedale 🏥; farmacia 💊; chiesa ⛪; " +
            "cinema 🎬; biblioteca 📚; palestra 🏋️; stadio 🏟️; zoo 🦁; campagna 🌾; casa dei nonni 👵; " +
            "parco giochi 🛝; ambulatorio 🩺; stazione 🚉; aeroporto 🛫"),
        T("trasporti", "Trasporti", "🚗", WordClass.COSE,
            "macchina 🚗; autobus 🚌; treno 🚆; aereo ✈️; nave 🚢; barca ⛵; bicicletta 🚲; moto 🏍️; " +
            "camion 🚚; taxi 🚕; ambulanza 🚑; polizia 🚓; pompieri 🚒; elicottero 🚁; metropolitana 🚇; " +
            "trattore 🚜; razzo 🚀; a piedi 🚶; scuolabus 🚌; semaforo 🚦"),
        T("tempo", "Tempo e giorni", "🗓️", WordClass.DESCRIZIONI,
            "oggi 📅; domani ➡️; ieri ⬅️; adesso ⏱️; dopo ⏭️; prima ⏮️; presto 🐇; tardi 🐢; mattina 🌅; " +
            "pomeriggio 🌇; sera 🌆; notte 🌙; giorno ☀️; settimana 🗓️; lunedì 📅 #C; martedì 📅 #C; " +
            "mercoledì 📅 #C; giovedì 📅 #C; venerdì 📅 #C; sabato 📅 #C; domenica 📅 #C; compleanno 🎂 #C; " +
            "Natale 🎄 #C; Pasqua 🐣 #C; Carnevale 🎭 #C; vacanze 🏖️ #C; primavera 🌸 #C; estate 🌞 #C; " +
            "autunno 🍂 #C; inverno ⛄ #C"),
        T("meteo", "Meteo e natura", "🌤️", WordClass.COSE,
            "sole ☀️; pioggia 🌧️; nuvole ☁️; neve ❄️; vento 💨; temporale ⛈️; arcobaleno 🌈; nebbia 🌫️; " +
            "luna 🌙; stelle ⭐; albero 🌳; fiore 🌸; erba 🌱; foglia 🍃; sasso 🪨; fuoco 🔥; onde 🌊; terra 🌍; " +
            "caldo 🥵 #D; freddo 🥶 #D"),
        T("colori", "Colori", "🎨", WordClass.DESCRIZIONI,
            "rosso 🔴; arancione 🟠; giallo 🟡; verde 🟢; blu 🔵; azzurro 🩵; viola 🟣; rosa 🩷; marrone 🟤; " +
            "nero ⚫; bianco ⚪; grigio 🩶"),
        T("numeri", "Numeri e forme", "🔢", WordClass.DESCRIZIONI,
            "uno 1️⃣; due 2️⃣; tre 3️⃣; quattro 4️⃣; cinque 5️⃣; sei 6️⃣; sette 7️⃣; otto 8️⃣; nove 9️⃣; " +
            "dieci 🔟; tanti 🔢; pochi 🤏; nessuno 0️⃣; cerchio ⭕ #C; quadrato 🟥 #C; triangolo 🔺 #C; " +
            "stella ⭐ #C; cuore ❤️ #C; rombo 🔷 #C"),
        T("descrizioni", "Descrizioni", "📏", WordClass.DESCRIZIONI,
            "grande 🐘; piccolo 🐭; alto 🦒; basso 🐾; lungo 📏; corto ✂️; buono 😋; cattivo 🤢; bello 😍; " +
            "brutto 😖; pulito ✨; sporco 🟫; nuovo 🆕; vecchio 🕰️; veloce 🐇; lento 🐢; pieno 🥛; vuoto 🫙; " +
            "rotto 💔; facile 👌; difficile 🧗; giusto ✅; sbagliato ❌; morbido ☁️; duro 🪨; bagnato 💦; " +
            "asciutto 🌞; rumoroso 🔊; silenzioso 🤫; aperto 🔓; chiuso 🔒; caldo 🔥; freddo 🧊; dolce 🍬; " +
            "salato 🧂; piccante 🌶️"),
        T("posizioni", "Posizioni", "↕️", WordClass.ALTRO,
            "sopra ⬆️; sotto ⬇️; dentro 📥; fuori 📤; vicino 🤏; lontano 🔭; davanti ⏩; dietro ⏪; " +
            "a destra ➡️; a sinistra ⬅️; qui 📍; lì 👉; su 🔼; giù 🔽; in mezzo ⏺️; accanto ↔️"),
        T("piccole", "Parole piccole", "🔤", WordClass.ALTRO,
            "il 🔤; la 🔤; lo 🔤; i 🔤; le 🔤; gli 🔤; un 🔤; una 🔤; e ➕; o 🔀; con 🤝; senza 🚫; per 🎯; " +
            "di 🔗; a ➡️; da ⬅️; in 📥; su 🔼; anche ➕; molto 💯; poco 🤏; tutto 🌐; niente 🚫; ma ✋; " +
            "perché 💡; mio 🙋 #P; tuo 👉 #P; suo 👆 #P"),
        T("sport", "Sport", "⚽", WordClass.COSE,
            "calcio ⚽; basket 🏀; pallavolo 🏐; tennis 🎾; nuoto 🏊; ciclismo 🚴; danza 💃; ginnastica 🤸; " +
            "corsa 🏃; sci ⛷️; rugby 🏉; karate 🥋; equitazione 🏇; pattinaggio ⛸️; arrampicata 🧗; bowling 🎳"),
        T("feste", "Feste e musica", "🎉", WordClass.COSE,
            "festa 🎉; regalo 🎁; torta 🎂; candeline 🕯️; palloncini 🎈; musica 🎵; canzone 🎶; chitarra 🎸; " +
            "pianoforte 🎹; tamburo 🥁; trombetta 🎺; violino 🎻; microfono 🎤; maschera 🎭; fuochi d'artificio 🎆; " +
            "Babbo Natale 🎅 #P; albero di Natale 🎄; uovo di Pasqua 🥚; zucca di Halloween 🎃; foto 📷"),
    )

    private fun cls(code: Char): WordClass? = when (code) {
        'P' -> WordClass.PERSONE; 'A' -> WordClass.AZIONI; 'D' -> WordClass.DESCRIZIONI
        'C' -> WordClass.COSE; 'S' -> WordClass.SOCIALE; 'Q' -> WordClass.DOMANDE; 'X' -> WordClass.ALTRO
        else -> null
    }

    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9àèéìòù]+"), "_").trim('_')

    val topics: List<Topic> by lazy {
        data.filter { it.id != CORE_TOPIC }.map { Topic(it.id, it.name, it.emoji) }
    }

    val cards: List<Card> by lazy {
        data.flatMap { t ->
            t.items.split(';').mapNotNull { raw ->
                var item = raw.trim()
                if (item.isEmpty()) return@mapNotNull null
                var wc = t.cls
                val hash = item.lastIndexOf(" #")
                if (hash > 0 && hash == item.length - 3) {
                    cls(item.last())?.let { wc = it }
                    item = item.substring(0, hash).trim()
                }
                val sp = item.lastIndexOf(' ')
                if (sp <= 0) return@mapNotNull null
                val label = item.substring(0, sp).trim()
                val emoji = item.substring(sp + 1).trim()
                Card("r_${t.id}_${slug(label)}", t.id, label, "", emoji, null, wc)
            }
        }.distinctBy { it.id }
    }
}
