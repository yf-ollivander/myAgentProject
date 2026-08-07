package org.jeecg.modules.airag.agent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.query.QueryRuleEnum;
import org.jeecg.common.system.vo.SysPermissionDataRuleModel;
import org.jeecg.common.system.vo.SysUserCacheInfo;
import org.jeecg.modules.airag.agent.entity.AiAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizedQueryGeneratorTest {
    @Test
    void explicitRulesResolveUserAndTenantWithoutHttpRequest() {
        SysUserCacheInfo user = new SysUserCacheInfo();
        user.setSysUserCode("user-a");
        user.setSysOrgCode("A01");
        SysPermissionDataRuleModel orgRule = rule("sysOrgCode", "#{sys_org_code}");
        SysPermissionDataRuleModel tenantRule = rule("tenantId", "#{tenant_id}");
        QueryWrapper<AiAgent> query = new QueryWrapper<>();

        QueryGenerator.installAuthMplus(query, AiAgent.class, List.of(orgRule, tenantRule), user, "42");

        assertTrue(query.getSqlSegment().contains("sys_org_code"));
        assertTrue(query.getSqlSegment().contains("tenant_id"));
        assertTrue(query.getParamNameValuePairs().containsValue("A01"));
        assertTrue(query.getParamNameValuePairs().containsValue("42"));
    }

    private static SysPermissionDataRuleModel rule(String column, String value) {
        SysPermissionDataRuleModel rule = new SysPermissionDataRuleModel();
        rule.setRuleColumn(column);
        rule.setRuleConditions(QueryRuleEnum.EQ.getValue());
        rule.setRuleValue(value);
        return rule;
    }
}
