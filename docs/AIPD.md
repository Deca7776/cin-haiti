# Analyse d'Impact sur la Protection des Données (AIPD)

## Microservice CIN Haïti — Document préparatoire

> **⚠️ AVERTISSEMENT CRITIQUE**  
> **Aucune mise en production de ce microservice ne doit être autorisée sans la certification formelle d'une AIPD complète**, validée par le délégué à la protection des données et les autorités compétentes en République d'Haïti.

---

## 1. Identification du traitement

| Élément | Description |
|---------|-------------|
| **Responsable de traitement** | Office National d'Identification (ONI) / Institution mandataire |
| **Finalité** | Numérisation automatisée de la CIN, validation humaine, stockage sécurisé, export contrôlé vers systèmes tiers autorisés |
| **Catégories de données** | Identité civile (NIN, nom, prénom, dates, adresse), **photo d'identité** (donnée sensible renforcée) |
| **Personnes concernées** | Titulaires de CIN haïtienne |
| **Durée de conservation** | Conforme au cadre légal haïtien — audit minimum 5 ans |

## 2. Nécessité et proportionnalité

- **Base légale** : consentement explicite du titulaire OU obligation légale (démarche administrative) OU intérêt légitime documenté.
- **Minimisation** : seuls les 11 champs du CDC sont collectés ; pas de biométrie (empreintes) en v1.
- **Validation humaine obligatoire** : aucun enregistrement automatique sans confirmation de l'opératrice.

## 3. Risques identifiés

| Risque | Gravité | Mesures techniques |
|--------|---------|-------------------|
| Fuite de données CIN | Élevée | Chiffrement AES-256-GCM au repos, TLS 1.3 en transit |
| Accès non autorisé tiers | Élevée | Double auth (API Key + JWT), moindre privilège par champ |
| Erreur OCR non corrigée | Moyenne | Score de confiance, signalisation < 80 %, double validation |
| Session non fermée | Moyenne | TTL Redis 10 min, suppression données temporaires |
| Export sans consentement | Élevée | Flag `consentement_documente` obligatoire avant export |

## 4. Mesures de sécurité implémentées

- [x] Chiffrement AES-256-GCM (données sensibles en session)
- [x] Photos chiffrées dans MinIO
- [x] Empreinte SHA-256 d'intégrité par enregistrement
- [x] Journal d'audit append-only signé (`cin_audit_log`)
- [x] Rate limiting et limite de sessions simultanées
- [x] Registre d'instances déployées

## 5. Droits des titulaires

Le système prévoit techniquement :

- **Droit d'accès** : consultation via processus institutionnel
- **Droit de rectification** : modification tracée dans l'audit
- **Droit de suppression** : archivage / statut `ARCHIVE` avec traçabilité

## 6. Certification avant production — CHECKLIST

| # | Exigence | Statut |
|---|----------|--------|
| 1 | AIPD rédigée et soumise à l'autorité compétente | ☐ À valider |
| 2 | Délégué à la protection des données désigné | ☐ À valider |
| 3 | Registre des traitements mis à jour | ☐ À valider |
| 4 | Clauses contractuelles avec systèmes tiers | ☐ À valider |
| 5 | Tests d'intrusion (pentest) réalisés | ☐ À valider |
| 6 | Formation des opératrices à la protection des données | ☐ À valider |
| 7 | Procédure de gestion des violations de données | ☐ À valider |
| 8 | Remplacement de toutes les clés de démonstration | ☐ À valider |

## 7. Signature et approbation

| Rôle | Nom | Date | Signature |
|------|-----|------|-----------|
| Responsable de traitement | | | |
| DPO / Référent données | | | |
| Autorité de contrôle | | | |

---

**Ce document constitue une base de travail. La certification finale relève des autorités haïtiennes compétentes en protection des données personnelles.**
