# VideoPlayer dla Windows — 0.2.0

Pierwsza wersja WPF / C# / .NET 10 z WebView2. Windows 10/11 x64.

## Uruchomienie

Uruchom `VideoPlayer-Windows.exe`. Pakiet jest samodzielny: nie wymaga osobnej instalacji .NET. Wymaga Microsoft Edge WebView2 Runtime (Evergreen), zwykle obecnego na Windows 10/11. Jeśli go brakuje, pobierz go z https://developer.microsoft.com/microsoft-edge/webview2/.

Wybierz Vider lub Freedisc, wpisz tytuł i naciśnij Enter / Szukaj. Kliknięcie miniaturki otwiera oryginalną stronę filmu w aplikacji. Możesz również wkleić link i użyć Otwórz link. Po wykryciu odtwarzacza aplikacja automatycznie przechodzi do widoku filmu i pełnego ekranu. Odtwarzanie uruchamia użytkownik w oryginalnym playerze.

- **Esc**: wyjście z pełnego ekranu i pokazanie przycisków. Widok filmu pozostaje aktywny.
- **F11**: przełączanie pełnego ekranu.
- **Prawy przycisk myszy → Pokaż przyciski / wyjdź z pełnego ekranu**: powrót do sterowania bez klawiatury.
- **Cała strona**: przywrócenie oryginalnego układu strony.
- **Wyniki**: powrót do kafelków i zamknięcie dokumentu filmu (zatrzymanie dźwięku).
- **Pokaż stronę / Sprawdź wyniki**: ręczne dokończenie wymaganych przez serwis kroków i ponowny odczyt miniatur.

Kody antyspamowe wpisuje użytkownik. Reklamy wewnątrz odtwarzacza pozostają aktywne. Aplikacja nie pobiera filmów. Układ i dostępność zewnętrznych serwisów mogą się zmieniać.

Cookies i dane strony przechowywane są lokalnie w `%LOCALAPPDATA%\VideoPlayer\WebView2`. Raport diagnostyczny zapisuje się wyłącznie po wybraniu Raport; nie zawiera cookies, kodów, tytułów wyszukiwań ani parametrów URL.

## Budowanie

```powershell
dotnet run --project windows/VideoPlayer.Checks -c Release
dotnet publish windows/VideoPlayer.Windows -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:EnableCompressionInSingleFile=true -o windows/publish
```

Workflow `.github/workflows/windows.yml` generuje ikonę z istniejącego wzoru, uruchamia kontrole polityk URL, testy współdzielonych skryptów JS oraz test uruchomienia WPF/WebView2 z lokalnymi dokumentami. Wynikowy EXE i raporty trafiają do artefaktów GitHub Actions. Test lokalny nie potwierdza działania bieżących stron Vider/Freedisc.

## Współdzielenie kodu

Skrypty JS są osadzane bezpośrednio z `app/src/main/assets` podczas kompilacji. Zmiana ekstrakcji miniatur, układu playera lub obsługi ramek trafia do obu platform; interfejs i sesja Windows są osobne.

## Historia i ulubione

Zakładki **Historia** i **Ulubione** pokazują własne, lokalne kolekcje jako kafelki. Historia przechowuje 200 ostatnio otwieranych filmów; ponowne otwarcie przenosi film na początek. Każdy kafelek ma przycisk dodania/usunięcia z ulubionych. Na listach można usuwać pojedyncze pozycje, a przycisk Wyczyść listę wymaga potwierdzenia. Wyczyszczenie historii nie usuwa ulubionych. Dane zapisują się w `%LOCALAPPDATA%\VideoPlayer\collections.json`; nie synchronizują się z Androidem ani kontami serwisów. Gdy film otwierany jest z wklejonego linku i nie udostępnia miniaturki, kafelek pokazuje symbol odtwarzania.


## Wersja 0.3.0

- Linki Freedisc `/embed/video/ID?match=1` otwierają film; wpis embed i zwykły link tego samego pliku dzielą historię oraz punkt wznowienia.
- Gdy wyszukiwanie serwisu nie odpowiada, aplikacja próbuje Google. Dostępny jest także przycisk „Szukaj w Google”. Wyniki są filtrowane do wybranego serwisu i wyświetlane jako kafelki. Zgodę i CAPTCHA użytkownik potwierdza ręcznie w aplikacji.
- Kolejne wyszukiwanie tej samej frazy rozpoczyna nową nawigację. Wyniki są czytane dopiero z właściwej strony wyszukiwania.
- Oryginalny odtwarzacz dostaje polecenie startu; aplikacja otwiera widok filmu na pełnym ekranie. Spacja / Enter przełączają odtwarzanie, strzałki lewo/prawo przewijają o 10 sekund. Skróty nie przejmują wpisywania w polach formularzy. F11 przełącza pełny ekran, Esc przywraca przyciski.
- Postęp jest zapisywany lokalnie co około 5 sekund. Ponowne otwarcie filmu z historii przywraca pozycję. Zakończony film zaczyna się od początku. Zmiana długości materiału chroni przed wznowieniem innej wersji filmu w niewłaściwym miejscu.
- Dla tytułów z numerem `S01E01`, `S1E01` lub `1x01` wyszukiwanie kolejnego odcinka zaczyna się 40 sekund przed końcem. Małe kafelki pojawiają się 10 sekund przed końcem w oknie nad filmem. Film gra dalej do kliknięcia propozycji. Dopiero potwierdzony brak odcinka uruchamia szukanie pierwszego odcinka następnego sezonu; błąd sieci i CAPTCHA nie oznaczają końca sezonu.
- Reklamy pozostają obsługiwane przez oryginalny odtwarzacz.

Walidacja: testy polityk adresów i trwałości kolekcji, 44 wspólne testy skryptów oraz test integracyjny WPF/WebView2 na Windows (pełny ekran, przywracanie strony, most odtwarzania, zapis pozycji, Play/Pauza, filtrowanie wyników Google). Test używa lokalnych kontrolowanych stron; działanie konkretnych serwisów nadal zależy od ich dostępności i zmian.
