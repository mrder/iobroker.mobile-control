# API- UND DATENVERTRAG

## 1. Versionierung

```text
/api/v1
/ws/v1
```

Jede App meldet:

- App-Version
- API-Version
- Plattform
- Geräte-ID
- Instanz-ID

---

## 2. Pairing

### `POST /api/v1/pairing/claim`

Request:

```json
{
  "pairingId": "id",
  "pairingSecret": "secret",
  "deviceName": "Galaxy S21",
  "platform": "android",
  "appVersion": "0.1.0",
  "publicKey": "base64"
}
```

Response:

```json
{
  "status": "waiting_for_approval",
  "claimId": "uuid"
}
```

### `GET /api/v1/pairing/status/{claimId}`

Status:

```text
waiting_for_approval
approved
rejected
expired
```

---

## 3. Auth

### `POST /api/v1/auth/challenge`

Request:

```json
{
  "deviceId": "uuid"
}
```

Response:

```json
{
  "challengeId": "uuid",
  "nonce": "base64",
  "expiresAt": "timestamp"
}
```

### `POST /api/v1/auth/login`

Request:

```json
{
  "deviceId": "uuid",
  "challengeId": "uuid",
  "signature": "base64"
}
```

Response:

```json
{
  "accessToken": "token",
  "refreshToken": "token",
  "expiresIn": 600,
  "user": {
    "id": "uuid",
    "name": "Sonny"
  }
}
```

### `POST /api/v1/auth/refresh`

Rotiert Refresh Token.

---

## 4. Objektkatalog

### `GET /api/v1/catalog`

Response:

```json
{
  "version": 42,
  "objects": [
    {
      "id": "public-object-uuid",
      "name": "Wohnzimmer Temperatur",
      "path": ["Wohnzimmer", "Klimasensor"],
      "role": "value.temperature",
      "valueType": "number",
      "unit": "°C",
      "read": true,
      "write": false,
      "history": true,
      "suggestedWidgets": ["temperature", "value", "chart"]
    }
  ]
}
```

---

## 5. States

### `GET /api/v1/states?ids=...`

Liefert aktuelle Werte öffentlicher Objekt-UUIDs.

---

## 6. Commands

### `POST /api/v1/commands`

Request:

```json
{
  "commandId": "uuid",
  "objectId": "public-object-uuid",
  "value": true,
  "timestamp": "timestamp",
  "nonce": "random"
}
```

Response:

```json
{
  "status": "accepted"
}
```

Spätere Status über WebSocket.

---

## 7. Dashboards

### `GET /api/v1/dashboards`

### `POST /api/v1/dashboards`

### `PUT /api/v1/dashboards/{id}`

### `DELETE /api/v1/dashboards/{id}`

Dashboard:

```json
{
  "id": "uuid",
  "name": "Zuhause",
  "revision": 7,
  "layouts": [
    {
      "sizeClass": "compact",
      "columns": 4,
      "widgets": []
    }
  ]
}
```

---

## 8. WebSocket-Nachrichten

Client:

```json
{
  "type": "subscribe",
  "objectIds": ["uuid-1", "uuid-2"]
}
```

Server:

```json
{
  "type": "state_update",
  "objectId": "uuid-1",
  "value": 22.4,
  "timestamp": "timestamp",
  "lastChange": "timestamp",
  "ack": true
}
```

Command Result:

```json
{
  "type": "command_result",
  "commandId": "uuid",
  "status": "confirmed"
}
```

---

## 9. Fehlercodes

```text
AUTH_REQUIRED
TOKEN_EXPIRED
DEVICE_REVOKED
SESSION_REVOKED
OBJECT_NOT_FOUND
READ_FORBIDDEN
WRITE_FORBIDDEN
VALUE_INVALID
CONFIRMATION_REQUIRED
LOCAL_ONLY
RATE_LIMITED
COMMAND_TIMEOUT
REVISION_CONFLICT
SERVER_UNAVAILABLE
```
