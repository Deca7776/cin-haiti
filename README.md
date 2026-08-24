# Microservice de Numérisation CIN — Haïti 🇭🇹

> **Chef-d'œuvre technique** — Implémentation Java (Spring Boot 21) du cahier des charges ONI pour la numérisation automatisée de la Carte d'Identité Nationale haïtienne.

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│  React UI       │────▶│  Spring Boot API │────▶│ PostgreSQL  │
│  (Validation)   │ JWT │  (Java 21)       │     │  16 + Flyway│
└─────────────────┘     └────────┬─────────┘     └─────────────┘
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼            ▼
               ┌────────┐  ┌────────┐  ┌────────┐
               │ Redis 7│  │ MinIO  │  │Tesseract│
               │Sessions│  │ Photos │  │  OCR   │
               └────────┘  └────────┘  └────────┘
```

### Stack technique

| Composant | Technologie |
|-----------|-------------|
| Backend | **Java 21**, Spring Boot 3.3, Spring Security |
| Base de données | PostgreSQL 16, Flyway, JPA/Hibernate |
| Cache / Sessions | Redis 7 (TTL 10 min, cloisonnement par opératrice) |
| OCR | Tesseract 5 (offline-first) + hooks Vision API |
| Images | Sharp-like preprocessing Java, extraction ROI photo |
| Stockage photos | MinIO (S3-compatible, chiffrement AES-256-GCM) |
| Frontend | React 18, Tailwind CSS, Vite |
| Sécurité | JWT RS256-ready, X-API-Key, rate limiting Bucket4j |
| Observabilité | Actuator, Prometheus, OpenAPI/Swagger |

## Collaboration GitHub

### Cloner le projet

```bash
git clone https://github.com/Deca7776/cin-haiti.git
cd cin-haiti
```

### Prérequis collaborateur

| Outil | Version |
|-------|---------|
| Java | 21 |
| Maven | 3.9+ |
| Node.js | 18+ |
| Tesseract | 5 (optionnel — tess4j embarqué) |

### Démarrage rapide après clone

```powershell
# 1. Données OCR Tesseract (une fois)
mkdir -p data/tessdata
# Télécharger fra.traineddata dans data/tessdata/
# https://github.com/tesseract-ocr/tessdata/raw/main/fra.traineddata

# 2. Backend
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:SPRING_PROFILES_ACTIVE = "local"
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=8081

# 3. Frontend
cd frontend && npm install && npm run dev
```

Scripts Windows : `scripts\start-backend.bat` et `scripts\start-frontend.bat`

### Branches

- `main` — code stable
- Créer une branche par fonctionnalité : `git checkout -b feature/nom`

---


### Prérequis

- **Java 21** (Microsoft OpenJDK 21 installé : `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot`)
- Maven est fourni dans `.tools/apache-maven-3.9.6/`
- Node.js 18+ (frontend)

### 1. Lancer le backend (profil `local`)

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:SPRING_PROFILES_ACTIVE = "local"
.\.tools\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Ou double-cliquez : `scripts\start-backend.bat`

API : http://localhost:8081  
Swagger : http://localhost:8081/swagger-ui.html

> Le profil `local` utilise H2 (fichier `./data/cin_local`), sessions en mémoire, stockage photo local — **aucun Docker requis**.

### 2. Vérifier que tout fonctionne (tests automatiques)

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:SPRING_PROFILES_ACTIVE = "local"
.\.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

**Résultat attendu :** `Tests run: 5, Failures: 0` — flux complet session → OCR → validation → export.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

UI : http://localhost:5173

### 4. Clés de démonstration

## Endpoints principaux (CDC §6.3)

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/v1/scan/sessions` | EF-01 Ouverture session |
| POST | `/api/v1/scan/{id}/image` | EF-03/04/05 Upload + OCR |
| GET | `/api/v1/scan/{id}/results` | Résultats pour validation |
| POST | `/api/v1/scan/{id}/validate` | EF-07 Enregistrement définitif |
| POST | `/api/v1/scan/{id}/cancel` | EF-02 Annulation |
| GET | `/api/v1/identites/{nin}` | EF-10 Export systèmes tiers |

## Intégration dans une autre application

Ce microservice est conçu pour être embarqué dans une app tierce (ex. un portail de paiement/gestion type EPS) de deux façons complémentaires.

### 1. Widget embarqué (iframe)

Chargez le frontend React dans une `<iframe>` avec le paramètre `embed=1` : l'entête et le pied de page propres au widget disparaissent (l'app hôte fournit déjà son propre chrome — sidebar, langue, avatar...), il ne reste que le flux Scan → Validation → Confirmation.

```html
<iframe
  src="https://cin.mondomaine.ht/?embed=1&token=JWT_OPERATRICE"
  style="width:100%; height:100%; border:0;"
  title="Numérisation CIN">
</iframe>
```

- `embed=1` — masque l'entête/pied de page du widget.
- `token=...` — optionnel : si l'app hôte gère déjà l'émission du jeton JWT opératrice, on saute l'écran de connexion. Sans ce paramètre, le widget affiche son propre écran d'authentification (mode démo par défaut).

Le widget notifie la page parente par `postMessage` à chaque étape clé du cycle de vie (`window.addEventListener('message', ...)`) :

```js
window.addEventListener('message', (e) => {
  if (e.data?.source !== 'cin-haiti-widget') return
  switch (e.data.type) {
    case 'ready':     /* le widget a fini de charger */ break
    case 'validated': /* e.data.payload = enregistrement CIN sauvegardé */ break
    case 'cancelled': /* l'opératrice a annulé / fermé le widget */ break
    case 'error':     /* e.data.payload.message = erreur affichée à l'opératrice */ break
  }
})
```

### 2. Appel direct de l'API REST

Pour une intégration headless (l'app hôte construit son propre écran), consultez le Swagger (`/swagger-ui.html`) — flux : ouvrir une session → uploader l'image → valider → exporter via `GET /api/v1/identites/{nin}` avec une clé `X-API-Key`.

### CORS

Les origines autorisées à appeler l'API depuis un navigateur (widget en iframe sur un autre domaine, ou frontend hôte appelant directement l'API) se configurent via la variable d'environnement `ALLOWED_ORIGINS` (liste séparée par des virgules, cf. `docker-compose.yml`) — pas besoin de modifier le code pour ajouter le domaine d'une nouvelle app intégratrice.

### Authentification opératrice (JWT)

`JwtValidator` a deux modes, selon la config au démarrage :

- **Démo/local (par défaut)** — sans `JWT_PUBLIC_KEY_PATH`, les jetons sont HMAC signés avec `JWT_DEV_SECRET`, obtenus via `GET /api/v1/dev/token`. Pratique pour développer sans IAM externe, **à ne jamais utiliser en production**.
- **Production (IAM externe)** — dès que `JWT_PUBLIC_KEY_PATH` pointe vers la clé publique RSA (PEM) de l'app hôte, les jetons sont vérifiés en **RS256** avec cette clé : l'app hôte signe elle-même ses JWT opératrice avec sa propre IAM (clé privée qui ne quitte jamais l'hôte), ce microservice ne fait que vérifier la signature. `/api/v1/dev/token` se désactive automatiquement dans ce mode (403) pour éviter qu'un jeton de démo non reconnu par la production ne parte silencieusement dans une iframe.

```bash
JWT_PUBLIC_KEY_PATH=/etc/cin/iam-public-key.pem
```

Le sujet (`sub`) du JWT devient l'identifiant opératrice (`operatorId`) utilisé pour l'audit ; l'app hôte doit donc y placer un identifiant stable de son côté.

## Structure du projet

```
CIN/
├── src/main/java/ht/oni/cin/
│   ├── api/              # Controllers REST, DTOs
│   ├── application/      # Services métier
│   ├── config/           # Spring configuration
│   ├── domain/           # Modèles domaine
│   ├── infrastructure/   # JPA, Redis, MinIO
│   └── security/         # JWT, dual auth, rate limit
├── frontend/             # Interface validation React
├── docs/                 # AIPD, réponses CDC, disclaimer
├── docker-compose.yml
└── Dockerfile
```

## Jalons CDC couverts

- [x] M1 — Pipeline OCR image → JSON
- [x] M2 — Interface validation v0
- [x] M3 — API REST, sessions, PostgreSQL + MinIO
- [x] M4 — Auth double couche, chiffrement, audit
- [x] M5 — API d'export tiers
- [x] M6 — Tests unitaires, documentation
- [ ] M7 — Production (nécessite AIPD certifiée — voir `docs/AIPD.md`)

## Clés de démonstration

| Élément | Valeur |
|---------|--------|
| JWT | `GET /api/v1/dev/token` |
| API Key (export) | `demo-api-key-2024` |
| Registration token instance | `demo-registration-token` |

## Documentation légale

- [DISCLAIMER.md](docs/DISCLAIMER.md) — Clause de non-responsabilité
- [AIPD.md](docs/AIPD.md) — Analyse d'Impact Protection des Données
- [REPONSES_CDC.md](docs/REPONSES_CDC.md) — Réponses aux questions du CDC

## Licence

Usage institutionnel — Office National d'Identification (ONI), République d'Haïti.
