"""Service OCR isolé pour le microservice CIN Haïti.

Reçoit une image (recadrage de champ ou carte entière) et renvoie le texte
reconnu par PaddleOCR (open source, gratuit, exécution locale — sans appel
cloud ni coût d'API). Appelé par le microservice Java comme moteur OCR
primaire ; en cas d'échec ou d'indisponibilité, le microservice bascule
automatiquement sur Tesseract en local.
"""

import cv2
import numpy as np
from fastapi import FastAPI, File, Form, UploadFile
from paddleocr import PaddleOCR

app = FastAPI(title="CIN OCR Service", description="Service OCR isolé (PaddleOCR)")

_ocr = PaddleOCR(use_angle_cls=True, lang="fr", show_log=False)


@app.get("/healthz")
def healthz():
    return {"status": "ok"}


@app.post("/recognize")
async def recognize(file: UploadFile = File(...), psm: int = Form(default=7)):
    data = await file.read()
    image = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        return {"text": "", "confidence": 0.0}

    result = _ocr.ocr(image, cls=True)

    lines: list[str] = []
    scores: list[float] = []
    if result and result[0]:
        for _box, (text, score) in result[0]:
            lines.append(text)
            scores.append(score)

    confidence = round(sum(scores) / len(scores) * 100, 2) if scores else 0.0
    return {"text": "\n".join(lines), "confidence": confidence}
