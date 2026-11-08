# ClassWave dummy participants

This utility keeps exactly four dummy participants in lesson `6a0c3da19ebc43c3addefd38`:

- Γιώργος Παπαδόπουλος
- Μαρία Κωνσταντίνου
- Ελένη Νικολάου
- Δημήτρης Γεωργίου

Both join with their microphone muted and camera off. The utility never requests access to a real microphone or camera. A paused synthetic video producer is used only because the ClassWave Android UI needs a mediasoup producer to render a participant tile.

## Run

Start the ClassWave backend first, then run:

```powershell
cd dummy-participants
npm install
npm start
```

Keep the command running while the participants are needed. Press `Ctrl+C` to remove all four.

The default backend URL is `http://localhost:3000`. To use another URL:

```powershell
$env:CLASSWAVE_SERVER_URL = 'http://192.0.2.10:3000'
npm start
```

Chrome is detected automatically on Windows, Linux, and macOS. If needed, set `CHROME_PATH` to a Chrome or Edge executable.

## Verify once

With the backend running, this command joins all four participants, verifies their state, and exits (which removes them):

```powershell
npm run check
```
