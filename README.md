================================================================================
# BinanceBoosterBot Anleitung & Notizen
================================================================================

# Binance API Crypto Trading Bot über Binance Rest API.


================================================================================
Bot Beschreibung:
================================================================================
Im prinzip suche ich nach kurz sprüngen nach oben (boost innerhalb von x minuten) oder mit minus werten nach unten (fallender kurz in den letzten x minuten)
und steige dann mit limit buy (leicht überhöht damit ich trade erwische * 1.0028) ein und verkaufe mit x % sellpoint wieder oder fall wir weiter fallen market sell instantly
wenn unter schwellwert (stoploss) fällt.

Dementsprechend wichtig wäre es rauszufinden welches boost oder drop wert signifikant ist um am besten mitzuschneiden statistisch. In den Monaten wo ich getestet habe war immer 2.7-2.9 recht gut, findet aber viel was
weiter nach unten sackt oder nicht mehr weiter boostet ... Höhere werte liefern weniger trades könnten aber statitisch gesehen sicherer sein, das wird nur die zukunft zeigen in findings.log ...


================================================================================
Das Analyse Script - findings.properties
================================================================================
Alle findings werden als "treffer" mal am PI in findings.properties gespeichert, egal ob live trading aktiv ist oder nicht.
Es ist ein property file damit man schneller beim simulieren von Ergebnissen in der IDE ein- und auskommentieren kann.
Mit dem Analyse Skript "AnalyseFindings.java" kann man dann jederzeit die findings nachträglich einlesen und mit den parameters des bots evaluieren "was-wäre-wenn" gewesen. 
Er parsed sich alle candlesticks pro finding reverse durch zum kaufzeitpunkt und schaut ob win oder stoploss mit den parametern die man setzt eingetreten wäre oder nicht und errechnet eine summe über alle trades.
Damit kann ich jederzeit simulieren was wäre gewesen wenn ich andere parameter verwendet hätte im Bot und so lange optimieren bis ein langfristiges plus rauskommt, man braucht halt viele daten.

Achtung: wenn das file mal über 3000 zeilen hat muss man eine thread bremse einbauen oder max threads auf um die 30-40 setzen sonst gibts wegen der candlestick massendaten einen binance block für einige minuten bis die api wieder geht!

======================================================
Wie startet man den Krypto BOT zum traden:
======================================================

A) lokal aus Eclipse: FindBooster.jar anwerfen

B) am PI: alles automatisiert rennt am host: dil@raspberrypi (user dil oder root)
raspberrypi user und passwörter: dil/dil, root/root

./kill.sh - stoppt den bot (killt ihn am pi)
./start.sh - startet den bot
./status.sh - schauen ob java prozess rennt (im "screen" am pi -> googeln, braucht man um prozess am leben zu erhalten nach start per script)
./watch.sh - gibt run.log aus
./find.sh - macht eine analyse aufgrund findings.log (achtung wenn zuviel daten drin 3000 einträge -> Binance API block und möglicherweise IP sperre!)

log files:
run.log -> der bot selbst
alive.log -> alive log (alle 20 minuten - watch time frame)
result.log -> summary aller trades ob es ein win oder loss war
findings.log -> sämtliche findings werden getracked unabhängig davon ob wir traden oder nicht

======================================================
DEPLOY SCRIPT FÜR RASPBERRYPI @HOME:
======================================================

1.) Um den aktuellsten maven bot auf den PI zu pushen gibt es ein Script welches alles macht automatisch vom deploy, kill und restart des bots am PI. Doppelklick und paar sekunden warten.
C:\develop\BinanceBoosterBot\pushbot.bat

2.) Geht das fenster zu kann man sich per ssh am PI einloggen (taskbar verknüpfung mit user dil) und das watch script starten oder run.log ausgeben. ./watch.sh würde mit monitoren was er macht.
alive.log zeigt alle 20 minuten an ob er noch rennt und hält binance api alive und macht paar sachen wie balance re-calculation, usw.

Jeder der Java kann kann den bot starten und debuggen, erweitern, fixen, etc.
Parameter Beschreibung ist im environment.properties so gut geht als einzeiler, ums zu verstehen kann sein das man einfach im code lesen muss was gedacht war mit dem parameter falls er ned sprechend genug gewählt wurde.
Das meiste ist hoffentlich logisch und selbst erklärend, folgende parameter kurz erklärt weil sie sollten gegen schlechte trades absichern und damit wird man spielen müssen bzw. erweitern:


# =================== PARAMETER QUICK ANLEITUNG ===================

Ein token mit -30 im daily dumpt zu hard da reinsteigen = verlust, 
gleich wenn etwas im daily schon + 80% hat kanns fast nur mehr nach unten gehen
Damit kann man eine range angeben in welcher wir tokens fürs trading akzeptieren und im booster/dumpster bot suchen:

# daily range for booster - Welchen wert im daily soll ein token haben damit wir den trade eingehen. 
max_daily=12.5
min_daily=-18

Wenn BTC im Roten ist, zieht er meist den ganzen markt runter, sprich minus im BTC zieht alles down, damit sollte dieser parameter abfangen und nur trades eingehen wenn BTC einen mindest wert im daily hat und nicht gerade dumped!!

# btc daily 24hTicker check
btcDaily.active=true
btcDaily.min=0.0

in 2-3 monaten gibt es gefühlt 1 wochenende in dem der markt gut performed, drum wochenends sollte er einfach nichts eingehen, 6,7,1 sind gute settings. ab sonntag in der nacht auf Montag ist er eh wieder dabei zum wochenstart

# skipDays/weekend check (do not trade on these days!)
# Mo(2) Di(3), Mi(4), Do(5), Fr(6), Sa(7) So(1)
skipDays.active=true
skipDays.days=6,7,1

# RSI Indikator
RSI (top im findings.log zum testen) range in welcher wir den trad akzeptiert hätten. wichtig hier, nur zur laufzeit errechnen und speichern wir den RSI (15min, 30min, 1h, 4h, 1D, usw.) damit hab ich leider akuell nur  RSIs von aktuellen findings in der hand. besser wäre a) zur laufzeit alle errechnen, kostet aber zeit die nur hat wenn nicht getraded wird grad!! oder im skript nach einbauen, aber dann IP sperre wegen der load der candlestick daten, also bremse einbauen!
datapoints und periods nie mehr ändern, je mehr daten desto genauer ist die berechnung und desto näher an der Binance RSI berechnung! (die keiner offiziell wo hat wohl gemerkt!! Mein RSI hier ist eine nachberechnung mit library und chatGPT mix, welche hart erarbeitet wurde...
nicht 100% aber sehr sehr nahe dran am binance RSI und genau das was ich wollte.

Somit bleibt nur spielen mit dem interval. ich hatte 15min und 1h im test, besser wäre noch zu schauen was ist im daily und weekly passiert und dann entsprechend die boost erkennung einschränken. Kann A) mehr trades eingehen am 15min und 
dennoch per daily, weekly dann den Token anschauen und entscheiden ob ma wirklich diesen trade eingehen sollte aufgrund daily und weekly daten.

# RSI indicator check
RSI.active=true
RSI.interval=1h
RSI.periods=14
RSI.datapoints=1000
RSI.max=60
RSI.min=0

======================================================
MARKT CRASH PROTECTION:
======================================================

Wenn der markt crashed verliert man maximal die aktuell offenen trades! Werden x trades in serie mit loss verkauft wird das live trading für x stunden pausiert:

# if we have a loosing streak and e.g. market dumps, sleep for x hours before starting to trade again!
marketCrash.active=true
marketCrash.maxLossInARow=6
marketCrash.cooldownInHours=6



