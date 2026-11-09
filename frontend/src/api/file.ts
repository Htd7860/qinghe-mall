import { request, ApiError, type PageResult } from "./http";
import { context, credentials, identities, prefix, demo } from "./session";

export type FilePurpose = "product-image" | "store-logo" | "aftersale-evidence";
export type FileRecord = {
  id: number;
  name: string;
  type: string;
  size: number;
  purpose: FilePurpose;
  url: string | null;
  contentPath: string;
  createTime: string;
};

export function fileIdentityKey() {
  const identity = identities[context.realm];
  return `${context.realm}:${identity?.tenantId ?? ""}:${identity?.userId ?? ""}`;
}

function requireBackend() {
  if (demo || context.realm === "member" || !identities[context.realm])
    throw new ApiError("当前入口不支持文件管理", 403);
}

function unchanged(key: string) {
  if (key !== fileIdentityKey())
    throw new ApiError("登录身份已变化，请重新读取文件", 409);
}

function fileRecord(value: unknown): value is FileRecord {
  if (!value || typeof value !== "object") return false;
  const file = value as FileRecord;
  if (
    !Number.isSafeInteger(file.id) ||
    file.id <= 0 ||
    typeof file.name !== "string" ||
    typeof file.type !== "string" ||
    !Number.isSafeInteger(file.size) ||
    file.size <= 0 ||
    !["product-image", "store-logo", "aftersale-evidence"].includes(
      file.purpose,
    ) ||
    typeof file.contentPath !== "string" ||
    typeof file.createTime !== "string"
  )
    return false;
  if (file.purpose === "aftersale-evidence") return file.url === null;
  if (typeof file.url !== "string") return false;
  try {
    const url = new URL(file.url);
    return (
      ["http:", "https:"].includes(url.protocol) &&
      !url.username &&
      !url.password &&
      !url.search &&
      !url.hash &&
      /^\/public-api\/infra\/file\/\d+\/get\/tenant\/\d+\/(product-image|store-logo)\/\d{8}\/[a-f0-9]{32}\.(jpg|jpeg|png|gif|webp|bmp)$/.test(
        url.pathname,
      )
    );
  } catch {
    return false;
  }
}

export async function listFiles(pageNo: number) {
  requireBackend();
  const key = fileIdentityKey();
  const result: unknown = await request(
    `${prefix()}/infra/file/page?pageNo=${pageNo}&pageSize=10`,
    credentials(),
  );
  unchanged(key);
  const page = result as PageResult<FileRecord>;
  if (
    !page ||
    !Number.isSafeInteger(page.total) ||
    page.total < 0 ||
    !Array.isArray(page.list) ||
    !page.list.every(fileRecord)
  )
    throw new ApiError("文件列表数据不完整，请重新读取", -2);
  return page;
}

export async function uploadFile(file: File, purpose: FilePurpose) {
  requireBackend();
  const key = fileIdentityKey();
  const body = new FormData();
  body.append("file", file);
  body.append("purpose", purpose);
  const result: unknown = await request(
    `${prefix()}/infra/file/upload`,
    { ...credentials(), timeoutMs: 30000 },
    { method: "POST", body },
  );
  unchanged(key);
  if (!fileRecord(result))
    throw new ApiError("上传结果不完整，请先刷新文件列表", -2, true);
  return result;
}

export async function deleteFile(id: number) {
  requireBackend();
  const key = fileIdentityKey();
  if (!Number.isSafeInteger(id) || id <= 0)
    throw new ApiError("文件编号不正确", 400);
  const result = await request(
    `${prefix()}/infra/file/delete?id=${id}`,
    credentials(),
    { method: "DELETE" },
  );
  unchanged(key);
  if (result !== true)
    throw new ApiError("删除结果待确认，请刷新列表", -2, true);
}

export async function downloadFile(file: FileRecord) {
  requireBackend();
  const key = fileIdentityKey(),
    current = credentials();
  if (!current.accessToken || !Number.isSafeInteger(file.id) || file.id <= 0)
    throw new ApiError("请重新登录后下载", 401);
  const controller = new AbortController(),
    timer = setTimeout(() => controller.abort(), 15000);
  try {
    // 只向本站固定内容接口发Bearer，绝不向服务端返回的任意URL发送凭据。
    const response = await fetch(
      `/api${prefix()}/infra/file/content?id=${file.id}`,
      {
        headers: {
          Authorization: `Bearer ${current.accessToken}`,
          "tenant-id": String(current.tenantId),
        },
        credentials: "omit",
        cache: "no-store",
        redirect: "error",
        signal: controller.signal,
      },
    );
    if (!response.ok)
      throw new ApiError(
        response.status === 404
          ? "文件不存在或无权访问"
          : "文件读取失败，请稍后重试",
        response.status,
      );
    if (!response.headers.get("Content-Type")?.startsWith("image/"))
      throw new ApiError("文件响应格式不正确", -2);
    const blob = await response.blob();
    unchanged(key);
    if (blob.size !== file.size)
      throw new ApiError("文件内容不完整，请重新读取", -2);
    return blob;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError("文件读取失败，请稍后重试", -1);
  } finally {
    clearTimeout(timer);
  }
}
