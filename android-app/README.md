# student-management Android App

Application Android (MVVM + Retrofit) qui consomme les endpoints REST de `student-management`.

## Prerequis

- Android Studio Hedgehog+ (ou 2024+)
- SDK Android 35
- Backend Spring Boot lance sur `http://localhost:8080`

## URL backend

Dans `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
```

`10.0.2.2` = alias de `localhost` depuis l'emulateur Android.

## Ecrans inclus

- Login (session Spring Security)
- Home par role (ADMIN, CHEF_FILIERE, TEACHER, STUDENT)
- Listes connectees aux endpoints mobile:
  - Student: notes, absences, emploi du temps, cours, annonces, devoirs
  - Teacher: modules, notes, absences, cours, annonces, devoirs

## Notes

- L'app utilise une session cookie (JSESSIONID) via `SessionCookieJar`.
- Les endpoints backend sont sous `/api/mobile/**`.
- Pour appareil physique, remplacez `10.0.2.2` par l'IP LAN de votre PC.
