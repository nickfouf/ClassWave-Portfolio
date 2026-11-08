import { build } from 'esbuild';
import { createServer } from 'node:http';
import { access, readFile } from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer-core';

const LESSON_ID = '6a06efd753ebdaf1a0fa0925';
const DUMMY_PARTICIPANTS = Object.freeze([
  { userId: `dummy-${LESSON_ID}-1`, username: 'Γιώργος Παπαδόπουλος' },
  { userId: `dummy-${LESSON_ID}-2`, username: 'Μαρία Κωνσταντίνου' },
  { userId: `dummy-${LESSON_ID}-3`, username: 'Ελένη Νικολάου' },
  { userId: `dummy-${LESSON_ID}-4`, username: 'Δημήτρης Γεωργίου' }
]);
const SERVER_URL = process.env.CLASSWAVE_SERVER_URL || 'http://localhost:3000';
const CHECK_MODE = process.argv.includes('--check');
const CHECK_TIMEOUT_MS = 30_000;

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectDirectory = path.resolve(currentDirectory, '..');
const outputDirectory = path.join(projectDirectory, 'dist');
const bundlePath = path.join(outputDirectory, 'dummy-client.js');
const htmlPath = path.join(currentDirectory, 'client.html');

const chromeCandidates = [
  process.env.CHROME_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
].filter(Boolean);

async function findChrome() {
  for (const candidate of chromeCandidates) {
    try {
      await access(candidate);
      return candidate;
    } catch {
    }
  }

  throw new Error('Chrome or Edge was not found. Set CHROME_PATH to the browser executable.');
}

async function createClientBundle() {
  await build({
    entryPoints: [path.join(currentDirectory, 'browser-client.js')],
    bundle: true,
    outfile: bundlePath,
    platform: 'browser',
    format: 'iife',
    target: ['chrome110'],
    sourcemap: false,
    logLevel: 'silent'
  });
}

function startStaticServer() {
  const server = createServer(async (request, response) => {
    try {
      if (request.url?.startsWith('/dummy-client.js')) {
        response.writeHead(200, { 'Content-Type': 'text/javascript; charset=utf-8' });
        response.end(await readFile(bundlePath));
        return;
      }

      response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      response.end(await readFile(htmlPath));
    } catch (error) {
      response.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
      response.end(error instanceof Error ? error.message : String(error));
    }
  });

  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

function localServerUrl(server) {
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('Unable to read the local server port');
  return `http://127.0.0.1:${address.port}`;
}

let browser;
let staticServer;
let shuttingDown = false;
let checkTimer;
const readyParticipants = new Set();

async function shutdown(exitCode = 0) {
  if (shuttingDown) return;
  shuttingDown = true;
  clearTimeout(checkTimer);

  await browser?.close().catch(() => {});
  await new Promise((resolve) => staticServer?.close(resolve) ?? resolve());
  process.exitCode = exitCode;
}

async function main() {
  await createClientBundle();
  staticServer = await startStaticServer();
  const chromePath = await findChrome();
  browser = await puppeteer.launch({
    executablePath: chromePath,
    headless: true,
    args: ['--autoplay-policy=no-user-gesture-required']
  });

  if (CHECK_MODE) {
    checkTimer = setTimeout(async () => {
      console.error(`Verification timed out after ${CHECK_TIMEOUT_MS / 1000} seconds.`);
      await shutdown(1);
    }, CHECK_TIMEOUT_MS);
  }

  for (const participant of DUMMY_PARTICIPANTS) {
    const page = await browser.newPage();
    await page.exposeFunction('reportDummyStatus', async (status) => {
      const suffix = status.detail ? ` (${status.detail})` : '';
      console.log(`[${status.username}] ${status.state}${suffix}`);

      if (status.state === 'ready') {
        readyParticipants.add(status.userId);
        if (CHECK_MODE && readyParticipants.size === DUMMY_PARTICIPANTS.length) {
          console.log('Verified: all four dummy participants joined with microphone and camera off.');
          await shutdown(0);
        }
      }
    });

    page.on('pageerror', (error) => console.error(`[${participant.username}] ${error.message}`));
    const parameters = new URLSearchParams({
      ...participant,
      roomId: LESSON_ID,
      serverUrl: SERVER_URL
    });
    await page.goto(`${localServerUrl(staticServer)}/?${parameters}`, { waitUntil: 'load' });
  }

  console.log(`Running exactly ${DUMMY_PARTICIPANTS.length} dummy participants in lesson ${LESSON_ID}.`);
  console.log(`Backend: ${SERVER_URL}`);
  if (!CHECK_MODE) console.log('Press Ctrl+C to remove all four dummy participants.');
}

process.once('SIGINT', () => shutdown(0));
process.once('SIGTERM', () => shutdown(0));

main().catch(async (error) => {
  console.error(error instanceof Error ? error.stack : error);
  await shutdown(1);
});
