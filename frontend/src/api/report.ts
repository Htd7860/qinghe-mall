export type OverviewReport = {
  asOf: string;
  paymentAmount: number;
  paidCount: number;
  refundAmount: number;
  refundCount: number;
  memberCount: number;
  pendingShip: number;
  pendingAfterSale: number;
  lowStock: number;
  failedJobs: number;
  trend: { date: string; amount: number }[];
};
export function assertReport(value: unknown): asserts value is OverviewReport {
  if (!value || typeof value !== "object")
    throw new Error("经营数据暂不可用，请重试");
  const report = value as OverviewReport;
  for (const field of [
    "paymentAmount",
    "paidCount",
    "refundAmount",
    "refundCount",
    "memberCount",
    "pendingShip",
    "pendingAfterSale",
    "lowStock",
    "failedJobs",
  ] as const) {
    if (!Number.isSafeInteger(report[field]) || report[field] < 0)
      throw new Error("经营数据异常，请重试");
  }
  if (
    !/^\d{4}-\d{2}-\d{2}/.test(report.asOf) ||
    !Array.isArray(report.trend) ||
    ![7, 30].includes(report.trend.length) ||
    report.trend.some(
      (d) =>
        !/^\d{4}-\d{2}-\d{2}$/.test(d.date) ||
        !Number.isSafeInteger(d.amount) ||
        d.amount < 0,
    )
  )
    throw new Error("经营数据异常，请重试");
}
