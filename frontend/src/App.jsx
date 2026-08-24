import { useState, useRef, useCallback, useEffect } from 'react'
import { Upload, CheckCircle, XCircle, AlertTriangle, X } from 'lucide-react'

const API = '/api/v1'
const FIELDS = [
  { key: 'numero_carte', label: 'Numéro de carte', required: true },
  { key: 'prenom', label: 'Prénom(s)', required: true },
  { key: 'nom', label: 'Nom de famille', required: true },
  { key: 'sexe', label: 'Sexe (M/F)', required: true },
  { key: 'nationalite', label: 'Nationalité', required: true, placeholder: 'HTI' },
  { key: 'date_naissance', label: 'Date de naissance', required: true, placeholder: 'JJ/MM/AAAA' },
  { key: 'lieu_naissance', label: 'Lieu de naissance', required: true, placeholder: 'Département Ouest, Commune Port-au-Prince' },
  { key: 'date_emission', label: "Date d'émission", required: true, placeholder: 'JJ/MM/AAAA' },
  { key: 'date_expiration', label: "Date d'expiration", required: true, placeholder: 'JJ/MM/AAAA' },
  { key: 'nin_display', label: 'NIN (numéro sur la carte)', required: true, placeholder: '0123456789' },
]

const STEPS = [
  { key: 'scan', label: 'Numérisation' },
  { key: 'validate', label: 'Validation' },
  { key: 'done', label: 'Confirmation' },
]

// Mode embarqué : une app hôte (ex. EPS) charge ce widget dans une iframe avec ?embed=1
// (affiche un bouton de fermeture au lieu du reste du chrome hôte, qu'elle fournit déjà)
// et optionnellement ?token=... pour sauter l'écran de connexion si elle gère elle-même
// l'émission du jeton opératrice.
const urlParams = new URLSearchParams(window.location.search)
const EMBED = urlParams.get('embed') === '1'
const EXTERNAL_TOKEN = urlParams.get('token')

// Contrat postMessage minimal pour l'intégration en iframe : l'app hôte peut écouter ces
// évènements (window.addEventListener('message', ...)) pour réagir au cycle de vie du
// widget sans avoir à parser son DOM — ready au montage, validated avec l'enregistrement
// sauvegardé, cancelled/closed sur abandon, error en cas d'échec affiché à l'opératrice.
function notifyHost(type, payload) {
  if (window.parent !== window) {
    window.parent.postMessage({ source: 'cin-haiti-widget', type, payload }, '*')
  }
}

function Stepper({ current }) {
  const index = STEPS.findIndex(s => s.key === current)
  if (index === -1) return null
  return (
    <ol className="flex items-center justify-center gap-2 sm:gap-4 mb-6 sm:mb-8">
      {STEPS.map((step, i) => {
        const state = i < index ? 'done' : i === index ? 'current' : 'upcoming'
        return (
          <li key={step.key} className="flex items-center gap-2 sm:gap-4">
            <div className="flex items-center gap-2">
              <span className={`flex items-center justify-center w-7 h-7 sm:w-8 sm:h-8 rounded-full text-xs sm:text-sm font-semibold shrink-0 transition-colors ${
                state === 'done' ? 'bg-oni-blue text-white'
                : state === 'current' ? 'bg-white text-oni-blue border-2 border-oni-blue ring-4 ring-oni-blue/15'
                : 'bg-slate-200 text-slate-500'}`}>
                {state === 'done' ? <CheckCircle className="w-4 h-4" /> : i + 1}
              </span>
              <span className={`hidden sm:inline text-sm font-medium ${state === 'upcoming' ? 'text-slate-400' : 'text-slate-700'}`}>
                {step.label}
              </span>
            </div>
            {i < STEPS.length - 1 && <span className="w-6 sm:w-10 h-px bg-slate-300" />}
          </li>
        )
      })}
    </ol>
  )
}

function App() {
  const [token, setToken] = useState(EXTERNAL_TOKEN || localStorage.getItem('cin_token') || '')
  const [session, setSession] = useState(null)
  const [form, setForm] = useState({})
  const [fieldMeta, setFieldMeta] = useState({})
  const [photo, setPhoto] = useState(null)
  const [preview, setPreview] = useState(null)
  const [idempotencyToken, setIdempotencyToken] = useState(null)
  const [step, setStep] = useState(EXTERNAL_TOKEN ? 'scan' : (token ? 'scan' : 'auth'))
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(null)
  const [savedRecord, setSavedRecord] = useState(null)
  const [extractionStats, setExtractionStats] = useState(null)
  const [showConfirm, setShowConfirm] = useState(false)
  const [photoConfirmed, setPhotoConfirmed] = useState(false)
  const fileRef = useRef(null)

  useEffect(() => {
    notifyHost('ready', {})
    if (EXTERNAL_TOKEN) {
      localStorage.setItem('cin_token', EXTERNAL_TOKEN)
    }
  }, [])

  useEffect(() => {
    if (error) notifyHost('error', { message: error })
  }, [error])

  const headers = useCallback(() => ({
    Authorization: `Bearer ${token}`,
    ...(loading ? {} : {})
  }), [token, loading])

  const fetchToken = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(`${API}/dev/token?operatorId=operatrice-demo`)
      const data = await res.json().catch(() => ({}))
      if (!res.ok || !data.token) {
        throw new Error(data.message || 'Jeton de démo indisponible — passez un token via ?token= (IAM de votre app)')
      }
      setToken(data.token)
      localStorage.setItem('cin_token', data.token)
      setStep('scan')
    } catch (e) {
      setError(e.message || 'Pa kapab konekte ak sèvè a / Impossible de se connecter au serveur')
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
      expected: data.fieldsExpected ?? 10,
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
      notifyHost('validated', data)
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
    notifyHost('cancelled', {})
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
    <div className="min-h-screen bg-transparent flex flex-col">
      {EMBED && (
        <div className="flex justify-end px-3 pt-3 sm:px-4 sm:pt-4">
          <button onClick={handleCancel} aria-label="Fermer" className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* flex-1 + items-center centre le contenu verticalement et horizontalement sur tout
          écran ; py-* garde de l'air quand une étape (validation, 2 colonnes) dépasse la
          hauteur de la fenêtre, pour que ça défile proprement au lieu d'être tronqué en haut. */}
      <main className="flex-1 w-full max-w-5xl mx-auto px-4 sm:px-6 py-6 sm:py-8 flex flex-col items-center justify-center">
        <div className="w-full">
          <Stepper current={step === 'auth' ? 'scan' : step} />

        {error && (
          <div className="mb-6 flex items-start gap-2 bg-red-50 border border-red-200 text-red-800 px-4 py-3 rounded-xl">
            <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
            <span className="text-sm">{error}</span>
          </div>
        )}

        {step === 'auth' && (
          <div className="bg-white rounded-2xl shadow-card p-6 sm:p-8 max-w-md mx-auto text-center">
            <h2 className="text-xl sm:text-2xl font-semibold mb-6">Connexion opératrice</h2>
            <button onClick={fetchToken} disabled={loading}
              className="w-full bg-oni-blue hover:bg-oni-blueDark disabled:opacity-50 text-white font-medium py-3 px-6 rounded-xl transition-colors">
              {loading ? 'Connexion...' : 'Obtenir jeton de démonstration'}
            </button>
          </div>
        )}

        {step === 'scan' && (
          <div className="bg-white rounded-2xl shadow-card p-5 sm:p-6 max-w-xl mx-auto">
            {!session ? (
              <button onClick={openSession} disabled={loading}
                className="w-full bg-oni-blue hover:bg-oni-blueDark disabled:opacity-50 text-white font-semibold py-3 rounded-xl mb-4 transition-colors">
                Ouvrir une session de scan
              </button>
            ) : (
              <div
                className={`relative border-2 border-dashed rounded-xl text-center cursor-pointer transition-colors overflow-hidden flex items-center justify-center aspect-[4/3] sm:aspect-video ${
                  preview ? 'border-slate-200 bg-slate-50' : 'border-slate-300 p-6 sm:p-8 hover:border-oni-blue hover:bg-slate-50/60'}`}
                onClick={() => fileRef.current?.click()}
              >
                {preview ? (
                  <img src={preview} alt="Carte importée" className="w-full h-full object-contain" />
                ) : (
                  <div>
                    <Upload className="w-10 h-10 sm:w-12 sm:h-12 mx-auto text-slate-400 mb-3" />
                    <p className="font-medium text-sm sm:text-base">Importer une image (JPEG, PNG)</p>
                    <p className="text-xs sm:text-sm text-slate-500 mt-1">ou glisser-déposer · min. 300 DPI recommandé</p>
                  </div>
                )}
              </div>
            )}
            <input ref={fileRef} type="file" accept="image/*" className="hidden"
              onChange={e => e.target.files?.[0] && uploadImage(e.target.files[0])} />
            {preview && (
              <p className="text-xs text-slate-500 text-center mt-3">Cliquez sur l'image pour en importer une autre</p>
            )}
          </div>
        )}

        {step === 'validate' && (
          <div className="grid lg:grid-cols-2 gap-6 md:gap-8">
            <div className="space-y-4 order-2 lg:order-1">
              {preview && (
                <div className="bg-white rounded-2xl shadow-card p-4">
                  <div className="rounded-lg bg-slate-50 aspect-[4/3] sm:aspect-video flex items-center justify-center overflow-hidden">
                    <img src={preview} alt="Carte scannée" className="w-full h-full object-contain" />
                  </div>
                </div>
              )}
              {photo && (
                <div className="bg-white rounded-2xl shadow-card p-4">
                  <img src={photo} alt="Photo titulaire" className="w-40 h-40 object-cover rounded-lg mx-auto border-2 border-slate-200" />
                  <button onClick={() => setPhotoConfirmed(true)}
                    className={`mt-3 w-full py-2 rounded-lg text-sm font-medium transition-colors ${photoConfirmed ? 'bg-green-100 text-green-800' : 'bg-slate-100 hover:bg-slate-200'}`}>
                    {photoConfirmed ? '✓ Photo confirmée' : 'Confirmer la correspondance visuelle'}
                  </button>
                </div>
              )}
            </div>

            <div className="bg-white rounded-2xl shadow-card p-5 sm:p-6 order-1 lg:order-2">
              {extractionStats && (
                <div className="mb-4 p-3 bg-blue-50 rounded-xl border border-blue-100">
                  <div className="flex justify-between text-sm mb-1">
                    <span className="text-slate-600">Champs extraits</span>
                    <span className="font-semibold text-oni-blue">{extractionStats.extracted}/{extractionStats.expected}</span>
                  </div>
                  <div className="h-2 bg-slate-200 rounded-full overflow-hidden">
                    <div className="h-full bg-oni-blue transition-all" style={{ width: `${Math.min(100, (extractionStats.extracted / extractionStats.expected) * 100)}%` }} />
                  </div>
                </div>
              )}
              <div className="space-y-3 max-h-[60vh] overflow-y-auto pr-1 -mr-1">
                {FIELDS.map(({ key, label, required, placeholder }) => {
                  const meta = fieldMeta[key] || {}
                  const needsReview = meta.needsReview
                  return (
                    <div key={key}>
                      <label className="block text-sm font-medium text-slate-700 mb-1">
                        {label} {required && <span className="text-red-500">*</span>}
                        {needsReview && <span className="ml-2 text-amber-600 text-xs">(à vérifier — {meta.confidence?.toFixed(0)}%)</span>}
                      </label>
                      <input value={form[key] || ''} onChange={e => setForm({ ...form, [key]: e.target.value })}
                        placeholder={placeholder}
                        className={`w-full border rounded-lg px-3 py-2 text-sm sm:text-base focus:outline-none focus:ring-2 focus:ring-oni-blue/30 focus:border-oni-blue transition-colors ${needsReview ? 'border-amber-400 bg-amber-50 italic' : 'border-slate-300'}`} />
                    </div>
                  )
                })}
              </div>

              <div className="flex flex-col sm:flex-row gap-3 mt-6">
                <button onClick={() => setShowConfirm(true)} disabled={loading || !photoConfirmed}
                  className="flex-1 flex items-center justify-center gap-2 bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white font-medium py-3 rounded-xl transition-colors">
                  <CheckCircle className="w-5 h-5" /> Valider et enregistrer
                </button>
                <button onClick={handleCancel} disabled={loading}
                  className="flex items-center justify-center gap-2 bg-slate-100 hover:bg-slate-200 text-slate-800 font-medium py-3 px-5 rounded-xl transition-colors">
                  <XCircle className="w-5 h-5" /> Annuler
                </button>
              </div>
            </div>
          </div>
        )}

        {showConfirm && (
          <div className="fixed inset-0 bg-slate-900/50 flex items-center justify-center z-50 p-4">
            <div className="bg-white rounded-2xl shadow-xl p-6 max-w-md w-full">
              <h3 className="text-lg font-bold mb-2">Confirmation finale</h3>
              <p className="text-slate-600 mb-4 text-sm sm:text-base">Vérifiez une dernière fois le NIN <strong>{form.nin_display || form.nin}</strong> avant enregistrement définitif.</p>
              <div className="flex gap-3">
                <button onClick={handleValidate} disabled={loading}
                  className="flex-1 bg-oni-blue hover:bg-oni-blueDark disabled:opacity-50 text-white py-2.5 rounded-xl font-medium transition-colors">
                  {loading ? 'Enregistrement...' : 'Confirmer'}
                </button>
                <button onClick={() => setShowConfirm(false)} className="flex-1 bg-slate-100 hover:bg-slate-200 py-2.5 rounded-xl transition-colors">Retour</button>
              </div>
            </div>
          </div>
        )}

        {step === 'done' && success && (
          <div className="bg-green-50 border border-green-200 rounded-2xl p-6 sm:p-8 max-w-md mx-auto">
            <CheckCircle className="w-14 h-14 sm:w-16 sm:h-16 text-green-600 mx-auto mb-4" />
            <h2 className="text-xl sm:text-2xl font-bold text-green-800 mb-2 text-center">{success}</h2>
            {savedRecord && (
              <div className="mt-6 bg-white rounded-xl p-4 border border-green-100 text-left">
                <p className="text-sm text-slate-600"><strong>{savedRecord.prenom} {savedRecord.nom}</strong> — NIN {savedRecord.ninDisplay || savedRecord.nin}</p>
                {savedRecord.numeroCarte && <p className="text-sm text-slate-500">Carte n° {savedRecord.numeroCarte}</p>}
              </div>
            )}
            <button onClick={reset} className="mt-6 mx-auto block bg-oni-blue hover:bg-oni-blueDark text-white px-6 py-2.5 rounded-xl transition-colors">Nouvelle numérisation</button>
          </div>
        )}
        </div>
      </main>
    </div>
  )
}

export default App
