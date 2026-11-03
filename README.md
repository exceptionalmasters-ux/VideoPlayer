# VideoPlayer

Nieoficjalny prototyp aplikacji Android 8.0+ do otwierania stron filmów Vider oraz diagnozowania odtwarzania na rzeczywistym telefonie. Wersja 0.3.0.

## Co działa w kodzie

- Otwieranie pełnej strony filmu z vider.pl / vider.info; JavaScript, cookies własne i DOM storage.
- Wklejanie adresu, odbieranie samego linku przez Android „Udostępnij”, cofanie, odświeżanie i lokalne ulubione.
- Pełny ekran zgłaszany przez odtwarzacz strony i obrót telefonu bez przeładowania Activity.
- **Widok filmu**: przełącza układ pełnej strony na istniejącą ramkę `.video-frame`. Ramka nie jest przenoszona ani ładowana ponownie. Ponowne dotknięcie przywraca stronę. Obsługa tego układu jest eksperymentalna i zależy od HTML serwisu.
- **Ekran startowy wyszukiwania**: wpisz tytuł i wybierz Szukaj. Google jest otwierane w osobnym WebView wewnątrz aplikacji. Odczytane linki do filmów są pokazywane jako natywne kafelki; kliknięcie otwiera pełną stronę filmu, bez kopiowania adresu. Wstecz lub „Wyniki” wraca do listy.
- **Miniatury**: obraz powiązany z wynikiem Google albo obraz z metadanych strony filmu. Jeśli strona nie udostępni obrazu, kafelek zachowuje neutralne tło i ikonę Play — nie podmieniamy go plakatem innego filmu.
- **Zachowanie sesji**: cookies Vider, w tym cookies firm trzecich potrzebne niektórym osadzonym komponentom, są włączone i zapisywane. Decyzję o zgodzie podejmuje użytkownik w komunikacie serwisu. Wybrany film jest pamiętany podczas przekierowania na stronę główną; aplikacja podejmuje najwyżej jedną automatyczną próbę powrotu po zamknięciu rozpoznanego komunikatu zgody. Jest też przycisk „Wróć do filmu”.
- **Diagnostyka → Eksportuj JSON**: zapisuje raport przez systemowy wybór miejsca, bez uprawnienia dostępu do całej pamięci telefonu.

Aplikacja używa oryginalnego odtwarzacza, wraz z jego reklamami wewnątrz ramki. Nie ma jeszcze natywnego odtwarzania MP4, pobierania filmów ani własnego katalogu.

## Co wiemy o Vider

Dostarczony przez użytkownika zapis MHTML pokazał pełną stronę filmu osadzającą `/embed/video/<id>`, JW Player, element HTML5 `<video>` i adres MP4 na stream.vider.info z parametrem identyfikatora użytkownika. U użytkownika pełna strona odtwarzała film równocześnie z reklamą zasłaniającą odtwarzacz, a osobny MP4 i osobna strona embed nie działały. Nie ustalono przyczyny: sesja, nagłówki i mechanizm reklamy pozostają hipotezami. Nie zapisujemy w repozytorium kopii strony konta ani adresów z identyfikatorem użytkownika.

## Diagnostyka i prywatność

Raport zawiera maksymalnie 500 ostatnich zdarzeń w pamięci procesu. Rejestruje nawigacje, błędy HTTP/sieci, żądania ramek i mediów, wejście w pełny ekran oraz widok filmu. Błędy JavaScript zapisują wyłącznie numer linii i zanonimizowany adres skryptu — bez treści komunikatu.

Na WebView obsługującym `WEB_MESSAGE_LISTENER` oraz `DOCUMENT_START_SCRIPT` obserwator otrzymuje zdarzenia z elementów `<video>` w ramkach dokładnie czterech dozwolonych domen strony. Wykrywa również wideo dodane dynamicznie. Zapisuje stan gotowości, buforowania, pauzy, kod błędu i pozycję w sekundach. Zdarzenia pochodzące ze strony są niezaufanymi obserwacjami, nie dowodem pobrania odpowiedzi z serwera. Brak zdarzeń odtwarzania może oznaczać nieobsługiwaną funkcję WebView albo odtwarzacz w innej domenie.

Zanim URL trafi do raportu, usuwane są login/hasło, wszystkie parametry zapytania (np. uid i token), fragment oraz nieznane ścieżki, w tym nazwy profili. Nie rejestrujemy cookies, nagłówków, treści strony, tytułów, historii wyszukiwania ani treści konsoli. Raport nie jest automatycznie wysyłany. Eksportowany plik pozostaje w miejscu wybranym przez użytkownika, a przycisk „Wyczyść raport” usuwa wyłącznie bufor w aplikacji. Bufor znika po zakończeniu procesu.

W WebView filmu cookies firm trzecich są włączone dla zgodności osadzonego odtwarzacza i komponentów zgód. W osobnym WebView wyszukiwarki pozostają wyłączone. Okna pop-up i przejścia poza domeny Vider są blokowane; ręcznie kliknięty link zewnętrzny można otworzyć w przeglądarce po potwierdzeniu. Te ograniczenia mogą wpływać na działanie reklam lub odtwarzacza i są rejestrowane w raporcie. Błędy TLS nie są pomijane, HTTP i dostęp do plików lokalnych są wyłączone. Sesja Chrome nie jest współdzielona z WebView — ewentualne logowanie odbywa się na stronie w aplikacji.

## APK z GitHub Actions

Workflow `.github/workflows/android.yml` uruchamia się na push do main i gałęzi codex/**, pull request oraz ręcznie przez **Actions → Android APK → Run workflow**. Używa JDK 17, Android SDK 35, AGP 8.9.2 i Gradle 8.11.1.

1. Wejdź w **Actions → Android APK** i otwórz zakończone powodzeniem uruchomienie.
2. Na dole w **Artifacts** pobierz **VideoPlayer-debug** (do pobrania może być potrzebne zalogowanie do GitHub).
3. Rozpakuj archiwum. W środku będzie `app-debug.apk`; skopiuj go na telefon i zainstaluj.

CI wykonuje testy JavaScript, testy jednostkowe polityki adresów, Android Lint oraz kompilację APK. Testy i raporty Lint są w artefakcie `validation-reports`. APK jest testowy, podpisywany kluczem debug; nie jest wydaniem Google Play. Od 0.3.0 workflow zachowuje testowy debug.keystore w GitHub Actions cache, aby kolejne udane buildy mogły aktualizować aplikację bez usuwania danych. To wyłącznie podpis testowy, nie klucz wydania produkcyjnego. Wersja 0.2.0 miała inny jednorazowy klucz: pierwsza instalacja 0.3.0 może wymagać odinstalowania 0.2.0, co usuwa cookies i ustawienia. Usunięcie/wygaśnięcie cache również może zmienić podpis.

## Budowanie lokalne

Zainstaluj Android Studio z Platform 35 i Build Tools 35.0.0 oraz JDK 17. Ustaw `JAVA_HOME` i `ANDROID_HOME`, albo zapisz `sdk.dir=/pelna/sciezka/do/Android/Sdk` w `local.properties`. W Windows użyj ukośników `/`.

- Z zainstalowanym Gradle 8.11.1: `gradle testDebugUnitTest lintDebug assembleDebug`.
- Alternatywnie: `python3 build.py` (Windows: `py build.py`). Skrypt pobiera oficjalny Gradle z kontrolą SHA-256, generuje standardowy wrapper i uruchamia testy, Lint oraz build.
- Po wygenerowaniu wrappera: `./gradlew testDebugUnitTest lintDebug assembleDebug` lub `gradlew.bat ...`.
- Testy skryptów: `node --test tests/browser-scripts.test.cjs`.

APK po udanej kompilacji: `app/build/outputs/apk/debug/app-debug.apk`. Projekt można otworzyć w Android Studio; przed pierwszym lokalnym importem najprościej wygenerować wrapper przez `build.py`.

## Wyszukiwanie: sposób działania i ograniczenia

Nie trzeba konfigurować klucza API ani kopiować linków z przeglądarki. W tej wersji dostawcą jest zwykła strona Google z zapytaniem o tytuł ograniczonym do `vider.info/vid/` i `vider.pl/vid/`. Parser odczytuje tylko HTTPS i dokładne domeny Vider, odrzuca katalogi oraz usuwa duplikaty filmów. Nie używa oficjalnego płatnego API i zależy od układu wyników wyszukiwarki.

Jeżeli Google wymaga zgody lub weryfikacji albo układ nie daje się odczytać, aplikacja pokazuje stronę wyszukiwarki w tym samym ekranie. Użytkownik sam kończy wymagany krok; kliknięcie linku filmu nadal otwiera go w aplikacji. Przycisk „Strona wyników” pozwala przełączyć widok źródła lub ponowić odczyt kafelków. Brak indeksowanych filmów nie jest zastępowany fikcyjnymi wynikami. Ten mechanizm wymaga sprawdzenia na telefonie; w przyszłości można wymienić dostawcę na udokumentowane API.

Miniatury są pobierane w tle w maksymalnie trzech wątkach: HTML do 768 KiB, obraz do 2 MiB, do czterech kontrolowanych przekierowań. HTML jest pobierany tylko ze stron Vider; cookies są dołączane wyłącznie dla odpowiadającej im domeny. Obrazy mogą pochodzić wyłącznie z img.vider.info lub gstatic.com i jego subdomen, albo ograniczonej miniatury data:image. W tym procesie nie są pobierane filmy MP4. Żądania wyszukiwania i tytuły nie trafiają do raportu diagnostycznego; Google otrzymuje wpisane zapytanie jako dostawca wyszukiwania.

## Test na telefonie

1. Jeśli Android odmawia aktualizacji 0.2.0 z powodu podpisu, odinstaluj ją i zainstaluj 0.3.0. Jest to pierwsze przejście na zachowywany podpis testowy; zgody i logowanie trzeba wtedy wykonać ponownie.
2. Wpisz tytuł, np. Maverick, i dotknij Szukaj. Sprawdź listę kafelków i miniatury. Jeśli pojawi się Google z komunikatem, wybierz zgodę samodzielnie w tym ekranie.
3. Dotknij kafelka. Sprawdź, czy otwiera się właściwy film, a po ewentualnym przeniesieniu na stronę główną następuje powrót. „Wróć do filmu” pozwala ponowić przejście ręcznie bez szukania linku.
4. Po uruchomieniu filmu sprawdź „Widok filmu”, pełny ekran i obrót. „Wyniki” wraca do wyszukiwania.
5. Zamknij i otwórz aplikację, wyszukaj drugi film i sprawdź, czy serwis pamięta decyzję cookies. Nie gwarantujemy zachowania zgody wbrew terminowi ważności lub regułom serwisu; nie kopiujemy cookies pomiędzy domenami .pl i .info.
6. Jeśli coś nie działa w odtwarzaczu, wybierz Diagnostyka → Eksportuj JSON i podaj objaw. Dla problemów wyszukiwania podaj tytuł oraz czy pojawia się strona Google, brak wyników, czy kafelki bez obrazów.

## Stan weryfikacji

Wersja 0.2.0 została zbudowana w GitHub Actions i odtwarzanie oraz widok filmu zostały potwierdzone przez użytkownika na telefonie. Wersja 0.3.0 dodaje nowy przebieg wyszukiwania, politykę miniatur i mechanizm powrotu po przekierowaniu. Testy parsera, nawigacji i kontroli adresów są wykonywane w CI wraz z Android Lint i kompilacją APK. Wyszukiwanie na żywo, faktyczne obrazy wyników i trwałość zgód wymagają testu na telefonie.
