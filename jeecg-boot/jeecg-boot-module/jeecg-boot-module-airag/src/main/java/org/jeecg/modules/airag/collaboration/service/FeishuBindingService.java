package org.jeecg.modules.airag.collaboration.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.airag.agent.service.AgentAccessContext;
import org.jeecg.modules.airag.collaboration.config.CollaborationProperties;
import org.jeecg.modules.airag.collaboration.contract.CollaborationEnums.*;
import org.jeecg.modules.airag.collaboration.dto.CollaborationDtos.*;
import org.jeecg.modules.airag.collaboration.entity.*;
import org.jeecg.modules.airag.collaboration.mapper.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
public class FeishuBindingService {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final AiFeishuUserBindingMapper bindingMapper;
    private final AiFeishuBindingTokenMapper tokenMapper;
    private final FeishuAccessService access;
    private final CollaborationProperties properties;
    private final SecureRandom random = new SecureRandom();

    public FeishuBindingService(AiFeishuUserBindingMapper bindingMapper, AiFeishuBindingTokenMapper tokenMapper,
                                FeishuAccessService access, CollaborationProperties properties) {
        this.bindingMapper = bindingMapper; this.tokenMapper = tokenMapper;
        this.access = access; this.properties = properties;
    }

    public IPage<BindingView> page(String botId, String username, Boolean enabled, int pageNo, int pageSize,
                                   AgentAccessContext context) {
        access.requireBot(botId, context, false);
        QueryWrapper<AiFeishuUserBinding> query = new QueryWrapper<>();
        query.lambda().eq(AiFeishuUserBinding::getTenantId, context.tenantId())
                .eq(AiFeishuUserBinding::getBotId, botId).eq(AiFeishuUserBinding::getDelFlag, 0)
                .like(StringUtils.hasText(username), AiFeishuUserBinding::getUsername, username)
                .eq(enabled != null, AiFeishuUserBinding::getEnabled, enabled)
                .orderByDesc(AiFeishuUserBinding::getCreateTime);
        IPage<AiFeishuUserBinding> raw = bindingMapper.selectPage(new Page<>(pageNo, pageSize), query);
        Page<BindingView> result = new Page<>(pageNo, pageSize, raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(this::view).toList());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public String createAdmin(BindingCreateRequest request, AgentAccessContext context) {
        access.requireBot(request.getBotId(), context, false);
        LoginUser user = access.requireUser(request.getUsername(), context.tenantId());
        return bind(request.getBotId(), request.getSenderOpenId(), user.getId(), user.getUsername(),
                context.tenantId(), BindingSource.ADMIN, context.username()).getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(String id, AgentAccessContext context) {
        AiFeishuUserBinding binding = bindingMapper.selectByIdForUpdate(id, context.tenantId());
        if (binding == null) throw CollaborationException.notFound("FEISHU_BINDING_NOT_FOUND", "Binding was not found");
        access.requireBot(binding.getBotId(), context, false);
        binding.setEnabled(false); binding.setUpdateBy(context.username()); binding.setUpdateTime(new Date());
        bindingMapper.updateById(binding);
    }

    @Transactional(rollbackFor = Exception.class)
    public BindingTokenResult createToken(String botId, AgentAccessContext context) {
        access.requireBot(botId, context, true);
        LoginUser user = access.requireUser(context.username(), context.tenantId());
        AiFeishuBindingToken current = tokenMapper.selectActiveForUpdate(botId, context.tenantId(), user.getId());
        if (current != null) { current.setStatus(BindingTokenStatus.REVOKED.name()); tokenMapper.updateById(current); }
        String code = base32();
        Date expiresAt = new Date(System.currentTimeMillis() + properties.getBindingTokenMinutes() * 60_000L);
        AiFeishuBindingToken token = new AiFeishuBindingToken();
        token.setTenantId(context.tenantId()); token.setBotId(botId); token.setUserId(user.getId());
        token.setUsername(user.getUsername()); token.setTokenHash(hash(code)); token.setStatus(BindingTokenStatus.ACTIVE.name());
        token.setExpiresAt(expiresAt); token.setCreateTime(new Date()); tokenMapper.insert(token);
        return new BindingTokenResult(code, expiresAt, "绑定【" + code + "】");
    }

    @Transactional(rollbackFor = Exception.class)
    public AiFeishuUserBinding consumeToken(String botId, String senderOpenId, String code) {
        AiFeishuBindingToken token = tokenMapper.selectByHashForUpdate(hash(code));
        if (token == null || !botId.equals(token.getBotId()) || !BindingTokenStatus.ACTIVE.name().equals(token.getStatus())) {
            throw CollaborationException.badRequest("FEISHU_BINDING_TOKEN_INVALID", "Binding token is invalid");
        }
        if (!token.getExpiresAt().after(new Date())) {
            token.setStatus(BindingTokenStatus.EXPIRED.name()); tokenMapper.updateById(token);
            throw CollaborationException.badRequest("FEISHU_BINDING_TOKEN_INVALID", "Binding token is invalid");
        }
        AiFeishuUserBinding binding = bind(botId, senderOpenId, token.getUserId(), token.getUsername(),
                token.getTenantId(), BindingSource.SELF_SERVICE, token.getUsername());
        token.setStatus(BindingTokenStatus.USED.name()); token.setUsedByOpenId(senderOpenId);
        token.setUsedAt(new Date()); tokenMapper.updateById(token);
        return binding;
    }

    public AiFeishuUserBinding requireEnabled(String botId, String senderOpenId) {
        AiFeishuUserBinding binding = bindingMapper.selectOne(new QueryWrapper<AiFeishuUserBinding>().lambda()
                .eq(AiFeishuUserBinding::getBotId, botId).eq(AiFeishuUserBinding::getSenderOpenId, senderOpenId)
                .eq(AiFeishuUserBinding::getEnabled, true).eq(AiFeishuUserBinding::getDelFlag, 0));
        if (binding == null) throw CollaborationException.notFound("FEISHU_BINDING_NOT_FOUND", "Feishu user is not bound");
        access.requireUser(binding.getUsername(), binding.getTenantId());
        return binding;
    }

    private AiFeishuUserBinding bind(String botId, String senderOpenId, String userId, String username,
                                     String tenantId, BindingSource source, String operator) {
        AiFeishuUserBinding bySender = bindingMapper.selectSenderForUpdate(botId, senderOpenId);
        AiFeishuUserBinding byUser = bindingMapper.selectUserForUpdate(botId, tenantId, userId);
        if (bySender != null && !userId.equals(bySender.getUserId()) || byUser != null && !senderOpenId.equals(byUser.getSenderOpenId())) {
            throw CollaborationException.conflict("FEISHU_BINDING_CONFLICT", "Feishu identity is already bound");
        }
        AiFeishuUserBinding binding = bySender != null ? bySender : byUser;
        if (binding == null) {
            binding = new AiFeishuUserBinding(); binding.setTenantId(tenantId); binding.setBotId(botId);
            binding.setSenderOpenId(senderOpenId); binding.setUserId(userId); binding.setUsername(username);
            binding.setBindingSource(source.name()); binding.setEnabled(true); binding.setDelFlag(0);
            binding.setCreateBy(operator); binding.setCreateTime(new Date());
            try { bindingMapper.insert(binding); } catch (DuplicateKeyException e) {
                throw CollaborationException.conflict("FEISHU_BINDING_CONFLICT", "Feishu identity is already bound");
            }
        } else {
            binding.setEnabled(true); binding.setUpdateBy(operator); binding.setUpdateTime(new Date());
            bindingMapper.updateById(binding);
        }
        return binding;
    }

    private BindingView view(AiFeishuUserBinding value) {
        return new BindingView(value.getId(), value.getBotId(), mask(value.getSenderOpenId()), value.getUsername(),
                value.getBindingSource(), Boolean.TRUE.equals(value.getEnabled()), value.getCreateTime(), value.getLastUsedAt());
    }

    private String mask(String value) {
        if (value == null || value.length() <= 10) return "******";
        return value.substring(0, 6) + "****" + value.substring(value.length() - 4);
    }

    private String base32() {
        byte[] bytes = new byte[16]; random.nextBytes(bytes); StringBuilder result = new StringBuilder(26);
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff); bits += 8;
            while (bits >= 5) { result.append(BASE32[(buffer >> (bits - 5)) & 31]); bits -= 5; }
        }
        if (bits > 0) result.append(BASE32[(buffer << (5 - bits)) & 31]);
        return result.toString();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64); for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
