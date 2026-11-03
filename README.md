# VideoPlayer

Nieoficjalny prototyp aplikacji Android 8.0+ do otwierania stron filmów Vider oraz diagnozowania odtwarzania na rzeczywistym telefonie. Wersja 0.4.0.

## Co działa w kodzie

- Otwieranie pełnej strony filmu z vider.pl / vider.info; JavaScript, cookies własne i DOM storage.
- Wklejanie adresu, odbieranie samego linku przez Android „Udostępnij”, cofanie, odświeżanie i lokalne ulubione.
- Pełny ekran zgłaszany przez odtwarzacz strony i obrót telefonu bez przeładowania Activity.
- **Widok filmu**: przełącza układ pełnej strony na istniejącą ramkę `.video-frame`. Ramka nie jest przenoszona ani ładowana ponownie. Ponowne dotknięcie przywraca stronę. Obsługa tego układu jest eksperymentalna i zależy od HTML serwisu.
- **Ekran startowy wyszukiwania**: wpisz tytuł i wybierz Szukaj. Wyszukiwanie Vider działa w tle; ekran aplikacji pokazuje wyłącznie natywne kafelki. Kliknięcie otwiera pełną stronę filmu. Nie ma przejścia na stronę Google ani przycisku otwierającego stronę wyników. „Wyniki” wraca do listy.
- **Miniatury**: obraz powiązany z linkiem na liście Vider albo obraz z metadanych strony filmu lub jego osadzonego odtwarzacza. Jeśli strona nie udostępni obrazu, kafelek zachowuje neutralne tło i ikonę Play — nie podmieniamy go plakatem innego filmu.
- **Zachowanie sesji**: cookies Vider, w tym cookies firm trzecich potrzebne niektórym osadzonym komponentom, są włączone i zapisywane. Decyzję o zgodzie podejmuje użytkownik w komunikacie serwisu. Wybrany film jest pamiętany podczas przekierowania na stronę główną; aplikacja podejmuje najwyżej jedną automatyczną próbę powrotu po zamknięciu rozpoznanego komunikatu zgody. Jest też przycisk „Wróć do filmu”.
- **Diagnostyka → Eksportuj JSON**: zapisuje raport przez systemowy wybór miejsca, bez uprawnienia dostępu do całej pamięci telefonu.

Aplikacja używa oryginalnego odtwarzacza, wraz z jego reklamami wewnątrz ramki. Nie ma jeszcze natywnego odtwarzania MP4, pobierania filmów ani własnego katalogu.

## Co wiemy o Vider

Dostarczony przez użytkownika zapis MHTML pokazał pełną stronę filmu osadzającą `/embed/video/<id>`, JW Player, element HTML5 `<video>` i adres MP4 na stream.vider.info z parametrem identyfikatora użytkownika. U użytkownika pełna strona odtwarzała film równocześnie z reklamą zasłaniającą odtwarzacz, a osobny MP4 i osobna strona embed nie działały. Nie ustalono przyczyny: sesja, nagłówki i mechanizm reklamy pozostają hipotezami. Nie zapisujemy w repozytorium kopii strony konta ani adresów z identyfikatorem użytkownika.

## Diagnostyka i prywatność

Raport zawiera maksymalnie 500 ostatnich zdarzeń w pamięci procesu. Rejestruje nawigacje, błędy HTTP/sieci, żądania ramek i mediów, wejście w pełny ekran oraz widok filmu. Błędy JavaScript zapisują wyłącznie numer linii i zanonimizowany adres skryptu — bez treści komunikatu.

Na WebView obsługującym `WEB_MESSAGE_LISTENER` oraz `DOCUMENT_START_SCRIPT` obserwator otrzymuje zdarzenia z elementów `<video>` w ramkach dokładnie czterech dozwolonych domen strony. Wykrywa również wideo dodane dynamicznie. Zapisuje stan gotowości, buforowania, pauzy, kod błędu i pozycję w sekundach. Zdarzenia pochodzące ze strony są niezaufanymi obserwacjami, nie dowodem pobrania odpowiedzi z serwera. Brak zdarzeń odtwarzania może oznaczać nieobsługiwaną funkcję WebView albo odtwarzacz w innej domenie.

Zanim URL trafi do raportu, usuwane są login/hasło, wszystkie parametry zapytania (np. uid i token), fragment oraz nieznane ścieżki, w tym nazwy profili. Nie rejestrujemy cookies, nagłówków, treści strony, tytułów, historii wyszukiwania ani treści konsoli. Raport nie jest automatycznie wysyłany. Eksportowany plik pozostaje w miejscu wybranym przez użytkownika, a przycisk „Wyczyść raport” usuwa wyłącznie bufor w aplikacji. Bufor znika po zakończeniu procesu.

W WebView filmu cookies firm trzecich są włączone dla zgodności osadzonego odtwarzacza i komponentów zgód. W WebView wyszukiwarki Vider również są włączone, aby zachować ten sam kontekst sesji. Okna pop-up i przejścia poza domeny Vider są blokowane; ręcznie kliknięty link zewnętrzny można otworzyć w przeglądarce po potwierdzeniu. Te ograniczenia mogą wpływać na działanie reklam lub odtwarzacza i są rejestrowane w raporcie. Błędy TLS nie są pomijane, HTTP i dostęp do plików lokalnych są wyłączone. Sesja Chrome nie jest współdzielona z WebView — ewentualne logowanie odbywa się na stronie w aplikacji.

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

W wersji 0.4 źródłem jest wyszukiwarka samego Vider. Publiczny plik https://vider.info/opensearch.xml deklaruje szablon `https://vider.info/search/all/{searchTerms}`. Aplikacja korzysta z tego adresu w tle i przekształca odczytane linki na kafelki. Wersja 0.3 błędnie pokazywała stronę Google, gdy parser nie znalazł wyników; to zachowanie i przycisk „Strona wyników” zostały usunięte.

Parser łączy miniaturę i tekst z różnych linków prowadzących do tego samego filmu. Obsługuje krótkie adresy `/vid/+fID` i starsze adresy `/profil/tytul+fID`, normalizując je do strony filmu. Nie wyświetla katalogów jako filmów. Stare indeksowane linki HTTP do dokładnych domen Vider są zamieniane na HTTPS przed otwarciem; inne hosty i schematy są odrzucane.

Jeśli wykryta zostanie CAPTCHA, zgoda cookies lub wymagane logowanie, pojawia się oznaczone okno „Potwierdź dostęp do Vider” wewnątrz aplikacji. Użytkownik sam wpisuje kod lub dokonuje wyboru. Po uzyskaniu dostępu aplikacja wraca do kafelków. Nie próbuje automatycznie rozwiązywać CAPTCHA. Przycisk „Gotowe” ponawia odczyt, a „Anuluj” lub Wstecz przerywa weryfikację. Przy błędzie, braku wyników lub nierozpoznanym układzie pozostaje natywny komunikat; strona wyszukiwania nie jest pokazywana jako awaryjny ekran.

Ograniczono równoległość pobierania miniatur do jednego zadania i dodano ich cache w pamięci, aby powtórne wyświetlenie listy nie pobierało ponownie tych samych obrazów. Kolejne wyszukiwania można rozpoczynać co najmniej co dwie sekundy. Nie gwarantuje to braku CAPTCHA: jej wyświetlanie zależy od serwisu i adresu sieciowego.

Przycisk **Raport** na ekranie wyszukiwania eksportuje diagnostykę JSON: kod HTTP, liczbę znalezionych linków/kafelków, etap weryfikacji i przekierowania. Zapytanie, tytuły, treść strony, wpisywane litery CAPTCHA, dane logowania i cookies nie są zapisywane w raporcie. Ścieżka zapytania jest usuwana z adresu przed logowaniem. Raport odtwarzacza pozostaje oddzielnie pod przyciskiem Diagnostyka.

Miniatury są pobierane z linków listy lub metadanych tej samej strony/ramki filmu. HTML do 768 KiB i obrazy do 2 MiB, do czterech kontrolowanych przekierowań. Cookies są dołączane tylko do odpowiadającej im domeny Vider. Nie są pobierane filmy MP4 w celu tworzenia miniatur. Przy braku udostępnionego obrazu pozostaje neutralny kafelek Play.

## Test na telefonie

1. Zainstaluj 0.4.0 jako aktualizację 0.3.0 — podpis testowy powinien być ten sam, więc nie odinstalowuj poprzedniej aplikacji bez potrzeby. Przejście z 0.2.0 może wymagać odinstalowania z powodu dawnego jednorazowego podpisu.
2. Wpisz tytuł i dotknij Szukaj. Sprawdź kafelki i miniatury. Ekran nie powinien pokazywać Google ani strony wyszukiwarki Vider.
3. Jeśli otworzy się „Potwierdź dostęp do Vider”, wpisz kod lub wybierz zgodę samodzielnie. Po zakończeniu powinny pojawić się miniatury.
4. Dotknij miniatury, uruchom film i sprawdź „Widok filmu”. „Wyniki” wraca do listy.
5. Jeśli zamiast kafelków jest błąd lub pusta lista, wybierz **Raport** na ekranie wyszukiwania i prześlij JSON z krótkim opisem. Nie trzeba kopiować kodu strony ani odpowiedzi CAPTCHA.

## Stan weryfikacji

Odtwarzanie i widok filmu w 0.2 zostały potwierdzone na telefonie przez użytkownika. W 0.3 testy i build przechodziły, ale awaryjne pokazanie strony Google nie spełniało oczekiwanego interfejsu. W 0.4 wyszukiwanie i wyniki są przebudowane zgodnie z powyższym opisem. Testy parsera, wykrywania weryfikacji, nawigacji i polityki adresów są wykonywane razem z Android Lint i kompilacją APK. Środowisko deweloperskie otrzymuje pustą odpowiedź HTTP 404 dla wyszukiwania Vider, dlatego działanie wyszukiwania na żywo wymaga testu na telefonie. Potwierdzono jedynie oficjalny szablon w opensearch.xml; nie traktujemy samej kompilacji jako dowodu działania serwisu.
