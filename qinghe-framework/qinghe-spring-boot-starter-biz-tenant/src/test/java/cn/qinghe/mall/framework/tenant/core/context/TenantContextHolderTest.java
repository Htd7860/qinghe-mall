package cn.qinghe.mall.framework.tenant.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 助手按 DEC-035 编写，用户自行运行。
 * 验证上下文契约和调用方 finally 清理后的线程复用，不验证 SQL 隔离或 TTL 传播。
 */
class TenantContextHolderTest {

    @BeforeEach
    void setUp() {
        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("空上下文没有租户编号，默认不忽略租户过滤")
    void emptyContextShouldNotHaveTenantOrBypassFiltering() {
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test
    @DisplayName("缺少租户编号时必需读取明确失败")
    void requiredTenantShouldRejectMissingContext() {
        assertThrows(IllegalStateException.class, TenantContextHolder::getRequiredTenantId);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    @DisplayName("切换租户后读取当前编号，不保留旧值")
    void settingTenantShouldReplacePreviousTenant() {
        TenantContextHolder.setTenantId(101L);
        assertEquals(Long.valueOf(101L), TenantContextHolder.getTenantId());
        assertEquals(Long.valueOf(101L), TenantContextHolder.getRequiredTenantId());

        TenantContextHolder.setTenantId(202L);
        assertEquals(Long.valueOf(202L), TenantContextHolder.getTenantId());
        assertEquals(Long.valueOf(202L), TenantContextHolder.getRequiredTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @ParameterizedTest(name = "ignore={0}，只有 true 才忽略过滤")
    @NullSource
    @ValueSource(booleans = {true, false})
    void ignoreFlagShouldOnlyAcceptTrueAsBypass(Boolean ignore) {
        TenantContextHolder.setTenantId(101L);
        // 先设为 true，验证 false/null 确实能撤销已开启的忽略状态。
        TenantContextHolder.setIgnore(true);
        TenantContextHolder.setIgnore(ignore);

        assertEquals(Boolean.TRUE.equals(ignore), TenantContextHolder.isIgnore());
        assertEquals(Long.valueOf(101L), TenantContextHolder.getRequiredTenantId());
    }

    @Test
    @DisplayName("clear 同时清除租户和忽略标记，且可重复调用")
    void clearShouldRemoveBothValuesAndBeSafeToRepeat() {
        TenantContextHolder.setTenantId(101L);
        TenantContextHolder.setIgnore(true);

        TenantContextHolder.clear();

        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
        assertThrows(IllegalStateException.class, TenantContextHolder::getRequiredTenantId);

        TenantContextHolder.clear();
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test
    @DisplayName("任务异常后 finally 清理，复用同一线程的下一任务无旧上下文")
    void finallyCleanupShouldPreventLeakIntoNextTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> failedTask = executor.submit(() -> {
                try {
                    TenantContextHolder.setTenantId(101L);
                    TenantContextHolder.setIgnore(true);
                    throw new IllegalStateException("模拟业务失败");
                } finally {
                    TenantContextHolder.clear();
                }
            });

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> failedTask.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);

            Future<?> nextTask = executor.submit(() -> {
                try {
                    assertNull(TenantContextHolder.getTenantId());
                    assertFalse(TenantContextHolder.isIgnore());
                    assertThrows(IllegalStateException.class,
                            TenantContextHolder::getRequiredTenantId);

                    TenantContextHolder.setTenantId(202L);
                    assertEquals(Long.valueOf(202L), TenantContextHolder.getRequiredTenantId());
                    assertFalse(TenantContextHolder.isIgnore());
                } finally {
                    TenantContextHolder.clear();
                }
            });
            nextTask.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
