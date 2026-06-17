# CLAUSE DE NON-RESPONSABILITÉ (DISCLAIMER)

**Microservice de Numérisation de la Carte d'Identité Nationale (CIN) Haïtienne**  
Version 1.0 — Document obligatoire avant toute utilisation

---

## 1. Objet

Le présent logiciel est un **prototype / système de démonstration** développé dans un cadre académique et institutionnel pour automatiser la numérisation des Cartes d'Identité Nationale (CIN) délivrées par l'Office National d'Identification (ONI) de la République d'Haïti.

## 2. Absence de garantie

Le logiciel est fourni **« EN L'ÉTAT »** (*AS IS*), sans garantie d'aucune sorte, expresse ou implicite, notamment :

- Aucune garantie de **précision OCR à 100 %** — l'opératrice humaine doit toujours valider les données extraites avant enregistrement.
- Aucune garantie de **vérification d'authenticité** de la carte auprès du RNPPI (hors périmètre v1).
- Aucune garantie de **disponibilité continue** du service (objectif 99,5 % en heures ouvrées, non contractuel en phase de démonstration).

## 3. Données personnelles

Le traitement des données contenues sur la CIN constitue un traitement de **données à caractère personnel** au sens de la législation haïtienne (Décret sur la Protection des Données Personnelles, cadre RNPPI).

**L'utilisateur s'engage à :**

- Ne traiter des CIN que sur une **base légale documentée** (consentement du titulaire, obligation légale ou intérêt légitime démontrable).
- Ne pas stocker ni transmettre de données au-delà de ce qui est strictement nécessaire.
- Respecter les droits des titulaires : accès, rectification, suppression.
- **Ne pas déployer en production** sans une Analyse d'Impact sur la Protection des Données (AIPD) validée — voir `docs/AIPD.md`.

## 4. Limitation de responsabilité

Les auteurs, l'ONI, les établissements partenaires et les contributeurs **ne pourront être tenus responsables** de :

- Toute erreur de saisie ou d'OCR non détectée par la validation humaine ;
- Toute utilisation frauduleuse ou non autorisée des données extraites ;
- Toute perte de données due à une mauvaise configuration ou à un défaut d'infrastructure ;
- Tout préjudice direct ou indirect résultant de l'utilisation de ce logiciel.

## 5. Sécurité

En environnement de démonstration, des clés de chiffrement et jetons par défaut sont utilisés. **Ils doivent impérativement être remplacés** avant toute mise en production :

- Clé AES-256 (`AES_ENCRYPTION_KEY`)
- Jeton d'enregistrement d'instance (`INSTANCE_REGISTRATION_TOKEN`)
- Clés JWT (passage à RS256 avec clé publique IAM)
- Clés API tiers (rotation tous les 90 jours)

## 6. Propriété intellectuelle

La CIN, le format NIN et le sigle ONI sont la propriété de l'État haïtien. Ce logiciel ne revendique aucun droit sur ces éléments officiels.

## 7. Acceptation

**En installant, exécutant ou déployant ce microservice, vous reconnaissez avoir lu, compris et accepté l'intégralité de la présente clause de non-responsabilité.**

---

*Pour toute question : contact institutionnel ONI — République d'Haïti*
