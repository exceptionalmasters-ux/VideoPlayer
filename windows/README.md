# VideoPlayer dla Windows — 0.1.0

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
