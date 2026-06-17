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
