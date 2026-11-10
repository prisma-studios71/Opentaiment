# Opentaiment

**Dodatek do Android Auto**, który pokazuje to, czego Android Auto nie ma: dane z auta przez OBD-II.
Spalanie, koszt jazdy, eco-score, moc, błędy silnika, historia tras i zabawki na tor – na telefonie
i na ekranie auta. Muzyka i nawigacja zostają w Android Auto, tak jak zawsze.

Bez reklam, bez kont, bez śledzenia – wszystko zostaje na telefonie.

![Telefon – jazda](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_drive.png)
![Telefon – statystyki](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_stats.png)
![Telefon – błędy](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_diagnostics.png)
![Telefon – trasy](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_trips.png)

## Jak to działa z Android Auto

Android Auto nie pozwala żadnej aplikacji rysować po swoim ekranie. Opentaiment dostaje więc
**własną ikonę w menu aplikacji Android Auto** i własne ekrany:

| Ekran w aucie | Co pokazuje |
|---|---|
| **Jazda** | prędkość, obroty, spalanie chwilowe i średnie, eco-score z podpowiedzią, koszt i dystans trasy, ostrzeżenia |
| **Statystyki** | moc i moment, przyspieszenie, temperatury, akumulator, poziom paliwa, trasa |
| **Diagnostyka** | Check Engine, kody błędów z opisami, gotowość do badania spalin, kasowanie błędów |
| **Trasy** | podsumowanie tygodnia i miesiąca, ostatnie jazdy |
| **Wyścig** | 0–100, 80–120, ¼ mili, okrążenia GPS, przeciążenia |
| **Połączenie** | połącz z adapterem / tryb demo / rozłącz |

Ostrzeżenia (przegrzanie, brak ładowania, mało paliwa…) wyskakują jako alert na ekranie auta
i powiadomienie na telefonie.

> Ekrany Android Auto rysuje sam Android Auto z gotowych szablonów Google, więc wyglądają
> „po androidowemu”, a nie jak na zrzutach z telefonu – i odświeżają się co sekundę.

## Co potrzebujesz

- Telefon z Androidem 8+ i auto z Android Auto (albo sam telefon w uchwycie)
- **Adapter ELM327 na Bluetooth** („kostka OBD”, ~30–80 zł) w gnieździe OBD-II
  (każda benzyna od 2001, diesel od 2004 w UE)

## Instalacja

1. Pobierz APK: **Actions → ostatni build → artefakt `opentaiment-phone-debug`**
   albo zbuduj: `./gradlew :app-phone:assembleDebug`
2. Zainstaluj na telefonie, wejdź w **Statystyki → Połącz OBD** i wybierz adapter
   (najpierw sparuj go w ustawieniach Bluetooth, PIN zwykle 1234 lub 0000).
   Bez auta? Kliknij **Demo**.
3. **Włącz aplikacje spoza Sklepu Play w Android Auto** (jednorazowo):
   Android Auto → Ustawienia → stuknij 10× w **„Wersja”** → menu **⋮** → **Ustawienia dla programistów**
   → zaznacz **„Nieznane źródła”**.
4. Podłącz telefon do auta – Opentaiment jest w menu aplikacji Android Auto.
   Przy kolejnych jazdach łączy się z adapterem sam.

W **Więcej → Ustawienia** ustaw cenę paliwa, rodzaj paliwa, masę i pojemność silnika –
od tego zależą koszt, moc i spalanie.

## Funkcje

**Dane z auta (OBD-II)**
- Spalanie chwilowe i średnie (z PID 5E, przepływomierza MAF albo ciśnienia MAP), wykrywa odcięcie paliwa przy hamowaniu silnikiem
- **Koszt paliwa** każdej trasy i podsumowania tygodnia/miesiąca
- **Eco-score 0–100** z podpowiedziami (gwałtowne ruszanie i hamowanie, wysokie obroty, postój na biegu jałowym)
- **Historia tras** – zapisuje się sama (po rozłączeniu, „Nowej trasie” albo 3 min ze zgaszonym silnikiem)
- **Alerty**: przegrzanie, za gorący olej, brak ładowania, za wysokie napięcie, słaby akumulator, mało paliwa
- Moc, moment i przyspieszenie (szacowane z fizyki), temperatury, napięcie, wykres ostatniej minuty
- **Błędy silnika**: zapisane / oczekujące / stałe, ~80 opisów po polsku, wyszukiwanie kodu, kasowanie
- **Freeze frame** – parametry silnika z chwili wystąpienia błędu
- **Gotowość do badania spalin** – czy auto przejdzie przegląd (benzyna i diesel)

**Wyścig** (tylko na torze!)
- 0–100 i 80–120 km/h, **¼ mili** z prędkością na mecie – mierzą się same
- **Okrążenia GPS** – ustaw linię mety jednym kliknięciem
- **G-meter** – przeciążenia w zakrętach (GPS), przy ruszaniu i hamowaniu (OBD)

**Wygląd i wygoda**
- **Automatyczny tryb nocny** – po zachodzie słońca (liczonym z Twojej lokalizacji) ekran robi się czerwono-czarny i ciemniejszy
- **6 kolorów akcentu**
- **Ekran „Jazda”** – jedna wielka liczba, kolejne strony przesuwasz palcem
- Gesty: przesuwanie stron, przesunięcie karty muzyki zmienia utwór (tablet), przytrzymanie kafelka = edycja (tablet)

| | | |
|---|---|---|
| ![Eco](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_driveEco.png) | ![Noc](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_driveNight.png) | ![Alert](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_driveWithAlert.png) |
| Eco-score i podpowiedź | Tryb nocny | Ostrzeżenie |
| ![Wyścig](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_race.png) | ![Ustawienia](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_settingsGreen.png) | ![Więcej](app-phone/src/test/snapshots/images/app.opentaiment.phone_PhoneScreenshotTest_more.png) |
| Wyścig | Ustawienia | Więcej |

## Radio z Androidem (albo tablet w desce)

Dla aut bez Android Auto ten sam kod działa jako **pełnoprawne radio samochodowe** (`app-headunit`):
launcher na cały ekran, radio internetowe, muzyka z pendrive'a, wszystkie ekrany OBD i rzeczy,
których potrzebuje radio na stałe w aucie – start z zapłonem, przyciski na kierownicy,
głośność zależna od prędkości. APK: artefakt `opentaiment-tablet-debug`.

| | |
|---|---|
| ![Ekran główny](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_CarRadioScreenshotTest_homeCarRadio.png) | ![Radio](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_CarRadioScreenshotTest_radio.png) |
| Ekran główny: nawigacja, opony, radio | Radio internetowe |
| ![Zegary](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_CarRadioScreenshotTest_gauges.png) | ![Opony](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_CarRadioScreenshotTest_tyres.png) |
| Zegary | Ciśnienie w oponach (TPMS) |
| ![Serwis](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_CarRadioScreenshotTest_service.png) | ![Muzyka z plików](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_CarRadioScreenshotTest_musicFiles.png) |
| Przypomnienia serwisowe | Muzyka z pendrive'a / karty |
| ![Ustawienia radia](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_CarRadioScreenshotTest_carSettings.png) | ![Noc](app-headunit/src/test/snapshots/images/app.opentaiment.headunit_CarRadioScreenshotTest_homeServiceWidget.png) |
| Ustawienia radia | Noc, widżet serwisu |

**Radio i muzyka**
- **Radio internetowe** z katalogu [radio-browser.info](https://www.radio-browser.info) (~50 tys. stacji, bez konta i klucza):
  popularne w Polsce, wyszukiwarka, ulubione, własny adres strumienia (MP3/AAC/HLS)
- Tytuł utworu z metadanych stacji, automatyczne wznawianie po zerwaniu internetu (tunele, słaby zasięg)
- **Muzyka z pamięci, karty SD i pendrive'a** – po folderach, „graj folder”, losowo
- Własna sesja multimediów: przyciski na kierownicy, powiadomienie i ekran blokady sterują radiem;
  następny/poprzedni przełącza ulubione stacje
- Pauza na komunikaty nawigacji i rozmowy (audio focus)

**Jak fabryczne radio**
- **Start razem z autem** – po włączeniu radia łączy się z OBD i włącza ostatnią stację
- **Zapłon = zasilanie USB** (tablet na ładowarce): odcięcie prądu zapisuje trasę i pauzuje muzykę
- Trasa jest zapisywana co 20 s, więc nagłe odcięcie prądu jej nie zgubi
- **Przyciski na kierownicy**: „Naucz przycisk” → wciśnij → wybierz akcję (graj/pauza, następna, głośniej,
  nawigacja, prowadź do domu, radio, telefon, asystent…). Z usługą dostępności działa też nad innymi aplikacjami
- **Głośność zależna od prędkości** (lekko / średnio / mocno) – z OBD, a bez niego z GPS
- **Jasność ekranu na dzień i noc** dla całego systemu, nie tylko Opentaiment
- **Dymek nad nawigacją** – prędkość i spalanie nad Mapami/Waze, czerwony przy ostrzeżeniu
- Widżet **Nawigacja**: do domu, do pracy, stacje paliw w pobliżu (Mapy Google, Waze albo dowolna apka z mapą)

**Auto**
- **Adaptery OBD na BLE** (Vgate iCar Pro BLE, OBDLink CX i klony „ELM327 BLE”) – wyszukiwanie bez parowania
- **Samo łączy się ponownie** po zerwaniu połączenia z adapterem (np. przy odpalaniu), trasa się nie urywa
- **Zegary** jak w sportowym aucie: obroty, prędkość, doładowanie turbo, temperatura płynu i oleju, napięcie
- **Ciśnienie w oponach** z tanich czujników TPMS na Bluetooth (BLE): 4 koła, ostrzeżenia o niskim ciśnieniu,
  temperaturze, wycieku i słabej baterii, przypisywanie czujników do kół
- **Przypomnienia serwisowe**: olej, przegląd, OC, rozrząd, płyn hamulcowy (+ własne) – po kilometrach i datach.
  Przebieg z auta (PID A6, nowsze auta) albo z Twojego odczytu + przejechane trasy
- **Ślad GPS każdej trasy** z mapką (kolor = prędkość), **eksport GPX** i **eksport wszystkich tras do CSV**
  (otwiera się wprost w Excelu)

### Jak ustawić radio

1. Zainstaluj APK. Przy pierwszym starcie Opentaiment zaproponuje, że zostanie **ekranem głównym**
   (launcherem) – kliknij „Ustaw”. Później: **Ustawienia → Tablet → Ustaw jako ekran główny**.
   W części chińskich radyj launcher wybiera się w ustawieniach fabrycznych („Default launcher”).
2. **Ustawienia → Radio samochodowe**: włącz to, czego chcesz. Przy jasności, dymku i przyciskach
   Android poprosi o osobne zgody („Modyfikowanie ustawień systemu”, „Wyświetlanie nad innymi aplikacjami”,
   usługa dostępności „Opentaiment – przyciski na kierownicy”).
3. Głośność od prędkości bez OBD i ślad GPS tras potrzebują **dokładnej lokalizacji**.
4. Czujniki opon: **Opony → Czujniki TPMS**. Czujniki z numerami 1–4 przypiszą się same.

## Uczciwie o ograniczeniach (radio)

- **Przyciski na kierownicy** w części chińskich radyj (MCU) nie docierają do Androida jako zwykłe klawisze –
  wtedy przypisz je w ustawieniach producenta do przycisków multimediów, a Opentaiment je zobaczy.
- **Zapłon**: radia, które przy zgaszeniu auta tylko „usypiają”, nie wysyłają sygnału startu – Opentaiment łączy się
  ponownie po wybudzeniu ekranu. Opcja „Zapłon = zasilanie USB” jest dla tabletów na ładowarce.
- Czujniki TPMS: obsługiwany jest najpopularniejszy format tanich czujników BLE („TPMS II”). Fabryczne czujniki
  auta (433 MHz) wymagają innego odbiornika.
- Mapka trasy to sam kształt przejazdu (bez podkładu mapy, działa offline) – ulice zobaczysz po otwarciu GPX w apce z mapą.
- Radio internetowe zużywa ~60 MB transferu na godzinę przy 128 kbps.

## Uczciwie o ograniczeniach

- **Moc i moment są szacowane** z prędkości, masy auta i oporów – zwykłe OBD ich nie podaje. Najdokładniej przy pełnym gazie na płaskim.
- **Spalanie w dieslu bez PID 5E** liczone z przepływomierza jest zawyżone (diesel pracuje na ubogiej mieszance).
- Zwykły ELM327 czyta **błędy silnika** ze standardowego OBD-II; ABS, poduszki czy klimatyzacja zwykle wymagają protokołów producenta.
- Dystans ¼ mili liczony jest z prędkości z OBD (jak licznik w aucie), a przeciążenia boczne z GPS – to dobre przybliżenia, nie pomiar z toru.
- Aplikacja nie jest w Sklepie Play, dlatego w Android Auto trzeba włączyć „Nieznane źródła”.

## Dla programistów

```
app-phone/        aplikacja na telefon (zakładki, usługa w tle, powiadomienia)
car/              ekrany Android Auto (Car App Library 1.7)
app-headunit/     radio z Androidem / tablet: launcher, usługa w tle, zapłon, kierownica, głośność, jasność
core/ui/          motywy, tryb nocny, komponenty
feature/vehicle/  OBD-II (ELM327 BT/BLE), obliczenia, historia + ślad GPS, alerty, TPMS, serwis, wyścig, ekrany Compose
feature/player/   radio internetowe i odtwarzacz plików (Media3)
feature/media|weather|apps/   moduły tabletu
```

- Testy logiki i ekranów Android Auto: `./gradlew testDebugUnitTest`
- Zrzuty ekranu bez emulatora: `./gradlew recordPaparazziDebug`
- Plan i architektura: [docs/PLAN.md](docs/PLAN.md)
