package ht.oni.cin.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    @GetMapping("/")
    @ResponseBody
    public String home() {
        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>CIN Haïti — Microservice</title>
                  <style>
                    body { font-family: system-ui, sans-serif; max-width: 640px; margin: 3rem auto; padding: 0 1rem; color: #1e293b; }
                    h1 { color: #00209F; }
                    a { color: #00209F; }
                    .card { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 1.25rem; margin: 1rem 0; }
                    code { background: #e2e8f0; padding: 2px 6px; border-radius: 4px; }
                  </style>
                </head>
                <body>
                  <h1>🇭🇹 Microservice CIN — ONI</h1>
                  <p>Le backend fonctionne. Cette adresse expose l'<strong>API REST</strong>, pas l'interface de validation.</p>
                  <div class="card">
                    <strong>Interface opératrice (scan + validation)</strong><br/>
                    → <a href="http://localhost:5173" target="_blank">http://localhost:5173</a><br/>
                    <small>Lancez d'abord : <code>cd frontend && npm install && npm run dev</code></small>
                  </div>
                  <div class="card">
                    <strong>Documentation API (Swagger)</strong><br/>
                    → <a href="/swagger-ui/index.html">/swagger-ui/index.html</a>
                  </div>
                  <div class="card">
                    <strong>Test rapide</strong><br/>
                    → <a href="/api/v1/dev/token">/api/v1/dev/token</a> (jeton JWT démo)
                  </div>
                  <div class="card">
                    <strong>Santé du service</strong><br/>
                    → <a href="/actuator/health">/actuator/health</a>
                  </div>
                </body>
                </html>
                """;
    }
}
