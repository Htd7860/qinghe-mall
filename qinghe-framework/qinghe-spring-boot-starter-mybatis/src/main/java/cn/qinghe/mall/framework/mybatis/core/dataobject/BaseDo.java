package cn.qinghe.mall.framework.mybatis.core.dataobject;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author：qw
 * @Package：cn.qinghe.mall.framework.mybatis.core.dataobject
 * @Project：qinghe-mall
 * @name：BaseDo
 * @Date：2026/10/21 13:50
 * @Filename：BaseDo
 */
@Data
public abstract class BaseDo implements Serializable {

    @TableField(fill= FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill=FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill=FieldFill.INSERT)
    private String creator;

    @TableField(fill=FieldFill.INSERT_UPDATE)
    private String updater;

    @TableLogic
    private Boolean deleted;

    public void clean(){
        this.createTime=null;
        this.updater=null;
        this.creator=null;
        this.updateTime=null;
    }
}
