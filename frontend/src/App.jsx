import { useState, useRef, useCallback } from 'react'
import { Camera, Upload, CheckCircle, XCircle, AlertTriangle, Shield, IdCard } from 'lucide-react'

const API = '/api/v1'
const FIELDS = [
  { key: 'numero_carte', label: 'Numéro de carte', required: true },
  { key: 'prenom', label: 'Prénom(s)', required: true },
  { key: 'nom', label: 'Nom de famille', required: true },
  { key: 'sexe', label: 'Sexe (M/F)', required: true },
  { key: 'nationalite', label: 'Nationalité', required: true, placeholder: 'HTI' },
  { key: 'date_naissance', label: 'Date de naissance', required: true, placeholder: 'JJ/MM/AAAA' },
  { key: 'lieu_naissance', label: 'Lieu de naissance', required: true },
  { key: 'departement', label: 'Département', required: false },
  { key: 'date_emission', label: "Date d'émission", required: true, placeholder: 'JJ/MM/AAAA' },
  { key: 'date_expiration', label: "Date d'expiration", required: true, placeholder: 'JJ/MM/AAAA' },
  { key: 'nin_display', label: 'NIN (numéro sur la carte)', required: true, placeholder: '0123456789' },
]

const DEPARTEMENTS = [
  'Artibonite', 'Centre', "Grand'Anse", 'Nippes', 'Nord', 'Nord-Est',
  'Nord-Ouest', 'Ouest', 'Sud', 'Sud-Est'
]

function App() {
  const [token, setToken] = useState(localStorage.getItem('cin_token') || '')
  const [session, setSession] = useState(null)
  const [form, setForm] = useState({})
  const [fieldMeta, setFieldMeta] = useState({})
  const [photo, setPhoto] = useState(null)
  const [preview, setPreview] = useState(null)
  const [idempotencyToken, setIdempotencyToken] = useState(null)
  const [step, setStep] = useState('auth')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [savedRecord, setSavedRecord] = useState(null)
  const [extractionStats, setExtractionStats] = useState(null)
  const [showConfirm, setShowConfirm] = useState(false)
  const [photoConfirmed, setPhotoConfirmed] = useState(false)
  const fileRef = useRef(null)

  const headers = useCallback(() => ({
    Authorization: `Bearer ${token}`,
    ...(loading ? {} : {})
  }), [token, loading])

  const fetchToken = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(`${API}/dev/token?operatorId=operatrice-demo`)
      const data = await res.json()
      setToken(data.token)
      localStorage.setItem('cin_token', data.token)
      setStep('scan')
    } catch (e) {
      setError('Pa kapab konekte ak sèvè a / Impossible de se connecter au serveur')
    } finally {
      setLoading(false)
    }
  }

  const openSession = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(`${API}/scan/sessions`, { method: 'POST', headers: headers() })
      if (!res.ok) throw new Error(await res.text())
      const data = await res.json()
      setSession(data)
      setStep('scan')
    } catch (e) {
      setError(e.message || 'Erè nan ouvèti sesyon an')
    } finally {
      setLoading(false)
    }
  }

  const uploadImage = async (file) => {
    if (!session) return
    setLoading(true)
    setError(null)
    setPreview(URL.createObjectURL(file))
    const fd = new FormData()
    fd.append('file', file)
    try {
      const res = await fetch(`${API}/scan/${session.sessionId}/image`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: fd
      })
      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.message || 'Échec du traitement OCR')
      }
      const data = await res.json()
      applyResults(data)
      setStep('validate')
      if (data.errorMessage) {
        setError('OCR : ' + data.errorMessage + ' — Vérifiez et corrigez les champs ci-dessous.')
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const applyResults = (data) => {
    const newForm = {}
    const meta = {}
    data.fields?.forEach(f => {
      const key = f.name
      newForm[key] = f.value
      meta[key] = { confidence: f.confidence, needsReview: f.needsReview }
    })
    if (newForm.nin_display && !newForm.nin) {
      newForm.nin = String(newForm.nin_display).replace(/\D/g, '').padStart(13, '0')
    }
    if (!newForm.nationalite) newForm.nationalite = 'HTI'
    setForm(newForm)
    setFieldMeta(meta)
    setExtractionStats({
      extracted: data.fieldsExtracted ?? Object.keys(newForm).length,
      expected: data.fieldsExpected ?? 11,
      confidence: data.averageConfidence ?? 0,
    })
    setIdempotencyToken(data.idempotencyToken)
    if (data.photoBase64) {
      setPhoto(`data:image/jpeg;base64,${data.photoBase64}`)
    }
  }

  const buildValidatePayload = () => {
    const ninDigits = (form.nin_display || form.nin || '').replace(/\D/g, '')
    const nin = ninDigits.padStart(13, '0').slice(-13)
    return {
      numeroCarte: form.numero_carte,
      prenom: form.prenom,
      nom: form.nom,
      sexe: form.sexe,
      nationalite: form.nationalite || 'HTI',
      dateNaissance: form.date_naissance,
      lieuNaissance: form.lieu_naissance,
      departement: form.departement,
      dateEmission: form.date_emission,
      dateExpiration: form.date_expiration,
      nin,
      ninDisplay: form.nin_display || ninDigits,
      adresse: null,
      idempotencyToken,
      scoreOcrMoyen: Object.values(fieldMeta).reduce((a, m) => a + (m.confidence || 0), 0) / Math.max(Object.keys(fieldMeta).length, 1),
      consentementDocumente: true,
      baseLegale: 'consentement_titulaire',
      confirmDuplicateCheck: true,
    }
  }

  const handleValidate = async () => {
    setLoading(true)
    setError(null)
    try {
      const body = buildValidatePayload()
      const res = await fetch(`${API}/scan/${session.sessionId}/validate`, {
        method: 'POST',
        headers: { ...headers(), 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.message || 'Validation échouée')
      }
      const data = await res.json()
      setSavedRecord(data)
      setSuccess(data.message || 'Enregistrement réussi')
      setStep('done')
      setShowConfirm(false)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = async () => {
    if (session) {
      await fetch(`${API}/scan/${session.sessionId}/cancel`, {
        method: 'POST',
        headers: headers()
      })
    }
    reset()
  }

  const reset = () => {
    setSession(null)
    setForm({})
    setFieldMeta({})
    setPhoto(null)
    setPreview(null)
    setStep(token ? 'scan' : 'auth')
    setSuccess(null)
    setSavedRecord(null)
    setExtractionStats(null)
    setError(null)
    setPhotoConfirmed(false)
  }

  return (
    <div className="min-h-screen">
      <header className="bg-gradient-to-r from-oni-blue via-blue-800 to-oni-blue text-white shadow-lg">
        <div className="max-w-6xl mx-auto px-4 py-5 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <IdCard className="w-8 h-8 text-oni-gold" />
            <div>
              <h1 className="text-xl font-bold tracking-tight">Numérisation CIN — Haïti</h1>
              <p className="text-blue-200 text-sm">Office National d'Identification (ONI)</p>
            </div>
          </div>
          <div className="flex items-center gap-2 text-sm bg-white/10 px-3 py-1.5 rounded-full">
            <Shield className="w-4 h-4 text-oni-gold" />
            <span>Session sécurisée · TTL 10 min</span>
          </div>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-4 py-8">
        {error && (
          <div className="mb-6 flex items-center gap-2 bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded-lg">
            <AlertTriangle className="w-5 h-5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {step === 'auth' && (
          <div className="bg-white rounded-2xl shadow-md p-8 max-w-md mx-auto text-center">
            <h2 className="text-2xl font-semibold mb-2">Connexion opératrice</h2>
            <p className="text-slate-600 mb-6">Authenticate via IAM — mode démo disponible</p>
            <button onClick={fetchToken} disabled={loading}
              className="w-full bg-oni-blue hover:bg-blue-900 text-white font-medium py-3 px-6 rounded-xl transition">
              {loading ? 'Connexion...' : 'Obtenir jeton de démonstration'}
            </button>
          </div>
        )}

        {step === 'scan' && (
          <div className="grid md:grid-cols-2 gap-8">
            <div className="bg-white rounded-2xl shadow-md p-6">
              <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
                <Camera className="w-5 h-5 text-oni-blue" /> Acquisition de la carte
              </h2>
              {!session ? (
                <button onClick={openSession} disabled={loading}
                  className="w-full bg-oni-gold hover:bg-yellow-500 text-oni-blue font-bold py-3 rounded-xl mb-4">
                  Ouvrir une session de scan
                </button>
              ) : (
                <p className="text-sm text-slate-500 mb-4">Session : {session.sessionId?.slice(0, 8)}…</p>
              )}
              <div
                className="border-2 border-dashed border-slate-300 rounded-xl p-8 text-center cursor-pointer hover:border-oni-blue transition"
                onClick={() => session && fileRef.current?.click()}
              >
                <Upload className="w-12 h-12 mx-auto text-slate-400 mb-3" />
                <p className="font-medium">Importer une image (JPEG, PNG)</p>
                <p className="text-sm text-slate-500 mt-1">ou glisser-déposer · min. 300 DPI recommandé</p>
              </div>
              <input ref={fileRef} type="file" accept="image/*" className="hidden"
                onChange={e => e.target.files?.[0] && uploadImage(e.target.files[0])} />
              {preview && <img src={preview} alt="Carte" className="mt-4 rounded-lg shadow max-h-48 mx-auto" />}
            </div>
            <div className="bg-white rounded-2xl shadow-md p-6">
              <h2 className="text-lg font-semibold mb-4">Instructions</h2>
              <ol className="list-decimal list-inside space-y-2 text-slate-700">
                <li>Positionnez la CIN à plat, bien éclairée</li>
                <li>Vérifiez que le NIN et la photo sont visibles</li>
                <li>Après OCR, corrigez les champs signalés en <em className="text-amber-600">orange</em></li>
                <li>Confirmez la photo avant validation finale</li>
              </ol>
              <p className="mt-4 text-sm text-slate-500 border-t pt-4">
                🇭🇹 <strong>Kreyòl:</strong> Tcheke tout enfòmasyon yo anvan ou anrejistre yo.
              </p>
            </div>
          </div>
        )}

        {step === 'validate' && (
          <div className="grid lg:grid-cols-2 gap-8">
            <div className="space-y-4">
              {preview && (
                <div className="bg-white rounded-2xl shadow-md p-4">
                  <img src={preview} alt="Carte scannée" className="w-full rounded-lg" />
                </div>
              )}
              {photo && (
                <div className="bg-white rounded-2xl shadow-md p-4">
                  <h3 className="font-medium mb-2">Photo extraite</h3>
                  <img src={photo} alt="Photo titulaire" className="w-40 h-40 object-cover rounded-lg mx-auto border-2 border-slate-200" />
                  <button onClick={() => setPhotoConfirmed(true)}
                    className={`mt-3 w-full py-2 rounded-lg text-sm font-medium ${photoConfirmed ? 'bg-green-100 text-green-800' : 'bg-slate-100 hover:bg-slate-200'}`}>
                    {photoConfirmed ? '✓ Photo confirmée' : 'Confirmer la correspondance visuelle'}
                  </button>
                </div>
              )}
            </div>

            <div className="bg-white rounded-2xl shadow-md p-6">
              <h2 className="text-lg font-semibold mb-2">Validation des données extraites</h2>
              {extractionStats && (
                <div className="mb-4 p-3 bg-blue-50 rounded-lg border border-blue-100">
                  <div className="flex justify-between text-sm mb-1">
                    <span className="text-slate-600">Extraction automatique (ROI ONI)</span>
                    <span className="font-semibold text-oni-blue">{extractionStats.extracted}/{extractionStats.expected} champs</span>
                  </div>
                  <div className="h-2 bg-slate-200 rounded-full overflow-hidden">
                    <div className="h-full bg-oni-blue transition-all" style={{ width: `${Math.min(100, (extractionStats.extracted / extractionStats.expected) * 100)}%` }} />
                  </div>
                  <p className="text-xs text-slate-500 mt-1">Confiance moyenne : {extractionStats.confidence?.toFixed(0)}% — corrigez les champs orange si besoin</p>
                </div>
              )}
              <div className="space-y-3 max-h-[60vh] overflow-y-auto pr-2">
                {FIELDS.map(({ key, label, required, placeholder }) => {
                  const meta = fieldMeta[key] || {}
                  const needsReview = meta.needsReview
                  return (
                    <div key={key}>
                      <label className="block text-sm font-medium text-slate-700 mb-1">
                        {label} {required && <span className="text-red-500">*</span>}
                        {needsReview && <span className="ml-2 text-amber-600 text-xs">(à vérifier — {meta.confidence?.toFixed(0)}%)</span>}
                      </label>
                      {key === 'departement' ? (
                        <select value={form[key] || ''} onChange={e => setForm({ ...form, [key]: e.target.value })}
                          className={`w-full border rounded-lg px-3 py-2 ${needsReview ? 'border-amber-400 bg-amber-50 italic' : 'border-slate-300'}`}>
                          <option value="">— Sélectionner —</option>
                          {DEPARTEMENTS.map(d => <option key={d} value={d}>{d}</option>)}
                        </select>
                      ) : (
                        <input value={form[key] || ''} onChange={e => setForm({ ...form, [key]: e.target.value })}
                          placeholder={placeholder}
                          className={`w-full border rounded-lg px-3 py-2 ${needsReview ? 'border-amber-400 bg-amber-50 italic' : 'border-slate-300'}`} />
                      )}
                    </div>
                  )
                })}
              </div>

              <div className="flex gap-3 mt-6">
                <button onClick={() => setShowConfirm(true)} disabled={loading || !photoConfirmed}
                  className="flex-1 flex items-center justify-center gap-2 bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white font-medium py-3 rounded-xl">
                  <CheckCircle className="w-5 h-5" /> Valider et enregistrer
                </button>
                <button onClick={handleCancel} disabled={loading}
                  className="flex items-center justify-center gap-2 bg-slate-200 hover:bg-slate-300 text-slate-800 font-medium py-3 px-5 rounded-xl">
                  <XCircle className="w-5 h-5" /> Annuler
                </button>
              </div>
            </div>
          </div>
        )}

        {showConfirm && (
          <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-2xl shadow-xl p-6 max-w-md w-full">
              <h3 className="text-lg font-bold mb-2">Confirmation finale</h3>
              <p className="text-slate-600 mb-4">Vérifiez une dernière fois le NIN <strong>{form.nin_display || form.nin}</strong> avant enregistrement définitif.</p>
              <div className="flex gap-3">
                <button onClick={handleValidate} disabled={loading}
                  className="flex-1 bg-oni-blue text-white py-2.5 rounded-xl font-medium">
                  {loading ? 'Enregistrement...' : 'Confirmer'}
                </button>
                <button onClick={() => setShowConfirm(false)} className="flex-1 bg-slate-200 py-2.5 rounded-xl">Retour</button>
              </div>
            </div>
          </div>
        )}

        {step === 'done' && success && (
          <div className="bg-green-50 border border-green-200 rounded-2xl p-8 max-w-2xl mx-auto">
            <CheckCircle className="w-16 h-16 text-green-600 mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-green-800 mb-2 text-center">{success}</h2>
            {savedRecord && (
              <div className="mt-6 space-y-4 text-left">
                <div className="bg-white rounded-xl p-4 border border-green-100">
                  <h3 className="font-semibold text-slate-800 mb-2">Titulaire enregistré</h3>
                  <p className="text-sm text-slate-600"><strong>{savedRecord.prenom} {savedRecord.nom}</strong> — NIN {savedRecord.ninDisplay || savedRecord.nin}</p>
                  {savedRecord.numeroCarte && <p className="text-sm text-slate-500">Carte n° {savedRecord.numeroCarte}</p>}
                </div>
                <div className="bg-white rounded-xl p-4 border border-green-100">
                  <h3 className="font-semibold text-slate-800 mb-2">Que s&apos;est-il passé ?</h3>
                  <ol className="list-decimal list-inside text-sm text-slate-600 space-y-1">
                    <li>Données validées et stockées en base (PostgreSQL / H2 local)</li>
                    <li>Photo archivée de façon chiffrée</li>
                    <li>Session de scan fermée (TTL expiré)</li>
                    <li>Événement d&apos;audit <code className="text-xs bg-slate-100 px-1 rounded">VALIDATION_CONFIRMED</code> enregistré</li>
                  </ol>
                </div>
                <div className="bg-white rounded-xl p-4 border border-green-100">
                  <h3 className="font-semibold text-slate-800 mb-2">Export vers systèmes tiers</h3>
                  <p className="text-sm text-slate-600">{savedRecord.nextStep}</p>
                  <p className="text-xs text-slate-400 mt-2">Clé démo : <code>X-API-Key: demo-api-key-2024</code></p>
                </div>
              </div>
            )}
            <button onClick={reset} className="mt-6 mx-auto block bg-oni-blue text-white px-6 py-2.5 rounded-xl">Nouvelle numérisation</button>
          </div>
        )}
      </main>

      <footer className="text-center text-xs text-slate-400 py-6">
        Microservice CIN v1.0 — Données personnelles protégées · Voir DISCLAIMER.md
      </footer>
    </div>
  )
}

export default App
