"""Service OCR isolé pour le microservice CIN Haïti.

Reçoit une image (recadrage de champ ou carte entière) et renvoie le texte
reconnu par PaddleOCR (open source, gratuit, exécution locale — sans appel
cloud ni coût d'API). Appelé par le microservice Java comme moteur OCR
primaire ; en cas d'échec ou d'indisponibilité, le microservice bascule
automatiquement sur Tesseract en local.
"""

import logging
import threading

import cv2
import numpy as np
from fastapi import FastAPI, File, Form, Response, UploadFile
from paddleocr import PaddleOCR

log = logging.getLogger("uvicorn.error")

app = FastAPI(title="CIN OCR Service", description="Service OCR isolé (PaddleOCR)")

_ocr = PaddleOCR(use_angle_cls=True, lang="fr", show_log=False)
# PaddleOCR (moteur natif oneDNN/Paddle sous-jacent) n'est pas thread-safe : chaque endpoint
# /recognize, /prepare(non concerne) et /layout est en "def" (pas "async def"), donc FastAPI les
# execute chacun dans un thread du pool -- plusieurs requetes simultanees peuvent alors appeler
# _ocr.ocr(...) en parallele sur la MEME instance. Observe le 19/08/2026 : plantages oneDNN
# intermittents ("could not create/execute a primitive") pouvant degenerer en SIGSEGV, et des
# appels qui ne repondent plus jamais -- signature classique d'acces concurrent a un moteur
# d'inference natif non reentrant. Ce verrou serialise tous les appels d'inference ; /prepare
# (OpenCV pur, sans PaddleOCR) reste hors verrou et continue de s'executer en parallele.
_ocr_lock = threading.Lock()

# Format CR80 standard (carte ID/bancaire) : 85.6mm x 53.98mm ~= 1.586
CARD_ASPECT = 1.586
WARP_WIDTH = 1600
WARP_HEIGHT = int(WARP_WIDTH / CARD_ASPECT)


@app.get("/healthz")
def healthz():
    return {"status": "ok"}


def _decode(data: bytes):
    return cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)


def _order_points(pts: np.ndarray) -> np.ndarray:
    """Ordonne 4 points en (haut-gauche, haut-droite, bas-droite, bas-gauche)."""
    rect = np.zeros((4, 2), dtype="float32")
    s = pts.sum(axis=1)
    rect[0] = pts[np.argmin(s)]
    rect[2] = pts[np.argmax(s)]
    diff = np.diff(pts, axis=1)
    rect[1] = pts[np.argmin(diff)]
    rect[3] = pts[np.argmax(diff)]
    return rect


def detect_and_warp_card(img: np.ndarray) -> np.ndarray:
    """Localise le contour de la carte dans la photo et corrige perspective/rotation vers
    un cadrage canonique 1600x1008 (ratio CR80). Les photos reelles (prises a main levee,
    carte dans une pochette plastique) ne remplissent pas le cadre exactement de la meme
    facon d'une capture a l'autre -- contrairement a un scan propre, elles ont des marges
    variables qui font deriver toutes les coordonnees ROI en aval. Si aucun contour fiable
    n'est trouve (fond ambigu, carte deja cadree pile au bord, contraste insuffisant), on
    renvoie l'image telle quelle : ce redressement est une amelioration best-effort, jamais
    une etape bloquante.
    """
    try:
        h, w = img.shape[:2]
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        blur = cv2.GaussianBlur(gray, (5, 5), 0)
        edges = cv2.Canny(blur, 30, 100)
        edges = cv2.dilate(edges, np.ones((5, 5), np.uint8), iterations=2)
        contours, _ = cv2.findContours(edges, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return img

        largest = max(contours, key=cv2.contourArea)
        if cv2.contourArea(largest) < 0.25 * w * h:
            # Contour trop petit pour etre la carte entiere -- probablement du bruit/reflet.
            return img

        rect = cv2.minAreaRect(largest)
        (rect_w, rect_h) = rect[1]
        if rect_w == 0 or rect_h == 0:
            return img
        aspect = max(rect_w, rect_h) / min(rect_w, rect_h)
        if not (1.25 <= aspect <= 1.95):
            # Pas un rectangle au ratio d'une carte -- le contour detecte n'est probablement
            # pas la carte (table, pochette, ombre...).
            return img

        ordered = _order_points(cv2.boxPoints(rect))
        dst = np.array(
            [[0, 0], [WARP_WIDTH - 1, 0], [WARP_WIDTH - 1, WARP_HEIGHT - 1], [0, WARP_HEIGHT - 1]],
            dtype="float32",
        )
        matrix = cv2.getPerspectiveTransform(ordered, dst)
        return cv2.warpPerspective(img, matrix, (WARP_WIDTH, WARP_HEIGHT))
    except Exception:
        log.warning("Detection/redressement de carte echoue, image d'origine conservee", exc_info=True)
        return img


@app.post("/prepare")
def prepare(file: UploadFile = File(...)):
    """Normalise une photo de carte : detecte son contour et corrige perspective/rotation
    vers un cadrage canonique, pour que les coordonnees ROI (Java) et l'ancrage par libelle
    (/layout) restent valides quelle que soit la facon dont la photo a ete cadree/zoomee."""
    data = file.file.read()
    image = _decode(data)
    if image is None:
        return Response(content=data, media_type="image/jpeg")

    warped = detect_and_warp_card(image)
    ok, buf = cv2.imencode(".jpg", warped, [cv2.IMWRITE_JPEG_QUALITY, 92])
    if not ok:
        ok, buf = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, 92])
    return Response(content=buf.tobytes(), media_type="image/jpeg")


@app.post("/recognize")
def recognize(file: UploadFile = File(...), psm: int = Form(default=7)):
    # Endpoint synchrone (pas "async def") : FastAPI l'execute alors dans un thread du pool
    # plutot que directement sur la boucle d'evenements. L'inference PaddleOCR est bloquante et
    # gourmande en CPU ; en "async def" elle gelait la boucle entiere le temps de l'inference,
    # y compris pour /healthz — d'ou un service completement injoignable pendant les gros calculs.
    data = file.file.read()
    image = _decode(data)
    if image is None:
        return {"text": "", "confidence": 0.0}

    try:
        with _ocr_lock:
            result = _ocr.ocr(image, cls=True)
    except Exception:
        # Certains recadrages ROI degeneres (trop petits/fins) font planter le moteur oneDNN de
        # PaddleOCR ("could not create a primitive descriptor for a reorder primitive") plutot que
        # de simplement renvoyer un texte vide. Sans ce filet, FastAPI renvoie un 500 que cin-api
        # interprete comme "service indisponible" et bascule sur Tesseract local (bien plus lent) —
        # pour une seule zone ratee, ca suffit a faire depasser le timeout nginx sur tout l'upload.
        log.warning("Echec inference OCR sur ce recadrage, on renvoie un resultat vide", exc_info=True)
        return {"text": "", "confidence": 0.0}

    lines: list[str] = []
    scores: list[float] = []
    if result and result[0]:
        for _box, (text, score) in result[0]:
            lines.append(text)
            scores.append(score)

    confidence = round(sum(scores) / len(scores) * 100, 2) if scores else 0.0
    return {"text": "\n".join(lines), "confidence": confidence}


@app.post("/layout")
def layout(file: UploadFile = File(...)):
    """OCR pleine carte en un seul appel, avec la position de chaque ligne detectee (en
    fraction 0..1 de la largeur/hauteur de l'image). Permet a cin-api de localiser un champ
    par son libelle bilingue puis de lire la valeur juste en dessous, plutot que de dependre
    de coordonnees fixes qui derivent d'une photo a l'autre (angle, zoom, cadrage variable).
    Remplace donc les ~10 appels ROI separes de /recognize par un seul appel — plus rapide,
    et une degenerescence sur une zone ne peut plus faire echouer que cet appel unique (repli
    Tesseract local cote Java), pas un recadrage isole au milieu d'une serie.
    """
    data = file.file.read()
    image = _decode(data)
    if image is None:
        return {"lines": []}

    h, w = image.shape[:2]
    try:
        with _ocr_lock:
            result = _ocr.ocr(image, cls=True)
    except Exception:
        log.warning("Echec inference OCR layout, renvoi d'une liste vide", exc_info=True)
        return {"lines": []}

    lines = []
    if result and result[0]:
        for box, (text, score) in result[0]:
            xs = [p[0] for p in box]
            ys = [p[1] for p in box]
            lines.append({
                "text": text,
                "confidence": round(score * 100, 2),
                "x0": max(0.0, min(xs) / w),
                "y0": max(0.0, min(ys) / h),
                "x1": min(1.0, max(xs) / w),
                "y1": min(1.0, max(ys) / h),
            })
    return {"lines": lines}
