# Opentaiment

Otwarty system infotainment do samochodu dla Androida i Android Auto.

- 🚗 **Head unit / tablet** – pełnoekranowy launcher na tablet z Androidem (albo radio z Androidem). **← jesteśmy tutaj (v0.1)**
- 📱 **Telefon + Android Auto** – moduły wyświetlane na ekranie auta przez Android Auto (następny etap).

![Ekran główny](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_HomeScreenScreenshotTest_homeLandscapePlaying.png)

## Co już działa (v0.1 – tablet)

- Ekran główny: zegar, data, pogoda (Open-Meteo, bez klucza API, ostrzeżenie o śliskiej drodze)
- Sterowanie muzyką z **dowolnej** aplikacji (Spotify, YouTube Music, Tidal, radio…): okładka, tytuł, postęp, play/pauza/następny/poprzedni
- Skróty: nawigacja (Google Maps → Waze → OsmAnd), muzyka, telefon, Bluetooth, ustawienia
- Lista aplikacji z wyszukiwarką
- Tryb launchera (można ustawić jako ekran główny), ekran nie gaśnie, ukryte paski systemowe
- Układ poziomy i pionowy

## Screeny (v0.1)

| | |
|---|---|
| ![Gra muzyka](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_HomeScreenScreenshotTest_homeLandscapePlaying.png) | ![Pauza, noc](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_HomeScreenScreenshotTest_homeLandscapePaused.png) |
| Gra muzyka, zimno → ostrzeżenie o śliskiej drodze | Pauza, wieczór, bezchmurnie |
| ![Nic nie gra](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_HomeScreenScreenshotTest_homeLandscapeNothingPlaying.png) | ![Brak dostępu](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_HomeScreenScreenshotTest_homeLandscapeNoMediaAccess.png) |
| Nic nie gra | Pierwsze uruchomienie – trzeba dać dostęp do muzyki |
| ![Lista aplikacji](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_HomeScreenScreenshotTest_appDrawer.png) | ![Pion](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_HomeScreenScreenshotTest_homePortraitPlaying.png) |
| Lista aplikacji z wyszukiwarką | Tablet w pionie |

Screeny są generowane z kodu (Paparazzi, bez emulatora): `./gradlew :app-headunit:recordPaparazziDebug`

## Instalacja na tablecie

1. Pobierz APK z zakładki **Actions → ostatni build → artefakt `opentaiment-headunit-debug`**
   albo zbuduj sam: `./gradlew :app-headunit:assembleDebug`
   (plik: `app-headunit/build/outputs/apk/debug/app-headunit-debug.apk`).
2. Zainstaluj (`adb install app-headunit-debug.apk` albo skopiuj plik na tablet i otwórz).
3. Przy pierwszym uruchomieniu zezwól na lokalizację (pogoda dla Twojego miasta).
4. Kliknij **„Otwórz ustawienia”** na karcie muzyki i włącz Opentaiment w *Dostęp do powiadomień* – bez tego Android nie pozwala sterować muzyką z innych aplikacji.
5. (Opcjonalnie) Ustawienia → Aplikacje → Aplikacja domyślna → **Ekran główny → Opentaiment**, żeby tablet startował od razu w trybie auta.

## Struktura

```
app-headunit/      launcher na tablet / head unit
core/ui/           motyw i wspólne komponenty
feature/media/     sterowanie sesjami mediów
feature/weather/   pogoda (Open-Meteo)
feature/apps/      lista aplikacji i skróty
```

Testy: `./gradlew testDebugUnitTest` · zrzuty ekranu bez emulatora: `./gradlew :app-headunit:recordPaparazziDebug`

Plan i architektura: [docs/PLAN.md](docs/PLAN.md)
