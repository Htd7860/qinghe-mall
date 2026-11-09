import { notify } from "./data/store";
export function exportCsv(
  name: string,
  headers: string[],
  rows: (string | number)[][],
) {
  const safe = (v: string | number) => {
    let s = String(v);
    if (/^[=+@\-]/.test(s)) s = `'${s}`;
    return `"${s.replaceAll('"', '""')}"`;
  };
  const blob = new Blob(
    [
      "\uFEFF" +
        [headers, ...rows].map((row) => row.map(safe).join(",")).join("\r\n"),
    ],
    { type: "text/csv;charset=utf-8" },
  );
  const url = URL.createObjectURL(blob),
    link = document.createElement("a");
  link.href = url;
  link.download = `${name}.csv`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  notify("文件已导出");
}
export const maskPhone = (s: string) =>
  s.replace(/^(\d{3})\d{4}(\d{4})$/, "$1****$2");
