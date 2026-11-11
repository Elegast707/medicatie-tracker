# Medicatie Tracker voor Android

Een Android-app om dagelijkse medicatie en taken bij te houden, met herinneringen en een logboek.

## Wat de app doet

- **Vandaag**: alle medicatie van vandaag, op tijd gesorteerd, en daaronder de taken die vandaag
  staan. Tik op *Genomen* (of *Overslaan*) bij een medicijn en op *Gedaan* bij een taak. Afgevinkt? Dan springt er een groen vinkje tevoorschijn. Te late items worden rood. Een vergissing herstel je met *Ongedaan maken*.
- **Meldingen**: op het ingestelde tijdstip krijg je een melding met de knoppen *Genomen* en
  *Overslaan*. Vink je niets af, dan komt de melding elke X minuten terug (per medicijn instelbaar), tot
  middernacht. Ook elke taak heeft een tijdstip (standaard 12:00, per taak aan te passen): dan krijg je
  een melding met de knop *Gedaan*, en daarna elk uur opnieuw tot je hem afvinkt, tot middernacht.
  Herinneringen blijven werken na een herstart van de telefoon.
- **Medicijnen** en **Taken**: twee pagina's die er hetzelfde uitzien, de een voor medicijnen en de
  ander voor taken. Bovenaan staan je reeks en badges, daaronder de meter met hoeveel procent je in de
  laatste 7, 30 of 90 dagen hebt afgevinkt, en een maandkalender: elke dag laat zien hoeveel er stond
  en hoeveel je hebt afgevinkt (groen = alles gedaan, rood = iets blijven liggen). Tik op een dag om
  die dag te zien en achteraf iets af te vinken. Op Medicijnen exporteer je alles als CSV-bestand (te
  openen in Excel), en helemaal onderaan voeg je medicijnen toe en stel je hun tijdstip en meldingen in.
  Op Taken voeg je taken toe met +: eenmalig (op een gekozen datum) of terugkerend: elke dag, op
  gekozen weekdagen of elke X dagen. Pas je het schema van een taak aan, dan geldt dat vanaf vandaag;
  eerdere dagen blijven kloppen.
- **Takenreeks**: taken hebben een eigen reeks en eigen sterren, in kalenderweken en -maanden. Een
  week (maandag t/m zondag) is compleet als alle taken die erin stonden gedaan zijn; dagen zonder taken
  doen niet mee, en een week zonder taken breekt de reeks niet maar geeft ook geen badge. Eén gemiste
  taak breekt de reeks. Badges: een week, een maand, 3 maanden, een half jaar en een jaar op rij.
  Taken verdienen geen sterren maar medailles met een vinkje: brons voor een week, zilver voor een maand, goud voor 3 maanden, blauw voor een jaar.
  In het tabblad Taken staat rechtsboven je takenreeks in weken; tik erop voor je medailles. Elke
  afgevinkte taak levert eten op voor je blobje, en elke nieuwe medaille muntjes.
- **Prestaties**: heb je alles afgevinkt, dan groeit je reeks. Bij 7, 30 en 90 dagen op rij krijg
  je een felicitatie, en als je doorgaat ook elke volgende week en bij 180 en 365 dagen. Op
  Medicijnen zie je je badges, je huidige reeks en je beste reeks ooit.
- **Sterren**: rechtsboven zie je hoeveel dagen je reeks is. Vanaf 1 dag staat daarnaast een gouden
  vinkje, dat bij 7 dagen een ster wordt: brons vanaf 7 dagen, zilver vanaf
  30, goud vanaf 90 en blauw vanaf 365. Met een ster kleurt de app goud, zonder ster in de kleur van het blobje (groen, rood als het honger heeft). Wordt
  een ster vervangen door een hogere of breekt je reeks, dan gaat hij naar je sterrencollectie
  (tik op de ster of op het blok met je badges).
- **Blobje**: je eigen virtual pet in 16-bit stijl, met een naam die je zelf kiest. Elke keer dat je
  iets afvinkt verdien je basiseten (brood, een drankje of een wortel; één keer per item per dag).
  Het blobje heeft twee balken: **buik** en **blij**. De buik loopt in een dag (24 uur) leeg; dan
  krijgt hij honger en wordt hij rood, ook in het tabblad onderaan. Ook de blijheid loopt in 24 uur
  van vol naar leeg, en twee keer zo snel als hij honger heeft. Aai je blobje door erop te tikken of
  eroverheen te vegen: dat kan altijd en geeft elke keer 10 punten. Voeren doe je door op het eten
  te tikken of het naar je blobje te slepen. Met honger kun je hem zo
  hooguit half blij maken; dan wil hij eerst eten. Eten geeft ook 15 punten blijheid. Doodgaan kan niet. Elke nieuw
  verdiende ster levert muntjes op: brons 10, zilver 25, goud 50, blauw 200. Daarmee koop je in de
  winkel lekker eten (pizza, patat, macaroni, pannenkoek) en accessoires: brillen, zonnebrillen,
  hoeden, sjaaltjes en de gouden pollepel van PKU research. Je start met 20 muntjes.
- **Medicijnen beheren** (onderaan de pagina Medicijnen): medicijnen toevoegen, wijzigen, tijdelijk uitzetten of
  verwijderen (de geschiedenis blijft bewaard). Per medicijn stel je in of *Overslaan* je reeks breekt.
  Via *Testmelding sturen* controleer je of meldingen werken.

Een nieuwe installatie begint leeg: je voegt zelf je medicijnen toe onderaan de pagina Medicijnen en je taken in het tabblad Taken. Bij een
update blijven je bestaande items en geschiedenis gewoon staan. Alle gegevens blijven op je telefoon.

## Installeren op je telefoon

1. Ga op je telefoon naar de repository op GitHub, dan **Releases**, en download het nieuwste
   `medicatie-tracker-….apk`.
2. Open het bestand. Android vraagt eenmalig of je browser of bestandsbeheer apps mag installeren:
   sta dit toe.
3. Open de app en tik op **Meldingen toestaan**.
4. Tip: zet *batterijoptimalisatie* voor deze app uit (Instellingen › Apps › Medicatie Tracker ›
   Batterij › Onbeperkt). Sommige merken (Samsung, Xiaomi, Huawei, OnePlus) stellen meldingen
   anders uit; zie [dontkillmyapp.com](https://dontkillmyapp.com) voor jouw telefoon.

Een nieuwe versie installeer je op dezelfde manier: je gegevens blijven bewaard.

## Eenmalig: signeersleutel instellen

Android installeert een update alleen als die met dezelfde sleutel is ondertekend. Zonder vaste
sleutel moet je de app bij elke nieuwe versie eerst verwijderen, en dan raak je je geschiedenis kwijt.
Doe daarom eenmalig het volgende:

1. Ga op GitHub naar **Actions**, dan **Signeersleutel maken (eenmalig)**, en kies **Run workflow**.
2. Open de voltooide run en download onder *Artifacts* het bestand `signeersleutel`. Pak het zip-bestand uit.
3. Ga naar **Settings**, dan **Secrets and variables**, dan **Actions**, en maak twee *repository secrets* aan:
   - `SIGNING_KEYSTORE_BASE64`: de inhoud van `SIGNING_KEYSTORE_BASE64.txt`
   - `SIGNING_PASSWORD`: de inhoud van `SIGNING_PASSWORD.txt`
4. Verwijder het artifact bij de run (prullenbak-icoon) en de gedownloade bestanden.
5. Start de workflow **Android APK bouwen** opnieuw (*Run workflow*).

Maak de sleutel maar één keer aan: met een nieuwe sleutel kun je de bestaande app niet meer bijwerken.

## Techniek

- Kotlin, Jetpack Compose (Material 3), Room-database, minimaal Android 8.0 (API 26).
- Herinneringen via `AlarmManager.setExactAndAllowWhileIdle`. Vanaf Android 13 met de permissie
  `USE_EXACT_ALARM` (wordt automatisch verleend), op Android 12 met `SCHEDULE_EXACT_ALARM`.
- Na herstart, app-update en wijziging van tijd of tijdzone worden alle alarmen opnieuw gezet.
- Elke push bouwt via GitHub Actions een APK (`.github/workflows/android.yml`) en draait eerst de
  unit-tests. Builds van `main` worden een gewone release, andere branches een pre-release.

Zelf bouwen: open de map `android/` in Android Studio, of voer `./gradlew assembleRelease` uit.

### Pixelart en lettertype

- Alle pixelart van het blobje (Sweetie 16-palet) wordt gemaakt door `tools/generate_sprites.py`
  (`python3 tools/generate_sprites.py`, vereist Pillow) en staat in `app/src/main/res/drawable-nodpi/`.
  Een plaatje vervangen kan door een PNG met dezelfde naam en afmetingen neer te zetten
  (blobje en accessoires 32×32, eten 16×16, achtergrond 64×48).
- De hele app staat in 32-bit pixelstijl: pixelkaders, blokjesbalken en eigen pixelicoontjes.
  De icoontjes (`ic_px_*`), de sterren en het gouden vinkje zijn vector drawables uit
  `tools/generate_pixel_icons.py` (`python3 tools/generate_pixel_icons.py`, geen extra pakketten nodig).
- Koppen in *Press Start 2P* door CodeMan38, onder de SIL Open Font License 1.1
  (`licenses/OFL-PressStart2P.txt`).
- Gewone tekst in *Pixelify Sans* door Stefie Justprince, onder de SIL Open Font License 1.1
  (`licenses/OFL-PixelifySans.txt`).
