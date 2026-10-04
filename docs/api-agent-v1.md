# API de l'agent dz-eid — v1

Base : `http://127.0.0.1:8989` (le port est configurable). L'agent n'écoute **que** sur la machine locale.

## Configuration (`%APPDATA%\dz-eid\config.json`)

```json
{
  "port": 8989,
  "allowedOrigins": ["https://app.mon-client.dz", "http://localhost:4200"],
  "preferredReader": "",
  "cardWaitSeconds": 20,
  "cscaDirectory": "",
  "convertPhotoToJpeg": true
}
```

| Clé | Description |
|---|---|
| `allowedOrigins` | Sites web autorisés à appeler l'agent. La page de démo locale est toujours autorisée. Le joker `*` est refusé. |
| `preferredReader` | Fragment du nom du lecteur à utiliser. Vide : le premier lecteur sans contact est choisi automatiquement. |
| `cardWaitSeconds` | Délai d'attente de la carte après l'appel à `/v1/read` |
| `cscaDirectory` | Dossier des certificats CSCA (`.cer`, `.crt`, `.der`, `.pem`) pour vérifier la chaîne de confiance |
| `convertPhotoToJpeg` | Convertit la photo JPEG2000 en JPEG, affichable dans tous les navigateurs |

Redémarrez l'agent après toute modification.

## `GET /v1/status`

```json
{ "product": "dz-eid-agent", "version": "0.1.0", "apiVersion": "1", "busy": false,
  "readers": [ { "name": "Identiv uTrust 3700 F CL Reader 0", "cardPresent": true } ],
  "preferredReader": "", "cscaCertificates": 0 }
```

## `POST /v1/read`

Corps de la requête : soit les 3 champs de la clé d'accès, soit la MRZ complète.

```json
{ "documentNumber": "123456789", "dateOfBirth": "850312", "dateOfExpiry": "310520",
  "readPhoto": true, "readSignature": true, "includeRaw": false }
```
```json
{ "mrz": "IDDZA1234567890<<<…\n8503127F3105208DZA<<<…\nBENALI<<AMINA<<<…" }
```

- Les dates sont acceptées en `AAMMJJ` ou `AAAA-MM-JJ`.
- `readPhoto` (défaut `true`) : photo DG2. `readSignature` (défaut `true`) : signature manuscrite DG7, si la carte en a une.
- Le contrôle anti-clonage (Active Authentication) est toujours effectué quand la carte possède un DG15 (+ ~0,3 s).
- Si aucune carte n'est posée, l'agent attend `cardWaitSeconds` secondes.
- Réponse `200` : un [`IdentityRecord`](contrat-identity-record-v1.md).

### Erreurs

```json
{ "error": { "code": "ACCESS_DENIED", "message": "Accès refusé par la puce : vérifiez le numéro du document, …" } }
```

| Code | HTTP | Cause | Action côté interface |
|---|---|---|---|
| `INVALID_INPUT` | 400 | Saisie incomplète ou mal formée | Corriger le formulaire |
| `ACCESS_DENIED` | 422 | La MRZ ne correspond pas à la carte | Vérifier la saisie |
| `NOT_EMRTD` | 422 | Ce n'est pas une carte d'identité électronique | — |
| `CARD_LOST` | 422 | Carte retirée pendant la lecture | Reposer la carte et réessayer |
| `READ_ERROR` | 422 | Erreur de communication | Réessayer |
| `NO_CARD` | 408 | Aucune carte posée dans le délai | Inviter à poser la carte |
| `BUSY` | 409 | Une lecture est déjà en cours | Patienter |
| `NO_READER` | 503 | Lecteur débranché | Brancher le lecteur |
| `INTERNAL` | 500 | Erreur inattendue | Consulter les journaux de l'agent |

Le champ `message` est en français et peut être affiché tel quel.

## `WS /v1/events`

Chaque message est un objet JSON contenant `type` et `timestamp`.

| `type` | Données | Quand |
|---|---|---|
| `HELLO` | `version`, `readers` | À la connexion |
| `READERS_CHANGED` | `readers` | Lecteur branché ou débranché |
| `CARD_INSERTED` / `CARD_REMOVED` | `reader` | Carte posée ou retirée |
| `WAITING_FOR_CARD` | `reader`, `timeoutSeconds` | Lecture demandée sans carte posée |
| `PROGRESS` | `step`, `percent`, `message` | Pendant la lecture |
| `READ_COMPLETED` | `durationMs` | Fin de lecture réussie (sans données personnelles) |
| `READ_FAILED` | `code`, `message` | Échec de lecture |
| `HEARTBEAT` | — | Toutes les 20 s (maintient la connexion) |

> [!NOTE]
> Les données d'identité ne transitent **jamais** par le WebSocket : elles ne sont renvoyées qu'en réponse à `POST /v1/read`, à l'appelant.
