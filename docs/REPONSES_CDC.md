# Réponses aux questions du Cahier des Charges

Ce document répond aux notes de bas de page du CDC (`CDC_Scan_CIN_Haiti.docx`).

---

## [1] Que fait-on si les contraintes de rate limiting ne sont pas respectées ?

**Contraintes concernées :**
- 100 requêtes/minute par clé d'API
- 10 sessions simultanées maximum par opératrice

### Comportement implémenté

| Contrainte | Violation détectée | Réponse HTTP | Action complémentaire |
|------------|-------------------|--------------|----------------------|
| 100 req/min (API export) | `RateLimitService` (Bucket4j) | **429 Too Many Requests** | Header `Retry-After` (secondes avant refill) + événement audit `RATE_LIMIT_EXCEEDED` |
| 10 sessions/opératrice | `SessionService.enforceSessionLimit()` | **429 Too Many Requests** | Message explicite FR/HT + audit `SESSION_LIMIT_EXCEEDED` |

### Politique opérationnelle recommandée

1. **Première violation** : rejet automatique, log d'audit, métrique Prometheus incrémentée.
2. **Violations répétées** (seuil configurable, ex. 5 en 15 min) : alerte superviseur + notification administrateur.
3. **Abus manifeste** : révocation temporaire de la clé API (flag `active = false` dans `cin_api_client`) ou blocage JWT via liste de révocation Redis.
4. **Sessions** : l'opératrice doit annuler ou laisser expirer une session avant d'en ouvrir une nouvelle.

### Code de référence

- Rate limit API : `RateLimitService.java`, `ExportController.java`
- Limite sessions : `SessionService.java` (EF-01)

---

## [2] Combien d'instances sont autorisées ? Le contrôle est-il en place ?

### Nombre d'instances autorisées

**Par défaut : 5 instances** (configurable via `cin.instances.max-allowed` / variable d'environnement `MAX_INSTANCES`).

Ce plafond est aligné sur la capacité cible du CDC : **500 cartes/jour par instance**, soit jusqu'à **2 500 cartes/jour** avec 5 instances en autoscaling horizontal.

### Mécanisme de contrôle

| Composant | Rôle |
|-----------|------|
| Table `cin_instance_registry` | Registre append-only des instances actives |
| `InstanceRegistryService` | Vérifie le plafond avant enregistrement |
| Heartbeat (`@Scheduled` 60s) | Met à jour `last_heartbeat` — instances silencieuses > 5 min peuvent être désactivées |
| Jeton `X-Registration-Token` | Empêche l'enregistrement non autorisé d'instances |

### Enregistrement d'une instance

```bash
curl -X POST http://localhost:8080/api/v1/dev/instances/register \
  -H "X-Registration-Token: demo-registration-token"
```

Réponse en cas de dépassement :
```json
{
  "error": "Nombre maximal d'instances autorisées atteint (5)"
}
```

### Recommandation production

- Intégrer le registre avec **Kubernetes HPA** (max replicas = `MAX_INSTANCES`)
- Superviser `cin_instance_registry` via Grafana/Prometheus
- Révoquer les instances dont `last_heartbeat` > seuil

---

## [3] Bien souligner : certificat AIPD avant mise en production

### Position officielle du projet

> **La mise en production (jalon M7) est formellement interdite tant qu'une Analyse d'Impact sur la Protection des Données (AIPD) n'a pas été conduite, validée et signée par les autorités compétentes.**

Le traitement des données CIN est classé **à risque élevé** en raison :
- de la nature d'identifiant national (NIN / RNPPI) ;
- de la photo d'identité (donnée sensible) ;
- des exports vers systèmes tiers.

### Documents fournis

- `docs/AIPD.md` — modèle complet avec checklist de certification
- `docs/DISCLAIMER.md` — clause d'acceptation obligatoire

### Affichage dans l'application

- Le README marque explicitement M7 comme bloqué sans AIPD
- Le footer de l'interface React renvoie au DISCLAIMER
- Le Swagger documente les exigences de consentement sur l'API d'export

---

## [4] Préparer et ajouter un disclaimer

### Fichier fourni

**`docs/DISCLAIMER.md`** — clause de non-responsabilité complète couvrant :

1. Nature du logiciel (démo / académique / institutionnel)
2. Absence de garantie (OCR, authenticité, disponibilité)
3. Obligations en matière de données personnelles
4. Limitation de responsabilité
5. Exigences de sécurité en production
6. Propriété intellectuelle ONI / État haïtien
7. **Clause d'acceptation explicite** à l'installation

### Intégration recommandée

- Afficher le disclaimer au premier lancement de l'interface opératrice (écran modal « J'accepte »)
- Inclure un lien dans le contrat des systèmes tiers consommant l'API d'export
- Archiver la date d'acceptation dans `cin_audit_log`

---

## Synthèse pour la soutenance

| Question CDC | Réponse courte |
|--------------|----------------|
| [1] Dépassement limites | HTTP 429 + audit + Retry-After + révocation si abus |
| [2] Instances | Max 5 (configurable), registre + heartbeat + token |
| [3] AIPD | Obligatoire avant prod — checklist dans `docs/AIPD.md` |
| [4] Disclaimer | `docs/DISCLAIMER.md` + intégration UI/API |
