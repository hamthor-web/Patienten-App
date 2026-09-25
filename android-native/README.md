# Patienten-App – native Android-Testversion

Diese Testversion nutzt Androids native `SpeechRecognizer`-API statt der Browser-Spracherkennung.

## Verhalten
- Einmalige Mikrofonfreigabe beim ersten Start.
- Sprachtest mit „Rot“ während der Einrichtung.
- Danach startet die App direkt im Farbspiel.
- Auf Geräten ab Android 12 wird bevorzugt On-Device-Spracherkennung genutzt, wenn verfügbar.
- Fallback auf den normalen Android-Spracherkennungsdienst, falls On-Device nicht verfügbar ist.
- Gesprochene Farbe: Rot, Blau oder Grün.
- Einer von drei Zielkreisen nimmt diese Farbe an.
- Der graue Kreis wird per Finger zum Ziel geschoben.

Der GitHub-Workflow `Android Debug APK` baut automatisch eine installierbare Debug-APK.
