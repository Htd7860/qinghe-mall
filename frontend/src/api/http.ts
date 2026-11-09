export type CommonResult<T> = { code: number; data: T; msg: string };
export type PageResult<T> = { list: T[]; total: number };
export class ApiError extends Error {
  code: number;
  outcomeUnknown: boolean;
  requestId: string;
  constructor(
    message: string,
    code: number,
    outcomeUnknown = false,
    requestId = "",
  ) {
    super(message);
    this.code = code;
    this.outcomeUnknown = outcomeUnknown;
    this.requestId = requestId;
  }
}
type RequestContext = {
  tenantId?: string;
  accessToken?: string;
  timeoutMs?: number;
  signal?: AbortSignal;
  idempotencyKey?: string;
  base?: string;
};
export async function request<T>(
  path: string,
  context: RequestContext = {},
  init: RequestInit = {},
): Promise<T> {
  if (!path.startsWith("/") || path.startsWith("//") || /[\\\r\n]/.test(path))
    throw new ApiError("请求地址无效", -2);
  const base = context.base ?? "/api";
  if (!base.startsWith("/") || base.startsWith("//") || /[?#\\\r\n]/.test(base))
    throw new ApiError("服务配置无效", -2);
  const method = (init.method || "GET").toUpperCase(),
    write = !["GET", "HEAD"].includes(method);
  const headers = new Headers(init.headers);
  if (context.tenantId) headers.set("tenant-id", context.tenantId);
  headers.delete("Cookie");
  headers.delete("X-CSRF-Token");
  headers.delete("Authorization");
  if (context.accessToken) {
    if (
      context.accessToken.length > 128 ||
      /[\s,\x00-\x1f\x7f]/.test(context.accessToken)
    )
      throw new ApiError("登录凭据无效，请重新登录", 401);
    headers.set("Authorization", `Bearer ${context.accessToken}`);
  }
  if (context.idempotencyKey)
    headers.set("Idempotency-Key", context.idempotencyKey);
  if (init.body && !(init.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  headers.set("Accept", "application/json");
  const controller = new AbortController(),
    timer = setTimeout(() => controller.abort(), context.timeoutMs ?? 10000);
  const abort = () => controller.abort();
  context.signal?.addEventListener("abort", abort, { once: true });
  try {
    if (context.signal?.aborted) controller.abort();
    const response = await fetch(`${base.replace(/\/$/, "")}${path}`, {
      ...init,
      method,
      headers,
      credentials: "omit",
      cache: "no-store",
      redirect: "error",
      signal: controller.signal,
    });
    const requestId = response.headers.get("X-Request-Id") || "";
    if (!response.ok) {
      let message =
        response.status === 400
          ? "请求参数不正确，请检查后重试"
          : response.status === 401
            ? "登录已失效，请重新登录"
            : response.status === 403
              ? "没有操作权限"
              : response.status === 429
                ? "操作频繁，请稍后重试"
                : "服务暂不可用，请稍后重试";
      // 400只读取后端CommonResult中已脱敏的校验提示；5xx仍使用固定文案。
      if (
        response.status === 400 &&
        response.headers.get("Content-Type")?.includes("application/json")
      ) {
        const detail: unknown = await response.json().catch(() => null);
        if (detail && typeof detail === "object") {
          const { code, msg } = detail as { code?: unknown; msg?: unknown };
          if (
            code === 400 &&
            typeof msg === "string" &&
            msg.trim() &&
            msg.length <= 200
          )
            message = msg;
        }
      }
      // 开发环境只记录状态，不输出请求正文、凭据或查询参数。
      if (import.meta.env.DEV)
        console.warn("API request rejected; HTTP status:", response.status);
      throw new ApiError(
        message,
        response.status,
        write && response.status >= 500,
        requestId,
      );
    }
    if (!response.headers.get("Content-Type")?.includes("application/json"))
      throw new ApiError("服务响应异常，请稍后重试", -2, write, requestId);
    const body: unknown = await response.json();
    if (
      !body ||
      typeof body !== "object" ||
      !Number.isInteger((body as CommonResult<T>).code) ||
      !("data" in body)
    )
      throw new ApiError("服务响应异常，请稍后重试", -2, write, requestId);
    const result = body as CommonResult<T>;
    if (result.code !== 0)
      throw new ApiError(
        result.code === 401
          ? "登录已失效，请重新登录"
          : result.code === 403
            ? "没有操作权限"
            : result.msg || "操作未完成",
        result.code,
        write && result.code >= 500 && result.code <= 599,
        requestId,
      );
    return result.data;
  } catch (e) {
    if (e instanceof ApiError) throw e;
    throw new ApiError(
      write
        ? "连接中断，处理结果待确认，请先查询结果"
        : "连接失败，请检查网络后重试",
      -1,
      write,
    );
  } finally {
    clearTimeout(timer);
    context.signal?.removeEventListener("abort", abort);
  }
}
