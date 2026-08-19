package ht.oni.cin.application.service;

/**
 * Une ligne de texte détectée par un OCR pleine page, avec sa position en fraction 0..1 de la
 * largeur/hauteur de l'image — indépendante de la résolution, donc directement comparable entre
 * le service PaddleOCR distant et le repli Tesseract local. Utilisé par
 * {@link LabelAnchoredOcrService} pour localiser un libellé bilingue puis la valeur juste en
 * dessous, plutôt que de dépendre de coordonnées ROI fixes qui dérivent d'une photo à l'autre.
 */
record OcrLine(String text, double confidence, double x0, double y0, double x1, double y1) {

    double xCenter() {
        return (x0 + x1) / 2.0;
    }
}
