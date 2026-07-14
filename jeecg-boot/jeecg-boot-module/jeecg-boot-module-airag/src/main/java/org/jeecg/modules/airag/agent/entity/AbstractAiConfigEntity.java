package org.jeecg.modules.airag.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public abstract class AbstractAiConfigEntity implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private String sysOrgCode;
    private String tenantId;
    @TableLogic
    private Integer delFlag;
}
