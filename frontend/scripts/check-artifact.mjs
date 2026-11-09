import { readdir, readFile } from "node:fs/promises";
import assert from "node:assert/strict";
const names = await readdir(new URL("../dist/assets/", import.meta.url));
assert.ok(names.some(n => n.endsWith(".js")), "Production JS is missing");
for (const name of names) {
  assert.ok(!/^(seed|demo-auth|seckill-demo)-/.test(name), `Demo module leaked: ${name}`);
  if (!name.endsWith(".js")) continue;
  const source = await readFile(new URL(`../dist/assets/${name}`, import.meta.url), "utf8");
  for (const credential of ["Qinghe!2026", "Operator!2026", "Platform!2026", "DemoMember!2026"]) assert.ok(!source.includes(credential), `Demo credential leaked in ${name}`);
  assert.ok(!source.includes("预览店铺") && !source.includes("预览岗位"), `Identity switch leaked in ${name}`);
}
console.log("Production artifact: no seed module, demo credentials or identity selectors");
