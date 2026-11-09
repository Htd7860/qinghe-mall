# XXL-JOB starter（DEC-065/066）

择取本地yudao的YudaoXxlJobAutoConfiguration与XxlJobProperties，保留作者/MIT；依赖同上游xxl-job-core 2.4.0。仅注册执行器配置，不移植无关异步线程池，也不额外开启Spring定时扫描。

默认xxl.job.enabled=false，只有显式开启才绑定校验配置并注册执行器。启用须设置独立XXL_JOB_ACCESS_TOKEN、admin地址、executor端口/日志目录，凭据不进入toString；调度器地址仅HTTPS或本机回环HTTP。默认trade执行器端口18083，./logs/xxl-job为执行器任务日志目录，不替代应用日志。执行器发布IP不是网络访问隔离，生产仍须把XXL执行器端口放在内网/访问控制之内。

本批没有配置秘密、启动执行器、安装/启动调度中心或创建任务。未编译/测试；源/配置静态检查见mall-saas/docs/evidence/yudao-order-recovery-job-port.json。
