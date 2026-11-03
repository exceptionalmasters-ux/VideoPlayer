# VideoPlayer

Nieoficjalny prototyp aplikacji Android 8.0+ do otwierania stron filmów Vider oraz diagnozowania odtwarzania na rzeczywistym telefonie. Wersja 0.2.0.

## Co działa w kodzie

- Otwieranie pełnej strony filmu z vider.pl / vider.info; JavaScript, cookies własne i DOM storage.
- Wklejanie adresu, odbieranie samego linku przez Android „Udostępnij”, cofanie, odświeżanie i lokalne ulubione.
- Pełny ekran zgłaszany przez odtwarzacz strony i obrót telefonu bez przeładowania Activity.
- **Widok filmu**: przełącza układ pełnej strony na istniejącą ramkę `.video-frame`. Ramka nie jest przenoszona ani ładowana ponownie. Ponowne dotknięcie przywraca stronę. Obsługa tego układu jest eksperymentalna i zależy od HTML serwisu.
- **Google**: wyszukuje tytuł z ograniczeniem do domen Vider w zewnętrznej przeglądarce. Znaleziony link można udostępnić do VideoPlayer. Wyniki nie są jeszcze wyświetlane natywnie w aplikacji.
- **Diagnostyka → Eksportuj JSON**: zapisuje raport przez systemowy wybór miejsca, bez uprawnienia dostępu do całej pamięci telefonu.

Aplikacja używa oryginalnego odtwarzacza, wraz z jego reklamami wewnątrz ramki. Nie ma jeszcze natywnego odtwarzania MP4, pobierania filmów ani własnego katalogu.

## Co wiemy o Vider

Dostarczony przez użytkownika zapis MHTML pokazał pełną stronę filmu osadzającą `/embed/video/<id>`, JW Player, element HTML5 `<video>` i adres MP4 na stream.vider.info z parametrem identyfikatora użytkownika. U użytkownika pełna strona odtwarzała film równocześnie z reklamą zasłaniającą odtwarzacz, a osobny MP4 i osobna strona embed nie działały. Nie ustalono przyczyny: sesja, nagłówki i mechanizm reklamy pozostają hipotezami. Nie zapisujemy w repozytorium kopii strony konta ani adresów z identyfikatorem użytkownika.

## Diagnostyka i prywatność

Raport zawiera maksymalnie 500 ostatnich zdarzeń w pamięci procesu. Rejestruje nawigacje, błędy HTTP/sieci, żądania ramek i mediów, wejście w pełny ekran oraz widok filmu. Błędy JavaScript zapisują wyłącznie numer linii i zanonimizowany adres skryptu — bez treści komunikatu.

Na WebView obsługującym `WEB_MESSAGE_LISTENER` oraz `DOCUMENT_START_SCRIPT` obserwator otrzymuje zdarzenia z elementów `<video>` w ramkach dokładnie czterech dozwolonych domen strony. Wykrywa również wideo dodane dynamicznie. Zapisuje stan gotowości, buforowania, pauzy, kod błędu i pozycję w sekundach. Zdarzenia pochodzące ze strony są niezaufanymi obserwacjami, nie dowodem pobrania odpowiedzi z serwera. Brak zdarzeń odtwarzania może oznaczać nieobsługiwaną funkcję WebView albo odtwarzacz w innej domenie.

Zanim URL trafi do raportu, usuwane są login/hasło, wszystkie parametry zapytania (np. uid i token), fragment oraz nieznane ścieżki, w tym nazwy profili. Nie rejestrujemy cookies, nagłówków, treści strony, tytułów, historii wyszukiwania ani treści konsoli. Raport nie jest automatycznie wysyłany. Eksportowany plik pozostaje w miejscu wybranym przez użytkownika, a przycisk „Wyczyść raport” usuwa wyłącznie bufor w aplikacji. Bufor znika po zakończeniu procesu.

Cookies firm trzecich są wyłączone. Okna pop-up i przejścia poza domeny Vider są blokowane; ręcznie kliknięty link zewnętrzny można otworzyć w przeglądarce po potwierdzeniu. Te ograniczenia mogą wpływać na działanie reklam lub odtwarzacza i są rejestrowane w raporcie. Błędy TLS nie są pomijane, HTTP i dostęp do plików lokalnych są wyłączone. Sesja Chrome nie jest współdzielona z WebView — ewentualne logowanie odbywa się na stronie w aplikacji.

## APK z GitHub Actions

Workflow `.github/workflows/android.yml` uruchamia się na push do main i gałęzi codex/**, pull request oraz ręcznie przez **Actions → Android APK → Run workflow**. Używa JDK 17, Android SDK 35, AGP 8.9.2 i Gradle 8.11.1.

1. Wejdź w **Actions → Android APK** i otwórz zakończone powodzeniem uruchomienie.
2. Na dole w **Artifacts** pobierz **VideoPlayer-debug** (do pobrania może być potrzebne zalogowanie do GitHub).
3. Rozpakuj archiwum. W środku będzie `app-debug.apk`; skopiuj go na telefon i zainstaluj.

CI wykonuje testy JavaScript, testy jednostkowe polityki adresów, Android Lint oraz kompilację APK. Testy i raporty Lint są w artefakcie `validation-reports`. APK jest testowy, podpisywany kluczem debug na maszynie budującej; nie jest wydaniem Google Play. Nowy klucz debug między uruchomieniami może wymagać odinstalowania wcześniejszego APK, co usuwa sesję i ustawienia aplikacji.

## Budowanie lokalne

Zainstaluj Android Studio z Platform 35 i Build Tools 35.0.0 oraz JDK 17. Ustaw `JAVA_HOME` i `ANDROID_HOME`, albo zapisz `sdk.dir=/pelna/sciezka/do/Android/Sdk` w `local.properties`. W Windows użyj ukośników `/`.

- Z zainstalowanym Gradle 8.11.1: `gradle testDebugUnitTest lintDebug assembleDebug`.
- Alternatywnie: `python3 build.py` (Windows: `py build.py`). Skrypt pobiera oficjalny Gradle z kontrolą SHA-256, generuje standardowy wrapper i uruchamia testy, Lint oraz build.
- Po wygenerowaniu wrappera: `./gradlew testDebugUnitTest lintDebug assembleDebug` lub `gradlew.bat ...`.
- Testy skryptów: `node --test tests/browser-scripts.test.cjs`.

APK po udanej kompilacji: `app/build/outputs/apk/debug/app-debug.apk`. Projekt można otworzyć w Android Studio; przed pierwszym lokalnym importem najprościej wygenerować wrapper przez `build.py`.

## Pierwszy test na telefonie

1. Zaktualizuj Chrome / Android System WebView.
2. Uruchom aplikację, otwórz pełny link filmu, ewentualnie zaloguj się na stronie.
3. Dotknij Play i poczekaj, aż reklama zniknie. Sprawdź dźwięk i przewijanie.
4. Przełącz „Widok filmu”, pełny ekran, obrót i przycisk Wstecz. Powrót z widoku filmu: ten sam przycisk.
5. Jeśli coś nie działa, wybierz **Diagnostyka → Eksportuj JSON** i prześlij raport wraz z krótkim opisem objawu. Nie trzeba kopiować strony konta ani cookies.

## Stan weryfikacji

3 listopada 2026 kompilacja w GitHub Actions zakończyła się powodzeniem: trzy testy JavaScript, pięć testów JUnit polityki adresów, Android Lint i budowanie debug APK. Wynik: https://github.com/exceptionalmasters-ux/VideoPlayer/actions/runs/41723557082 (commit a13f0f7cd83c40398f9ab1565009eb4776a4e5dd). APK jest dostępny w artefakcie VideoPlayer-debug przez 14 dni od uruchomienia. Kolejny push lub ręczne Run workflow odtwarza build.

Lokalnie sprawdzono również składnię Java, XML, Python i YAML oraz wykonanie polityki URL na JVM. Odtwarzanie Vider, logowanie i układ na rzeczywistym telefonie pozostają do sprawdzenia.
