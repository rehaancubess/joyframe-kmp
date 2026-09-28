// A narrow accidental-disclosure guard, not a substitute for reviewing a release.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const files = [...new Set(execFileSync('git', ['ls-files', '-co', '--exclude-standard'], { encoding: 'utf8' }).trim().split('\n'))].filter(Boolean);
const findings = [];
for (const file of files) {
  if (file === 'tools/audit-public-source.mjs') continue;
  // Rendered screenshots and animations for the docs only. Models and audio stay prohibited everywhere.
  if (/^docs\/media\/[\w.-]+\.(png|gif)$/i.test(file)) continue;
  if (/\.(glb|gltf|png|jpg|wav|mp3|keystore|jks|p12|mobileprovision|asc)$/i.test(file) || /(^|\/)(google-services\.json|GoogleService-Info\.plist|local\.properties|\.env.*)$/.test(file)) {
    findings.push(`${file}: unexpected asset or private configuration`);
    continue;
  }
  if (file.endsWith('.jar')) {
    if (file !== 'gradle/wrapper/gradle-wrapper.jar') findings.push(`${file}: unexpected binary`);
    continue;
  }
  const content = readFileSync(file, 'utf8');
  const checks = [
    [/AIza[0-9A-Za-z_-]{30,}/, 'possible API key'],
    [/gh[pousr]_[0-9A-Za-z]{20,}/, 'possible GitHub token'],
    [/-----BEGIN (?:RSA |OPENSSH |EC |PGP )?PRIVATE KEY/, 'private key'],
    [/\/Users\/[^\s/]+\//, 'absolute user path'],
  ];
  if (/\.(kt|kts)$/.test(file)) checks.push([/io\.brawlkarts|GameSnapshot|RevenueCat|FirebaseAuth/, 'private app dependency']);
  for (const [pattern, label] of checks) if (pattern.test(content)) findings.push(`${file}: ${label}`);
}
if (findings.length) { console.error(findings.join('\n')); process.exitCode = 1; }
else console.log(`Checked ${files.length} public-source candidates: no prohibited assets, app imports or credential patterns found.`);
