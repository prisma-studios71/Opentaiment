# Opentaiment – plan projektu

## 1. Wizja

Opentaiment to otwarty system infotainment do auta: muzyka, nawigacja, dane z auta (OBD-II),
pogoda, szybkie skróty – jeden spójny, ładny interfejs, bez reklam i bez śledzenia.

## 2. Ważne ograniczenie: czym jest (a czym nie jest) Android Auto

Android Auto to **powłoka Google** wyświetlana na ekranie auta. Aplikacje firm trzecich
nie mogą jej zastąpić ani rysować własnego, dowolnego UI. Dozwolone są tylko kategorie:

| Kategoria           | Jak się to robi                              |
|---------------------|----------------------------------------------|
| Media (audio)       | `MediaLibraryService` (Media3)               |
| Nawigacja           | Car App Library – `NavigationTemplate`       |
| POI (parkingi, ładowarki, stacje) | Car App Library – `PlaceListMapTemplate` |
| IoT                 | Car App Library – szablony list/siatki       |
| Wiadomości          | powiadomienia `MessagingStyle`               |
| Pogoda              | Car App Library (nowsze wersje API)          |

Wniosek: projekt ma **dwa tryby**:

1. **Tryb Android Auto** – Opentaiment dostarcza do AA moduły (media, nawigacja/POI, dashboard
   jako szablony). Wygląd narzuca Google, my dostarczamy treść i logikę.
2. **Tryb Head Unit** – na radiach z „gołym” Androidem (chińskie head unity) lub AAOS
   Opentaiment działa jako **pełny launcher** z własnym UI – tu mamy pełną swobodę.

Cała logika (media, OBD, nawigacja, ustawienia) siedzi we wspólnych modułach, więc oba tryby
korzystają z tego samego kodu.

## 3. Stack technologiczny

- **Kotlin**, coroutines + Flow
- **Jetpack Compose** (UI telefonu i launchera head unit)
- **androidx.car.app** (Car App Library) – ekrany Android Auto / AAOS
- **Media3** (ExoPlayer + MediaLibraryService) – odtwarzacz i integracja z AA
- **Hilt** – DI
- **Room + DataStore** – baza i ustawienia
- **MapLibre + OpenStreetMap** – mapy; routing: GraphHopper/OSRM (opcjonalnie offline)
- **Bluetooth/ELM327** – OBD-II
- Gradle version catalog, minSdk 26, targetSdk najnowszy

## 4. Struktura modułów

```
Opentaiment/
├── app-phone/          # aplikacja na telefon + wejście Android Auto (CarAppService, MediaService)
├── app-headunit/       # launcher na head unit / AAOS (HOME intent, tryb poziomy)
├── car/                # ekrany Car App Library (Screen, Session, szablony)
├── core/
│   ├── model/          # modele domenowe
│   ├── data/           # repozytoria, Room, DataStore
│   ├── ui/             # design system Compose (motywy, komponenty, tryb dzień/noc)
│   └── common/         # utilsy, dispatchery, logowanie
└── feature/
    ├── media/          # lokalne pliki, radio internetowe, podcasty, sterowanie innymi playerami
    ├── navigation/     # mapa, wyszukiwanie, routing, ulubione miejsca
    ├── vehicle/        # OBD-II: prędkość, obroty, temperatura, spalanie, kody błędów
    ├── dashboard/      # ekran główny z widgetami
    ├── weather/        # pogoda (Open-Meteo, bez klucza API)
    └── settings/       # ustawienia, motywy, profile kierowców
```

## 5. Funkcje

### MVP (v0.1)
- Ekran główny (head unit): zegar, pogoda, aktualnie grane, skróty do aplikacji
- Odtwarzacz: lokalne pliki + radio internetowe (Radio Browser API), widoczny w Android Auto
- Sterowanie zewnętrznymi playerami (Spotify/YT Music) przez `MediaSessionManager` (head unit)
- Szybkie uruchamianie nawigacji przez intent (Google Maps / Waze / OsmAnd)
- Tryb dzień/noc automatycznie

### v0.2
- OBD-II przez Bluetooth ELM327: wskaźniki na żywo, odczyt/kasowanie kodów DTC
- Dashboard z konfigurowalnymi widgetami
- POI w Android Auto: stacje paliw / ładowarki (OpenChargeMap, OSM Overpass)

### v0.3
- Własna nawigacja na MapLibre + routing (Car App Library `NavigationTemplate`)
- Mapy offline
- Profile kierowców, motywy

### Później
- Sterowanie głosem
- Statystyki tras i spalania
- Integracje IoT (brama garażowa, Home Assistant)
- Wtyczki/rozszerzenia

## 6. Zasady UX w aucie

- Duże cele dotyku (min. 76 dp na head unit), max 2–3 poziomy menu
- Zero zbędnych animacji w czasie jazdy, kontrast w nocy, brak jasnej bieli po zmroku
- Ograniczenia w ruchu (UX restrictions) – blokada klawiatury/długich list w trakcie jazdy
- Wszystko obsługiwalne kierownicą/pokrętłem (D-pad focus)

## 7. Dystrybucja i testy

- Android Auto: testy przez **Desktop Head Unit (DHU)**; publikacja w Google Play
  wymaga przejścia review jakości Android Auto (bez tego działa tylko w trybie deweloperskim AA).
- Head unit: APK z GitHub Releases / F-Droid.
- AAOS: emulator Automotive w Android Studio.
- CI: GitHub Actions – build, lint (detekt/ktlint), testy jednostkowe.

## 8. Licencja

Propozycja: **GPL-3.0** (otwartość, forki muszą zostać otwarte) albo **Apache-2.0**
(łatwiejsza współpraca z firmami). Do decyzji.

## 9. Najbliższe kroki

1. Szkielet projektu Gradle z modułami z pkt 4
2. Design system w `core/ui`
3. `feature/media` + integracja z Android Auto (najszybszy efekt „wow” w aucie)
4. Launcher head unit z ekranem głównym
5. OBD-II
