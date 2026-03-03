package no.utgdev.serg.mock

internal fun adminPageHtml(maxGenerateBatch: Int): String =
    """
    <!doctype html>
    <html lang="nb">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>SERG Mock Admin</title>
        <style>
          :root {
            --bg: #f6f7f9;
            --card: #ffffff;
            --text: #1f2430;
            --muted: #5c6577;
            --border: #d9dee8;
            --primary: #1a5fb4;
            --danger: #b42318;
          }
          * { box-sizing: border-box; }
          body {
            margin: 0;
            font-family: "SF Mono", "Menlo", "Consolas", monospace;
            color: var(--text);
            background: linear-gradient(180deg, #eef2f8 0%, var(--bg) 38%);
          }
          main {
            max-width: 1080px;
            margin: 2rem auto;
            padding: 0 1rem 3rem;
            display: grid;
            gap: 1rem;
          }
          .panel {
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 1rem;
          }
          h1 { margin: 0 0 0.5rem 0; font-size: 1.6rem; }
          p { margin: 0; color: var(--muted); }
          .grid {
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 0.75rem;
            margin-top: 1rem;
          }
          .field {
            display: flex;
            flex-direction: column;
            gap: 0.4rem;
          }
          label { font-size: 0.85rem; color: var(--muted); }
          input, select, button {
            border: 1px solid var(--border);
            border-radius: 8px;
            padding: 0.6rem 0.75rem;
            font: inherit;
          }
          button {
            cursor: pointer;
            background: var(--primary);
            color: #fff;
            border: none;
          }
          button.danger { background: var(--danger); }
          .stats {
            display: grid;
            grid-template-columns: repeat(4, minmax(0, 1fr));
            gap: 0.75rem;
          }
          .stat {
            border: 1px dashed var(--border);
            border-radius: 8px;
            padding: 0.75rem;
          }
          .stat .value { font-size: 1.3rem; font-weight: 700; }
          table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.88rem;
          }
          th, td {
            text-align: left;
            border-bottom: 1px solid var(--border);
            padding: 0.45rem 0.35rem;
          }
          #status {
            margin-top: 0.6rem;
            color: var(--muted);
            min-height: 1.2rem;
          }
          @media (max-width: 900px) {
            .grid, .stats { grid-template-columns: 1fr 1fr; }
          }
          @media (max-width: 640px) {
            .grid, .stats { grid-template-columns: 1fr; }
            table { display: block; overflow-x: auto; }
          }
        </style>
      </head>
      <body>
        <main>
          <section class="panel">
            <h1>SERG mock admin (ikke produksjon)</h1>
            <p>Generer syntetiske hendelser og tilhorende formuesobjekt-data i minnet.</p>
            <div class="grid">
              <div class="field">
                <label for="count">Antall hendelser</label>
                <input id="count" type="number" value="100" min="1" max="$maxGenerateBatch" step="1">
              </div>
              <div class="field">
                <label for="preset">Preset</label>
                <select id="preset">
                  <option value="BALANCED">BALANCED</option>
                  <option value="NEW_HEAVY">NEW_HEAVY</option>
                  <option value="DELETE_HEAVY">DELETE_HEAVY</option>
                </select>
              </div>
              <div class="field">
                <label>&nbsp;</label>
                <button id="generate" type="button">Generer</button>
              </div>
              <div class="field">
                <label>&nbsp;</label>
                <button id="clear" type="button" class="danger">Clear all</button>
              </div>
            </div>
            <div id="status"></div>
          </section>

          <section class="panel">
            <div class="stats">
              <div class="stat"><div>Total hendelser</div><div class="value" id="total">0</div></div>
              <div class="stat"><div>Neste sekvens</div><div class="value" id="next">1</div></div>
              <div class="stat"><div>Forste sekvens</div><div class="value" id="first">-</div></div>
              <div class="stat"><div>Siste sekvens</div><div class="value" id="last">-</div></div>
            </div>
          </section>

          <section class="panel">
            <h2>Siste hendelser</h2>
            <table>
              <thead>
                <tr>
                  <th>Sekvens</th>
                  <th>HendelseID</th>
                  <th>Type</th>
                  <th>Tidspunkt</th>
                  <th>Kommune</th>
                </tr>
              </thead>
              <tbody id="rows"></tbody>
            </table>
          </section>
        </main>

        <script>
          const statusEl = document.getElementById('status');
          const countEl = document.getElementById('count');
          const presetEl = document.getElementById('preset');
          const rowsEl = document.getElementById('rows');
          const totalEl = document.getElementById('total');
          const nextEl = document.getElementById('next');
          const firstEl = document.getElementById('first');
          const lastEl = document.getElementById('last');

          function setStatus(message) {
            statusEl.textContent = message;
          }

          function renderState(state) {
            totalEl.textContent = state.totalEvents;
            nextEl.textContent = state.nextSequence;
            firstEl.textContent = state.firstSeq ?? '-';
            lastEl.textContent = state.lastSeq ?? '-';

            rowsEl.innerHTML = '';
            for (const row of state.recent) {
              const tr = document.createElement('tr');
              tr.innerHTML = `<td>${'$'}{row.sekvensnummer}</td><td>${'$'}{row.hendelseidentifikator}</td><td>${'$'}{row.hendelsestype}</td><td>${'$'}{row.registreringstidspunkt}</td><td>${'$'}{row.kommunenummer ?? ''}</td>`;
              rowsEl.appendChild(tr);
            }
          }

          async function fetchState() {
            const response = await fetch('/admin/api/state');
            const json = await response.json();
            renderState(json);
          }

          async function generate() {
            const count = Number(countEl.value);
            const preset = presetEl.value;
            setStatus(`Genererer ${'$'}{count} hendelser med preset ${'$'}{preset}...`);

            const response = await fetch('/admin/api/generate', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ count, preset }),
            });
            const json = await response.json();
            if (!response.ok) {
              throw new Error(json.message || 'Generate feilet');
            }
            setStatus(`Genererte ${'$'}{json.generated} hendelser. Siste sekvens: ${'$'}{json.lastSeq ?? '-'}`);
            await fetchState();
          }

          async function clearAll() {
            setStatus('Sletter alle hendelser...');
            const response = await fetch('/admin/api/clear', { method: 'POST' });
            const json = await response.json();
            if (!response.ok) {
              throw new Error(json.message || 'Clear feilet');
            }
            setStatus(`Slettet ${'$'}{json.clearedEvents} hendelser.`);
            await fetchState();
          }

          document.getElementById('generate').addEventListener('click', () => {
            generate().catch((error) => setStatus(`Feil: ${'$'}{error}`));
          });

          document.getElementById('clear').addEventListener('click', () => {
            clearAll().catch((error) => setStatus(`Feil: ${'$'}{error}`));
          });

          fetchState().catch((error) => setStatus(`Feil ved innlasting av state: ${'$'}{error}`));
        </script>
      </body>
    </html>
    """.trimIndent()
