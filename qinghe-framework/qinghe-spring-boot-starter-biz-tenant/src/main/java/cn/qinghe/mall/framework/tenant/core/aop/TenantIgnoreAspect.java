package cn.qinghe.mall.framework.tenant.core.aop;

import cn.qinghe.mall.framework.tenant.core.context.TenantContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

/** 方法级忽略租户：只作用于经过Spring代理的同步调用，结束后恢复原状态。 */
@Aspect
public class TenantIgnoreAspect {

    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final BeanFactory beanFactory;

    public TenantIgnoreAspect(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Around(value = "@annotation(tenantIgnore)", argNames = "joinPoint,tenantIgnore")
    public Object around(ProceedingJoinPoint joinPoint, TenantIgnore tenantIgnore) throws Throwable {
        boolean oldIgnore = TenantContextHolder.isIgnore();
        try {
            if (StringUtils.hasText(tenantIgnore.enable())) {
                StandardEvaluationContext context = new StandardEvaluationContext();
                context.setBeanResolver(new BeanFactoryResolver(beanFactory));
                Object enable = expressionParser.parseExpression(tenantIgnore.enable()).getValue(context);
                if (Boolean.TRUE.equals(enable)) {
                    TenantContextHolder.setIgnore(true);
                }
            }
            return joinPoint.proceed();
        } finally {
            TenantContextHolder.setIgnore(oldIgnore);
        }
    }
}
