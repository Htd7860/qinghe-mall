import { db } from "./store";
import { identities } from "../api/session";
import type { Identity, Realm } from "../api/policy";
// Imported only by the explicit prototype build. These are fictional demo accounts.
export function demoSignIn(
  realm: Realm,
  tenant: string,
  username: string,
  password: string,
): Identity {
  let s: Identity;
  if (realm === "platform") {
    if (username !== "operator" || password !== "Platform!2026")
      throw new Error("账号或密码不正确");
    s = {
      realm,
      userId: 999,
      name: "平台运营",
      tenantId: "",
      tenantCode: "",
      role: "平台运营",
      permissions: ["tenants", "packages", "domains", "audit", "seckill"],
      expiresAt: Date.now() + 3600000,
    };
  } else if (realm === "merchant") {
    const staff = db.staff.find(
      (x) =>
        x.tenant === tenant && x.username === username && x.status === "正常",
    );
    const role = db.roles.find(
      (x) =>
        x.tenant === tenant && x.name === staff?.role && x.status === "正常",
    );
    if (
      !staff ||
      !role ||
      password !== (username === "admin" ? "Qinghe!2026" : "Operator!2026")
    )
      throw new Error("商家编码、账号或密码不正确");
    s = {
      realm,
      userId: staff.id,
      name: staff.name,
      tenantId: tenant,
      tenantCode: tenant,
      role: staff.role,
      permissions: [...role.permissions],
      expiresAt: Date.now() + 3600000,
    };
  } else {
    const member = db.members.find(
      (m) => m.tenant === tenant && m.phone === username,
    );
    if (!member || password !== "DemoMember!2026")
      throw new Error("账号或密码不正确");
    s = {
      realm,
      userId: member.id,
      name: member.name,
      tenantId: tenant,
      tenantCode: tenant,
      role: "会员",
      permissions: [],
      expiresAt: Date.now() + 3600000,
    };
  }
  identities[realm] = s;
  return s;
}
