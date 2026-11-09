# Opentaiment – plan projektu

## 1. Wizja

Opentaiment to **dodatek do Android Auto**, nie jego zamiennik. Muzyka i nawigacja zostają
w Android Auto (Spotify, Mapy, Waze…), a Opentaiment dokłada to, czego tam brakuje:
dane z auta przez OBD-II – spalanie, koszt, eco-score, moc, błędy, historię tras i zabawki
na tor. Bez reklam i bez śledzenia, wszystko zostaje na telefonie.

## 2. Jak to działa z Android Auto

Android Auto nie pozwala rysować nakładek na swoim ekranie. Aplikacje firm trzecich
pokazują się w nim jako osobne pozycje w menu aplikacji i rysują ekrany z gotowych
szablonów Google (Car App Library). Opentaiment tak właśnie działa:

```
 Telefon                                         Ekran auta (Android Auto)
┌──────────────────────────────┐   USB/Wi-Fi   ┌───────────────────────────────┐
│ app-phone (UI na telefonie)  │◄─────────────►│ Menu AA → ikona „Opentaiment”  │
│ car (CarAppService, ekrany AA)│               │ Jazda · Statystyki · Błędy ·  │
│        │                     │               │ Trasy · Wyścig · Połączenie    │
│   VehicleHub (jedno połączenie)               └───────────────────────────────┘
│        │ Bluetooth (SPP)     │
└────────┼─────────────────────┘
         ▼
   adapter ELM327 w gnieździe OBD-II auta
```

- Jedno połączenie z autem (`VehicleHub`) obsługuje jednocześnie UI telefonu i ekrany Android Auto.
- Usługa pierwszoplanowa (`VehicleService`) trzyma połączenie, gdy telefon leży zablokowany w uchwycie.
- Ekrany AA odświeżają się co 1 s (limit Android Auto na odświeżanie szablonów).
- Ostrzeżenia (przegrzanie, brak ładowania, mało paliwa) wyskakują jako alert w aucie i powiadomienie na telefonie.

Kategoria aplikacji w Android Auto: **IoT** (szablony list, siatki i paneli). Aplikacja spoza
Sklepu Play wymaga włączenia „Nieznanych źródeł” w ustawieniach programisty Android Auto.

**Tablet / radio z Androidem** – tryb dodatkowy: ten sam kod jako pełnoekranowy launcher
(zegar, pogoda, sterowanie muzyką, kafelki) dla aut bez Android Auto.

## 3. Stack technologiczny

- **Kotlin**, coroutines + Flow, **Jetpack Compose**
- **androidx.car.app 1.7** (Car App Library) – ekrany Android Auto
- Bluetooth SPP + protokół **ELM327** – OBD-II
- GPS z platformy (bez Google Play Services) – okrążenia i g-meter
- Testy: JUnit (logika), **Robolectric** (ekrany Android Auto), **Paparazzi** (zrzuty UI bez emulatora)

## 4. Moduły

```
app-phone/          aplikacja na telefon: zakładki, usługa w tle, powiadomienia
car/                ekrany Android Auto (CarAppService + szablony)
app-headunit/       launcher na tablet / radio z Androidem (tryb dodatkowy)
core/ui/            motywy, tryb nocny (wschód/zachód słońca), wspólne komponenty
feature/vehicle/    OBD-II, obliczenia, historia tras, alerty, wyścig, ekrany Compose
feature/media/      sterowanie muzyką (tablet)
feature/weather/    pogoda (tablet)
feature/apps/       lista aplikacji (tablet)
```

## 5. Co jest zrobione

**OBD-II**
- ✅ Spalanie chwilowe i średnie (PID 5E → MAF → MAP), wykrywanie odcięcia paliwa
- ✅ Moc, moment, przyspieszenie (szacowane z fizyki)
- ✅ Sprinty 0–100 i 80–120, ¼ mili z prędkością na mecie
- ✅ Historia tras (automatyczny zapis, podział po 3 min ze zgaszonym silnikiem), podsumowania tygodnia i miesiąca
- ✅ Koszt paliwa (cena za litr w ustawieniach)
- ✅ Eco-score 0–100 z podpowiedziami (gwałtowne przyspieszanie/hamowanie, wysokie obroty, postój na biegu jałowym)
- ✅ Alerty na żywo: przegrzanie, gorący olej, brak ładowania, za wysokie napięcie, słaby akumulator, mało paliwa
- ✅ Kody błędów (zapisane / oczekujące / stałe), opisy po polsku, kasowanie
- ✅ Freeze frame (parametry silnika w chwili błędu)
- ✅ Testy gotowości do badania spalin (benzyna i diesel)
- ✅ Wyścig: g-meter (boczne z GPS, wzdłużne z OBD), okrążenia GPS z linią mety

**Wygląd i wygoda**
- ✅ Automatyczny tryb nocny (czerwono-czarny, przyciemniony ekran) po zachodzie słońca
- ✅ 6 kolorów akcentu
- ✅ Ekran „Jazda” – jedna wielka liczba, strony przesuwane palcem
- ✅ Gesty: przesuwanie stron jazdy, przesunięcie karty muzyki zmienia utwór (tablet), przytrzymanie kafelka = edycja

**Aplikacje**
- ✅ Telefon + Android Auto
- ✅ Tablet: launcher z edytowalnymi kafelkami, pogodą i sterowaniem muzyką

## 6. Dalej

- Adaptery OBD na BLE (np. Vgate iCar Pro BLE, OBDLink CX)
- Automatyczne ponowne łączenie po zerwaniu połączenia
- Eksport tras do CSV
- Wersja w Sklepie Play (wymaga przeglądu jakości Android Auto i podpisu release)
- Licencja – do decyzji (GPL-3.0 albo Apache-2.0)
