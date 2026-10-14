# Medicatie Tracker voor Android

Een Android-app om dagelijkse medicatie en taken bij te houden, met herinneringen en een logboek.

## Wat de app doet

- **Vandaag**: alle medicatie en taken van vandaag, op tijd gesorteerd. Tik op *Genomen/Gedaan* of
  *Overslaan*. Te late items worden rood. Een vergissing herstel je met *Ongedaan maken*.
- **Meldingen**: op het ingestelde tijdstip krijg je een melding met de knoppen *Genomen/Gedaan* en
  *Overslaan*. Vink je niets af, dan komt de melding elke X minuten terug (per item instelbaar), tot
  middernacht. Herinneringen blijven werken na een herstart van de telefoon.
- **Geschiedenis**: per dag wat je gedaan, overgeslagen of gemist hebt, het percentage per item en
  het aantal dagen op rij dat alles compleet was. Tik op een regel om achteraf iets af te vinken.
  Met het deel-icoon exporteer je alles als CSV-bestand (te openen in Excel).
- **Prestaties**: heb je alles afgevinkt, dan groeit je reeks. Bij 7, 30 en 90 dagen op rij krijg
  je een felicitatie, en als je doorgaat ook elke volgende week en bij 180 en 365 dagen. In
  Geschiedenis zie je je badges, je huidige reeks en je beste reeks ooit.
- **Sterren**: rechtsboven zie je hoeveel dagen je reeks is. Vanaf 1 dag staat daarnaast een gouden
  vinkje, dat bij 7 dagen een ster wordt: brons vanaf 7 dagen, zilver vanaf
  30, goud vanaf 90 en blauw vanaf 365. Met een ster kleurt de app goud, zonder ster grijs. Wordt
  een ster vervangen door een hogere of breekt je reeks, dan gaat hij naar je sterrencollectie
  (tik op de ster, of via Beheren).
- **Blobje**: je eigen virtual pet in 16-bit stijl, met een naam die je zelf kiest. Elke keer dat je
  iets afvinkt verdien je basiseten (brood, een drankje of een wortel; één keer per item per dag).
  Heeft je blobje een dag (24 uur) niets gegeten, dan krijgt hij honger: hij wordt rood en
  verdrietig, ook in het tabblad onderaan. Doodgaan kan niet; voeren maakt hem weer blij. Elke nieuw
  verdiende ster levert muntjes op: brons 10, zilver 25, goud 50, blauw 200. Daarmee koop je in de
  winkel lekker eten (pizza, patat, macaroni, pannenkoek) en accessoires: brillen, zonnebrillen,
  hoeden, sjaaltjes en de gouden pollepel van PKU research. Je start met 20 muntjes.
- **Beheren** (zijmenu, knop linksboven): items toevoegen, wijzigen, tijdelijk uitzetten of
  verwijderen (de geschiedenis blijft bewaard). Per item stel je in of *Overslaan* je reeks breekt.
  Via *Testmelding sturen* controleer je of meldingen werken.

Bij de eerste start staat het schema uit de oude web-app er al in (Lophlex 08:00, 12:00 en 18:00,
calciumtabletten om 20:00). Alle gegevens blijven op je telefoon.

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
- Lettertype *Press Start 2P* door CodeMan38, onder de SIL Open Font License 1.1
  (`licenses/OFL-PressStart2P.txt`).
