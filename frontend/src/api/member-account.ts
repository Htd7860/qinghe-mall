import { ApiError, request } from "./http";
import { resolveStorefront } from "./storefront";

export type MemberCodePurpose = "register" | "reset-password";
export type MemberAccountInput = {
  tenant: string;
  mobile: string;
  password: string;
  code: string;
};

async function entry(tenant: string, mobile: string, registration: boolean) {
  if (import.meta.env.MODE === "prototype")
    throw new ApiError("演示环境不创建账号或发送短信", 400);
  if (!/^1[3-9][0-9]{9}$/.test(mobile))
    throw new ApiError("请填写有效手机号", 400);
  const store = await resolveStorefront(tenant);
  if (registration && !store.canPurchase)
    throw new ApiError("该商城暂不接受新会员注册", 403);
  return store.tenantCode;
}

export async function sendMemberCode(
  tenant: string,
  mobile: string,
  purpose: MemberCodePurpose,
) {
  const code = await entry(tenant, mobile, purpose === "register");
  const accepted = await request<boolean>(
    "/app-api/auth/send-code",
    {},
    {
      method: "POST",
      body: JSON.stringify({ tenant: code, mobile, purpose }),
    },
  );
  if (accepted !== true)
    throw new ApiError("验证码发送结果未确认，请勿立即重发", 503, true);
}

/** 账号变更成功不代表已登录；不在这里获取或保存Token，不自动重放写请求。 */
export async function submitMemberAccount(
  purpose: MemberCodePurpose,
  input: MemberAccountInput,
) {
  if (!/^[0-9]{6}$/.test(input.code))
    throw new ApiError("请输入六位验证码", 400);
  if (
    [...input.password].length < 8 ||
    new TextEncoder().encode(input.password).length > 72 ||
    /[\x00-\x1f\x7f-\x9f]/.test(input.password)
  )
    throw new ApiError(
      "密码至少8个字符，最多72个UTF-8字节，且不能含控制字符",
      400,
    );
  const tenant = await entry(
    input.tenant,
    input.mobile,
    purpose === "register",
  );
  const completed = await request<boolean>(
    `/app-api/auth/${purpose}`,
    {},
    {
      method: "POST",
      body: JSON.stringify({
        tenant,
        mobile: input.mobile,
        password: input.password,
        code: input.code,
      }),
    },
  );
  if (completed !== true)
    throw new ApiError("账号操作结果未确认，请先尝试登录", 503, true);
}
