# 会员服务源码复用

本批从本地 yudao-cloud/yudao-module-member 选择基础会员身份和本人收货地址。原MIT许可与作者保留，见../third-party/yudao-cloud/LICENSE。范围/源码/已执行SQL详见../../mall-saas/docs/design/MEMBER-ADDRESS-REUSE.md。

api提供地址快照DTO；server有独立应用/本地配置、真实租户会员模型与地址CRUD。消费者认证/验证码/店铺入口以及真实MEMBER策略尚未接入，Gateway无地址公开路由；未启动/编译或执行接口测试。不能把数据库空表和Controller源码称为完整会员模块可用。


2026-10-28资格后续：新增仅专用服务身份可读的会员资格POST及system MEMBER策略接线；QINGHE_SYSTEM_MEMBER_TOKEN默认空、未配置，不能凭普通用户Token调用此端点。普通业务继续原Bearer链。消费者认证/验证码/签发和地址公开路由仍待，未构建/运行验证，详见正式工作区MEMBER-QUALIFICATION-REUSE.md。


2026-10-28：新增内部密码认证候选与默认DISABLED的短信渠道源码；Aliyun签名取原版，TEST只有显式sms-test+回环绑定可用，无公开验证码读取或真实短信发送。次数/过期/一次性消费及注册/重置/最终签发仍待，详见正式工作区MEMBER-PASSWORD-SMS-CHANNEL-REUSE.md。


2026-10-28验证码：新增号码级共享额度、发送结果状态及一次性消费Service，V002两空表已落，Gateway仍无公开短信路径。注册/重置须通过useSmsCode的本地事务回调接入，单独validate不是授权票据；无真实SMS/运行验收，见MEMBER-SMS-CODE-REUSE.md。

2026-10-29：内部MemberUserService已接REGISTER/RESET_PASSWORD验证码回调，注册与凭据版本重置/成功审计/验证码消费同库事务；V003审计空表已创建。无公开注册找回/Token、无真实写入或运行验证，见正式工作区MEMBER-REGISTRATION-RESET-REUSE.md。

2026-10-29 system侧已补仅会员服务可调用的内部Token签发契约/实现，但本member尚未接调用方，凭据未配置，没有消费者登录完成或Token实际生成。下一段固定来源/有界客户端和密码→签发接线，见正式工作区MEMBER-TOKEN-ISSUE-REUSE.md。

2026-10-29会员侧已新增内部MemberAuthService.login及system-api有界签发客户端，配置QINGHE_MEMBER_SYSTEM_TOKEN仍为空；不会把密码/验证码传入system。没有公开HTTP、登录记录、真实Token或运行验收，见正式工作区MEMBER-LOGIN-ISSUE-REUSE.md。

2026-10-29：新增MemberStorefrontClient和MemberStorefrontAuthService，实际唯一Host服务器解析后再登录，返回前复核入口归属/版本；system内部查询复用原StorefrontService，凭据仍空、无公开认证或实际调用。现有login(tenantId,...)仅供目标已权威确认的内部调用，不直接绑定浏览器tenantId。见正式工作区MEMBER-STOREFRONT-AUTH-REUSE.md。

2026-10-29：内部入口登录已包裹共享账号失败限额，member新增Redis starter/连接配置，默认5次/900秒；没有实际Redis或公开429验证。内部资格/签发已知401按HTTP200业务体返回，服务凭据错误保留非2xx。日志与最近登录仍待，见正式工作区MEMBER-LOGIN-PROTECTION-REUSE.md。

2026-10-29：入口包装已接私有登录记录及最近登录更新，SUCCESS与元数据在member库事务内，system签发先提交不作跨库回滚承诺。V004日志空表已创建，没有真实账号/Token/日志或运行验证；明文对端仅账号私有元数据，日志只存摘要，见正式工作区MEMBER-LOGIN-HISTORY-REUSE.md。

2026-10-29：已新增密码POST /app-api/auth/login与受保护GET /app-api/auth/session源码，Gateway仅这两条会员认证路径放行；前端手机字段显式转换，失败限额429附Retry-After。未构建/重启/实际HTTP，凭据仍空，无真实会员初始化；注册/短信/刷新/退出仍未开放。内部LocalDateTime协议要求与system/gateway同一JVM时区，运行部署待验收；见正式工作区MEMBER-LOGIN-SESSION-HTTP-REUSE.md。

2026-10-29 system侧内部会员refresh/logout源码与契约已补，保留母会话期限/原refresh，退出复用通用撤销；member客户端/public Controller/过期logout过滤器和前端仍未接，不能称浏览器生命周期可用。现有pair期限校验允许access=refresh截止，见正式工作区MEMBER-TOKEN-LIFECYCLE-SYSTEM-REUSE.md。


2026-10-29会员生命周期源码：POST /app-api/auth/refresh-token?store=编码（JSON refreshToken，无access）与POST /app-api/auth/logout（原access Bearer）已通过固定服务客户端接system；刷新重新解析实际Host/原店铺并核对绝对期限，退出允许过期凭据直接撤销固定MEMBER会话。Gateway154精确method/path，未开放其他认证接口。仅静态检查，未编译/启动/HTTP/真实刷新退出，服务密钥未配；注册找回短信、登出业务审计/清理与运行验收仍待。详见../../mall-saas/docs/design/MEMBER-TOKEN-LIFECYCLE-HTTP-REUSE.md。
